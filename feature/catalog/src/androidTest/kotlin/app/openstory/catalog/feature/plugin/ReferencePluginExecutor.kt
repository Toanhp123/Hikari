package app.openstory.catalog.feature.plugin

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
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

internal data class ReferencePluginLimits(
    val timeoutMillis: Long = 15_000L,
    val maxHeapBytes: Long = 16L * 1_024L * 1_024L,
    val maxBridgeMessageBytes: Int = 256 * 1_024,
    val maxOutputJsonBytes: Int = 2 * 1_024 * 1_024,
) {
    init {
        require(timeoutMillis > 0 && maxHeapBytes > 0)
        require(maxBridgeMessageBytes > 0 && maxOutputJsonBytes > 0)
    }
}

internal enum class ReferencePluginFailureCode {
    SANDBOX_UNSUPPORTED,
    SANDBOX_UNAVAILABLE,
    TIMEOUT,
    BRIDGE_MESSAGE_TOO_LARGE,
    OUTPUT_TOO_LARGE,
    INVALID_OUTPUT,
    OPERATION_UNAVAILABLE,
    EXECUTION_FAILED,
    CAPABILITY_DENIED,
    PROTOCOL_INVALID,
}

internal class ReferencePluginExecutionException(
    val failureCode: ReferencePluginFailureCode,
    val retryable: Boolean = false,
    cause: Throwable? = null,
) : RuntimeException(failureCode.name, cause)

internal fun interface ReferenceJavaScriptEvaluator {
    suspend fun execute(
        source: String,
        operation: PluginOperation,
        input: JsonElement,
        limits: ReferencePluginLimits,
        bridge: suspend (String) -> String,
    ): String
}

internal class ReferencePluginExecutor(
    private val evaluator: ReferenceJavaScriptEvaluator,
    private val limits: ReferencePluginLimits = ReferencePluginLimits(),
) : AutoCloseable {
    constructor(
        context: Context,
        limits: ReferencePluginLimits = ReferencePluginLimits(),
    ) : this(AndroidxReferenceJavaScriptEvaluator(context), limits)

    suspend fun execute(
        source: String,
        operation: PluginOperation,
        input: JsonElement,
        bridge: suspend (String) -> String,
    ): JsonElement {
        val output = withTimeoutOrNull(limits.timeoutMillis) {
            evaluator.execute(source, operation, input, limits) { message ->
                requireByteLimit(message, limits.maxBridgeMessageBytes, ReferencePluginFailureCode.BRIDGE_MESSAGE_TOO_LARGE)
                bridge(message).also { response ->
                    requireByteLimit(
                        response,
                        limits.maxBridgeMessageBytes,
                        ReferencePluginFailureCode.BRIDGE_MESSAGE_TOO_LARGE,
                    )
                }
            }
        } ?: throw ReferencePluginExecutionException(ReferencePluginFailureCode.TIMEOUT)
        requireByteLimit(output, limits.maxOutputJsonBytes, ReferencePluginFailureCode.OUTPUT_TOO_LARGE)
        return runCatching { Json.parseToJsonElement(output) }
            .getOrElse { throw ReferencePluginExecutionException(ReferencePluginFailureCode.INVALID_OUTPUT, cause = it) }
    }

    override fun close() {
        (evaluator as? AutoCloseable)?.close()
    }
}

private fun requireByteLimit(
    value: String,
    maximumBytes: Int,
    failureCode: ReferencePluginFailureCode,
) {
    if (value.encodeToByteArray().size > maximumBytes) {
        throw ReferencePluginExecutionException(failureCode)
    }
}

private class AndroidxReferenceJavaScriptEvaluator(
    context: Context,
    private val callbackDispatcher: CoroutineDispatcher = Dispatchers.Default,
) : ReferenceJavaScriptEvaluator, AutoCloseable {
    private val context = context.applicationContext
    private val sandboxMutex = Mutex()
    private val invocationScripts = ReferenceInvocationScriptBuilder()

    @Volatile
    private var sandbox: JavaScriptSandbox? = null

    override suspend fun execute(
        source: String,
        operation: PluginOperation,
        input: JsonElement,
        limits: ReferencePluginLimits,
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
        if (!JavaScriptSandbox.isSupported()) {
            throw ReferencePluginExecutionException(ReferencePluginFailureCode.SANDBOX_UNSUPPORTED)
        }
        return runCatching { JavaScriptSandbox.createConnectedInstanceAsync(context).await() }
            .getOrElse {
                throw ReferencePluginExecutionException(
                    ReferencePluginFailureCode.SANDBOX_UNAVAILABLE,
                    cause = it,
                )
            }
    }
}

