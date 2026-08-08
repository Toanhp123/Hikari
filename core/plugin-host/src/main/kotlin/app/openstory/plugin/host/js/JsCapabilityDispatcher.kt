package app.openstory.plugin.host.js

import app.openstory.common.AppResult
import app.openstory.network.PluginHttpGateway
import app.openstory.network.PluginHttpRequest
import app.openstory.network.RequestBudget
import app.openstory.plugin.api.PluginCapability
import app.openstory.plugin.api.PluginManifest
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
    ): JsBridgeResponse {
        val url = (request.params["url"] as? JsonPrimitive)?.contentOrNull
            ?: return JsBridgeResponse.failure(request.id, INVALID_MESSAGE)
        return when (
            val result = http.execute(
                PluginHttpRequest(url),
                RequestBudget(
                    maxRequests = budget.limits.maxHostCalls,
                    maxDurationMillis = budget.limits.maxDurationMillis,
                    maxDecompressedBytes = budget.limits.maxResponseBytes,
                ),
            )
        ) {
            is AppResult.Success -> if (result.value.body.size > budget.limits.maxResponseBytes) {
                JsBridgeResponse.failure(request.id, RESPONSE_TOO_LARGE)
            } else {
                JsBridgeResponse.success(request.id, result.value.toBridgeJson())
            }
            is AppResult.Failure -> JsBridgeResponse.failure(request.id, result.error.code)
        }
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
        val SAFE_ID = Regex("[A-Za-z0-9._-]{1,64}")
    }
}
