package app.openstory.plugin.host.js

import android.content.Context
import androidx.javascriptengine.IsolateStartupParameters
import androidx.javascriptengine.JavaScriptIsolate
import androidx.javascriptengine.JavaScriptSandbox
import androidx.javascriptengine.Message
import androidx.javascriptengine.MessagePort
import androidx.javascriptengine.MessagePortClient
import com.google.common.util.concurrent.ListenableFuture
import java.util.concurrent.ExecutionException
import java.util.concurrent.Executor
import java.util.concurrent.CancellationException as FutureCancellationException
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
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject

class AndroidxJsIsolateExecutor(
    context: Context,
    private val callbackDispatcher: CoroutineDispatcher = Dispatchers.Default,
) : JsIsolateExecutor, AutoCloseable {
    private val context = context.applicationContext
    private val sandboxMutex = Mutex()

    @Volatile
    private var sandbox: JavaScriptSandbox? = null

    override suspend fun execute(
        source: String,
        operation: String,
        inputJson: String,
        limits: JsRuntimeLimits,
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
            val envelope = isolate.evaluateJavaScriptAsync(
                buildInvocationScript(source, operation, inputJson),
            ).await()
            unwrapEnvelope(envelope)
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
            throw JsExecutionFailure(SANDBOX_UNSUPPORTED)
        }
        return try {
            JavaScriptSandbox.createConnectedInstanceAsync(context).await()
        } catch (_: RuntimeException) {
            throw JsExecutionFailure(SANDBOX_UNAVAILABLE)
        }
    }

}

private fun requireFeatures(value: JavaScriptSandbox) {
    val supported = value.isFeatureSupported(JavaScriptSandbox.JS_FEATURE_PROMISE_RETURN) &&
        value.isFeatureSupported(JavaScriptSandbox.JS_FEATURE_MESSAGE_PORTS)
    if (!supported) throw JsExecutionFailure(SANDBOX_UNSUPPORTED)
}

private fun JavaScriptSandbox.createBoundedIsolate(
    limits: JsRuntimeLimits,
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
    lateinit var activePort: MessagePort
    val executor = Executor { command -> scope.launch { command.run() } }
    activePort = createMessageChannel(
        BRIDGE_PORT,
        executor,
        MessagePortClient { message ->
            if (message.type == Message.TYPE_STRING) {
                scope.launch {
                    val response = try {
                        bridge(message.string)
                    } catch (_: RuntimeException) {
                        JsBridgeCodec().encodeResponse(
                            JsBridgeResponse.failure(INVALID_CALL_ID, BRIDGE_FAILED),
                        )
                    }
                    activePort.postMessage(Message.createStringMessage(response))
                }
            }
        },
    )
    return activePort
}

private fun unwrapEnvelope(source: String): String {
    val envelope = parseEnvelope(source)
    val ok = (envelope["ok"] as? JsonPrimitive)?.contentOrNull
    return if (ok == "true") {
        requireResult(envelope)
    } else {
        throw JsExecutionFailure(envelope.safeErrorCode())
    }
}

private fun parseEnvelope(source: String): JsonObject =
    runCatching { Json.parseToJsonElement(source).jsonObject }
        .getOrElse { throw JsExecutionFailure(INVALID_OUTPUT) }

private fun requireResult(envelope: JsonObject): String =
    envelope["result"]?.toString() ?: throw JsExecutionFailure(INVALID_OUTPUT)

private fun JsonObject.safeErrorCode(): String {
    val error = get("error") as? JsonObject
    val code = (error?.get("code") as? JsonPrimitive)?.contentOrNull
    return code?.takeIf(SAFE_CODE::matches) ?: EXECUTION_FAILED
}

private const val BRIDGE_PORT = "openstoryHost"
private const val INVALID_CALL_ID = "invalid"
private const val BRIDGE_FAILED = "plugin.bridge_dispatch_failed"
private const val SANDBOX_UNSUPPORTED = "plugin.javascript_sandbox_unsupported"
private const val SANDBOX_UNAVAILABLE = "plugin.javascript_sandbox_unavailable"
private const val INVALID_OUTPUT = "plugin.javascript_output_invalid"
private const val EXECUTION_FAILED = "plugin.javascript_execution_failed"
private val SAFE_CODE = Regex("[A-Za-z0-9._-]+")

private fun buildInvocationScript(
    source: String,
    operation: String,
    inputJson: String,
): String {
    val operationJson = JsonPrimitive(operation).toString()
    return """
        (async () => {
          const port = await android.getNamedPort("openstoryHost");
          const pending = new Map();
          let nextCallId = 0;
          port.onmessage = event => {
            const response = JSON.parse(event.data);
            const callback = pending.get(response.id);
            if (!callback) return;
            pending.delete(response.id);
            if (response.error) {
              const error = new Error(response.error.code);
              error.code = response.error.code;
              callback.reject(error);
            } else {
              callback.resolve(response.result);
            }
          };
          const callHost = (method, params) => new Promise((resolve, reject) => {
            const id = `call-${'$'}{++nextCallId}`;
            pending.set(id, {resolve, reject});
            port.postMessage(JSON.stringify({id, method, params}));
          });
          Object.defineProperty(globalThis, "host", {
            value: Object.freeze({http: params => callHost("http.execute", params)}),
            writable: false,
            configurable: false
          });
          try {
            $source
            const handler = globalThis.openstoryPlugin?.[$operationJson];
            if (typeof handler !== "function") {
              const error = new Error("operation unavailable");
              error.code = "plugin.javascript_operation_unavailable";
              throw error;
            }
            const result = await handler($inputJson);
            return JSON.stringify({ok: true, result});
          } catch (error) {
            const candidate = typeof error?.code === "string" ? error.code : "";
            const code = /^[A-Za-z0-9._-]+${'$'}/.test(candidate)
              ? candidate
              : "plugin.javascript_execution_failed";
            return JSON.stringify({ok: false, error: {code}});
          }
        })()
    """.trimIndent()
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
