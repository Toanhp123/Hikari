package app.openstory.plugins.runtime.execution

import app.openstory.plugins.api.protocol.PluginOperation
import java.util.LinkedHashMap
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonPrimitive

internal class InvocationScriptBuilder(
    private val maxCachedSources: Int = DEFAULT_SOURCE_CACHE_SIZE,
    private val sourceEncoder: (String) -> String = { source -> JsonPrimitive(source).toString() },
) {
    init {
        require(maxCachedSources > 0) { "Source cache size must be positive." }
    }

    private val sourceLiterals = object : LinkedHashMap<String, String>(maxCachedSources, LOAD_FACTOR, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, String>?): Boolean =
            size > maxCachedSources
    }
    private val operationSegments = PluginOperation.entries.associateWith { operation ->
        operation.wireName.split('.').joinToString(separator = ",") { segment ->
            JsonPrimitive(segment).toString()
        }
    }

    fun build(
        source: String,
        operation: PluginOperation,
        input: JsonElement,
    ): String {
        val cachedSource = synchronized(sourceLiterals) { sourceLiterals[source] }
        val sourceLiteral = cachedSource ?: sourceEncoder(source).let { encoded ->
            synchronized(sourceLiterals) {
                sourceLiterals[source] ?: encoded.also { sourceLiterals[source] = it }
            }
        }
        return invocationScript(
            sourceLiteral = sourceLiteral,
            input = input,
            segments = checkNotNull(operationSegments[operation]),
        )
    }

    private companion object {
        const val DEFAULT_SOURCE_CACHE_SIZE = 8
        const val LOAD_FACTOR = 0.75f
    }
}

private fun invocationScript(sourceLiteral: String, input: JsonElement, segments: String): String = """
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

internal const val BRIDGE_PORT = "openstoryHost"