private fun requireFeatures(sandbox: JavaScriptSandbox) {
    val supported = sandbox.isFeatureSupported(JavaScriptSandbox.JS_FEATURE_PROMISE_RETURN) &&
        sandbox.isFeatureSupported(JavaScriptSandbox.JS_FEATURE_MESSAGE_PORTS)
    if (!supported) {
        throw ReferencePluginExecutionException(ReferencePluginFailureCode.SANDBOX_UNSUPPORTED)
    }
}

private fun JavaScriptSandbox.createBoundedIsolate(
    limits: ReferencePluginLimits,
): JavaScriptIsolate {
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
                    val response = try {
                        bridge(message.string)
                    } catch (failure: ReferencePluginExecutionException) {
                        bridgeFailureResponse(message.string, failure)
                    }
                    port.postMessage(Message.createStringMessage(response))
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

private class ReferenceInvocationScriptBuilder {
    fun build(
        source: String,
        operation: PluginOperation,
        input: JsonElement,
    ): String {
        val sourceLiteral = JsonPrimitive(source).toString()
        val segments = operation.wireName.split('.').joinToString(separator = ",") { segment ->
            JsonPrimitive(segment).toString()
        }
        return invocationScript(sourceLiteral, input, segments)
    }
}

private fun invocationScript(
    sourceLiteral: String,
    input: JsonElement,
    segments: String,
): String = """
    (async () => {
      const port = await android.getNamedPort("$BRIDGE_PORT");
      const pending = new Map(), bridgeFailureMarker = Symbol("openstory.bridgeFailure");
      let nextCallId = 0;
      port.onmessage = event => {
        const response = JSON.parse(event.data);
        const callback = pending.get(response.id);
        if (!callback) return;
        pending.delete(response.id);
        if (response.error) {
          const failure = Object.assign(new Error(), response.error);
          Object.defineProperty(failure, bridgeFailureMarker, {value: true});
          callback.reject(failure);
        } else {
          callback.resolve(response.result);
        }
      };
      const bridgeCall = (method, payload) => new Promise((resolve, reject) => {
        const id = `call-${'$'}{++nextCallId}`;
        pending.set(id, {resolve, reject});
        port.postMessage(JSON.stringify({id, method, payload}));
      });
      Object.defineProperty(globalThis, "host", {
        value: Object.freeze({
          http: request => bridgeCall("http.execute", request),
          html: Object.freeze({query: request => bridgeCall("html.query", request)}),
          log: event => bridgeCall("log.safe", event),
        }),
        writable: false,
        configurable: false,
      });
      try {
        globalThis.eval($sourceLiteral);
        const path = [$segments];
        let handler = globalThis.openstoryPlugin;
        for (const segment of path) handler = handler?.[segment];
        if (typeof handler !== "function") {
          throw Object.assign(new Error(), {code: "plugin.operation_unavailable"});
        }
        return JSON.stringify({payload: JSON.stringify(await handler($input))});
      } catch (failure) {
        const code = failure && typeof failure.code === "string"
          ? failure.code : "plugin.execution_failed";
        const retryable = failure && failure[bridgeFailureMarker] === true
          && failure.retryable === true;
        return JSON.stringify({errorCode: code, retryable});
      }
    })()
""".trimIndent()

private fun decodeInvocationResult(source: String): String {
    val envelope = runCatching { Json.parseToJsonElement(source).jsonObject }
        .getOrElse { throw ReferencePluginExecutionException(ReferencePluginFailureCode.INVALID_OUTPUT, cause = it) }
    envelope["errorCode"]?.jsonPrimitive?.content?.let { code ->
        val mapped = when (code) {
            "plugin.operation_unavailable" -> ReferencePluginFailureCode.OPERATION_UNAVAILABLE
            "plugin.bridge_message_too_large" -> ReferencePluginFailureCode.BRIDGE_MESSAGE_TOO_LARGE
            "plugin.capability_denied" -> ReferencePluginFailureCode.CAPABILITY_DENIED
            else -> ReferencePluginFailureCode.EXECUTION_FAILED
        }
        throw ReferencePluginExecutionException(
            mapped,
            retryable = envelope["retryable"]?.jsonPrimitive?.booleanOrNull ?: false,
        )
    }
    return envelope["payload"]?.jsonPrimitive?.content
        ?: throw ReferencePluginExecutionException(ReferencePluginFailureCode.INVALID_OUTPUT)
}

private fun bridgeFailureResponse(
    request: String,
    failure: ReferencePluginExecutionException,
): String {
    val id = runCatching {
        Json.parseToJsonElement(request).jsonObject["id"]?.jsonPrimitive?.content
    }.getOrNull().orEmpty()
    val code = when (failure.failureCode) {
        ReferencePluginFailureCode.BRIDGE_MESSAGE_TOO_LARGE -> "plugin.bridge_message_too_large"
        else -> "plugin.capability_denied"
    }
    return """{"id":${JsonPrimitive(id)},"error":{"code":${JsonPrimitive(code)},"retryable":false}}"""
}

private const val BRIDGE_PORT = "openstoryHost"
