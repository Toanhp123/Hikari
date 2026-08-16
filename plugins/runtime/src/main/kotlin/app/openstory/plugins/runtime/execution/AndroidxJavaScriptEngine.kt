package app.openstory.plugins.runtime.execution

import android.content.Context
import androidx.javascriptengine.IsolateStartupParameters
import androidx.javascriptengine.JavaScriptIsolate
import androidx.javascriptengine.JavaScriptSandbox
import androidx.javascriptengine.Message
import androidx.javascriptengine.MessagePort
import androidx.javascriptengine.MessagePortClient
import app.openstory.plugins.api.protocol.PluginOperation
import com.google.common.util.concurrent.ListenableFuture
import java.util.concurrent.CancellationException as FutureCancellationException
import java.util.concurrent.ExecutionException
import java.util.concurrent.Executor
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

class AndroidxJavaScriptEngine(
    context: Context,
    private val callbackDispatcher: CoroutineDispatcher = Dispatchers.Default,
) : JavaScriptEngine, AutoCloseable {
    private val context = context.applicationContext
    private val sandboxMutex = Mutex()
    private val invocationScripts = InvocationScriptBuilder()

    @Volatile
    private var sandbox: JavaScriptSandbox? = null

    override suspend fun execute(
        source: String,
        operation: PluginOperation,
        input: JsonElement,
        limits: RuntimeLimits,
        bridge: suspend (String) -> String,
    ): String {
        val activeSandbox = sandbox()
        requireFeatures(activeSandbox)
        val isolateCanTerminate = activeSandbox.isFeatureSupported(
            JavaScriptSandbox.JS_FEATURE_ISOLATE_TERMINATION,
        )
        val isolate = activeSandbox.createBoundedIsolate(limits)
        val callbackScope = CoroutineScope(SupervisorJob() + callbackDispatcher)
        var port: MessagePort? = null
        return try {
            port = isolate.createBridgePort(callbackScope, bridge)
            val evaluation = isolate.evaluateJavaScriptAsync(
                invocationScripts.build(source, operation, input),
            ).await()
            decodeInvocationResult(evaluation)
        } finally {
            port?.close()
            callbackScope.cancel()
            isolate.close()
            if (!currentCoroutineContext().isActive && !isolateCanTerminate) {
                withContext(NonCancellable) { discardSandbox(activeSandbox) }
            }
        }
    }

    override fun close() {
        val active = sandbox
        sandbox = null
        active?.close()
    }

    private suspend fun sandbox(): JavaScriptSandbox = sandboxMutex.withLock {
        sandbox ?: openSandbox().also { sandbox = it }
    }

    private suspend fun discardSandbox(expected: JavaScriptSandbox) = sandboxMutex.withLock {
        if (sandbox === expected) {
            sandbox = null
            expected.close()
        }
    }

    private suspend fun openSandbox(): JavaScriptSandbox {
        if (!JavaScriptSandbox.isSupported()) throw JavaScriptExecutionFailure(SANDBOX_UNSUPPORTED)
        return runCatching { JavaScriptSandbox.createConnectedInstanceAsync(context).await() }
            .getOrElse { throw JavaScriptExecutionFailure(SANDBOX_UNAVAILABLE) }
    }
}

private fun requireFeatures(sandbox: JavaScriptSandbox) {
    val supported = sandbox.isFeatureSupported(JavaScriptSandbox.JS_FEATURE_PROMISE_RETURN) &&
        sandbox.isFeatureSupported(JavaScriptSandbox.JS_FEATURE_MESSAGE_PORTS)
    if (!supported) throw JavaScriptExecutionFailure(SANDBOX_UNSUPPORTED)
}

private fun JavaScriptSandbox.createBoundedIsolate(limits: RuntimeLimits): JavaScriptIsolate {
    val parameters = IsolateStartupParameters()
    if (isFeatureSupported(JavaScriptSandbox.JS_FEATURE_ISOLATE_MAX_HEAP_SIZE)) {
        parameters.maxHeapSizeBytes = limits.maxHeapBytes
    }
    if (isFeatureSupported(JavaScriptSandbox.JS_FEATURE_EVALUATE_WITHOUT_TRANSACTION_LIMIT)) {
        parameters.maxEvaluationReturnSizeBytes = limits.maxOutputJsonBytes
    }
    return createIsolate(parameters)
}

private fun JavaScriptIsolate.createBridgePort(
    scope: CoroutineScope,
    bridge: suspend (String) -> String,
): MessagePort {
    lateinit var port: MessagePort
    val executor = Executor { command -> scope.launch { command.run() } }
    port = createMessageChannel(
        BRIDGE_PORT,
        executor,
        MessagePortClient { message ->
            if (message.type == Message.TYPE_STRING) {
                scope.launch {
                    port.postMessage(Message.createStringMessage(bridge(message.string)))
                }
            }
        },
    )
    return port
}

private suspend fun <T> ListenableFuture<T>.await(): T = suspendCancellableCoroutine { continuation ->
    addListener(
        {
            try {
                continuation.resume(get())
            } catch (failure: ExecutionException) {
                continuation.resumeWithException(failure.cause ?: failure)
            } catch (failure: FutureCancellationException) {
                continuation.cancel(failure)
            } catch (failure: InterruptedException) {
                Thread.currentThread().interrupt()
                continuation.resumeWithException(failure)
            }
        },
        Executor(Runnable::run),
    )
    continuation.invokeOnCancellation { cancel(true) }
}

private const val SANDBOX_UNSUPPORTED = "plugin.javascript_sandbox_unsupported"
private const val SANDBOX_UNAVAILABLE = "plugin.javascript_sandbox_unavailable"

internal fun decodeInvocationResult(source: String): String {
    val envelope = runCatching { kotlinx.serialization.json.Json.parseToJsonElement(source).jsonObject }
        .getOrElse { executionFailure() }
    envelope["errorCode"]?.jsonPrimitive?.content?.let { code ->
        executionFailure(
            code = code,
            retryable = envelope["retryable"]?.jsonPrimitive?.booleanOrNull ?: false,
        )
    }
    return envelope["payload"]?.jsonPrimitive?.content
        ?: executionFailure()
}

private fun executionFailure(
    code: String = "plugin.execution_failed",
    retryable: Boolean = false,
): Nothing = throw JavaScriptExecutionFailure(code, retryable)
