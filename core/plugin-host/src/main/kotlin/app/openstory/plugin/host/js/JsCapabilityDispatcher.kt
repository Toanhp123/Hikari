package app.openstory.plugin.host.js

import app.openstory.common.AppResult
import app.openstory.network.PluginHttpGateway
import app.openstory.network.PluginHttpRequest
import app.openstory.network.RequestBudget
import app.openstory.plugin.api.PluginCapability
import app.openstory.plugin.api.PluginManifest
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.put

class JsCapabilityDispatcher(
    private val manifest: PluginManifest,
    private val http: PluginHttpGateway,
) {
    suspend fun dispatch(
        request: JsBridgeRequest,
        budget: JsOperationBudget,
    ): JsBridgeResponse = when {
        !SAFE_ID.matches(request.id) || request.method != HTTP_EXECUTE ->
            JsBridgeResponse.failure(safeId(request.id), CAPABILITY_DENIED)
        PluginCapability.NETWORK !in manifest.capabilities ->
            JsBridgeResponse.failure(request.id, CAPABILITY_DENIED)
        !budget.consumeHostCall() ->
            JsBridgeResponse.failure(request.id, HOST_CALL_LIMIT)
        else -> executeHttp(request, budget)
    }

    private suspend fun executeHttp(
        request: JsBridgeRequest,
        budget: JsOperationBudget,
    ): JsBridgeResponse = request.params.toHttpRequestOrNull()?.let { httpRequest ->
        executeHttpRequest(request.id, httpRequest, budget)
    } ?: JsBridgeResponse.failure(request.id, INVALID_MESSAGE)

    private suspend fun executeHttpRequest(
        requestId: String,
        request: PluginHttpRequest,
        budget: JsOperationBudget,
    ): JsBridgeResponse = when (
        val result = http.execute(
            request,
            RequestBudget(
                maxRequests = budget.limits.maxHostCalls,
                maxDurationMillis = budget.limits.maxDurationMillis,
                maxDecompressedBytes = budget.limits.maxResponseBytes,
            ),
        )
    ) {
        is AppResult.Success -> if (result.value.body.size > budget.limits.maxResponseBytes) {
            JsBridgeResponse.failure(requestId, RESPONSE_TOO_LARGE)
        } else {
            JsBridgeResponse.success(requestId, result.value.toBridgeJson())
        }
        is AppResult.Failure -> JsBridgeResponse.failure(requestId, result.error.code)
    }

    private fun JsonObject.toHttpRequestOrNull(): PluginHttpRequest? {
        val url = (this["url"] as? JsonPrimitive)?.contentOrNull
        val headers = requestHeaders()
        return if (url == null || headers == null) null else PluginHttpRequest(url, headers)
    }

    private fun JsonObject.requestHeaders(): Map<String, String>? = when (val rawHeaders = this["headers"]) {
        null -> emptyMap()
        !is JsonObject -> null
        else -> rawHeaders.validatedHeaders()
    }

    private fun JsonObject.validatedHeaders(): Map<String, String>? = if (size > MAX_REQUEST_HEADERS) {
        null
    } else {
        entries.mapNotNull { (name, element) -> validHeader(name, element) }
            .takeIf { it.size == size }
            ?.toMap(linkedMapOf())
    }

    private fun validHeader(name: String, element: JsonElement): Pair<String, String>? {
        val value = element as? JsonPrimitive
        return value
            ?.takeIf { it.isString && validHeaderName(name) && validHeaderValue(it.content) }
            ?.let { name to it.content }
    }

    private fun validHeaderName(name: String): Boolean =
        name.length in 1..MAX_HEADER_NAME_LENGTH &&
            name.all { character ->
                character in 'A'..'Z' ||
                    character in 'a'..'z' ||
                    character in '0'..'9' ||
                    character in HEADER_NAME_SYMBOLS
            }

    private fun validHeaderValue(value: String): Boolean =
        value.length <= MAX_HEADER_VALUE_LENGTH &&
            value.all { character ->
                character == '\t' || (character.code >= MIN_HEADER_VALUE_CODE && character != '\u007f')
            }

    private fun app.openstory.network.PluginHttpResponse.toBridgeJson(): JsonObject =
        buildJsonObject {
            put("status", status)
            put("bodyText", decodedText ?: body.decodeToString())
        }

    private fun safeId(id: String): String = if (SAFE_ID.matches(id)) id else INVALID_ID

    private companion object {
        const val HTTP_EXECUTE = "http.execute"
        const val CAPABILITY_DENIED = "plugin.capability_denied"
        const val HOST_CALL_LIMIT = "plugin.host_call_limit"
        const val INVALID_MESSAGE = "plugin.bridge_message_invalid"
        const val RESPONSE_TOO_LARGE = "plugin.response_too_large"
        const val INVALID_ID = "invalid"
        const val MAX_REQUEST_HEADERS = 16
        const val MAX_HEADER_NAME_LENGTH = 64
        const val MAX_HEADER_VALUE_LENGTH = 2_048
        const val MIN_HEADER_VALUE_CODE = 0x20
        const val HEADER_NAME_SYMBOLS = "!#$%&'*+-.^_`|~"
        val SAFE_ID = Regex("[A-Za-z0-9._-]{1,64}")
    }
}
