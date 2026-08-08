package app.openstory.plugin.host.js

import app.openstory.common.AppError
import app.openstory.common.AppResult
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject

data class JsBridgeRequest(
    val id: String,
    val method: String,
    val params: JsonObject,
)

data class JsBridgeError(
    val code: String,
)

data class JsBridgeResponse(
    val id: String,
    val result: JsonElement? = null,
    val error: JsBridgeError? = null,
) {
    companion object {
        fun success(id: String, result: JsonElement): JsBridgeResponse =
            JsBridgeResponse(id = id, result = result)

        fun failure(id: String, code: String): JsBridgeResponse =
            JsBridgeResponse(id = id, error = JsBridgeError(code))
    }
}

class JsBridgeCodec(
    private val limits: JsRuntimeLimits = JsRuntimeLimits(),
) {
    fun decodeRequest(source: String): AppResult<JsBridgeRequest> {
        if (source.encodeToByteArray().size > limits.maxBridgeMessageBytes) {
            return bridgeFailure(MESSAGE_TOO_LARGE)
        }
        return runCatching { parseRequest(source) }.fold(
            onSuccess = { AppResult.Success(it) },
            onFailure = { bridgeFailure(INVALID_MESSAGE) },
        )
    }

    private fun parseRequest(source: String): JsBridgeRequest {
        val value = JSON.parseToJsonElement(source).jsonObject
        require(value.keys == REQUEST_FIELDS)
        val id = requireNotNull(value.string("id"))
        val method = requireNotNull(value.string("method"))
        val params = requireNotNull(value["params"] as? JsonObject)
        require(SAFE_ID.matches(id) && SAFE_METHOD.matches(method))
        return JsBridgeRequest(id, method, params)
    }

    private fun JsonObject.string(name: String): String? =
        (get(name) as? JsonPrimitive)?.contentOrNull

    private companion object {
        val JSON = kotlinx.serialization.json.Json
        val REQUEST_FIELDS = setOf("id", "method", "params")
        val SAFE_ID = Regex("[A-Za-z0-9._-]{1,64}")
        val SAFE_METHOD = Regex("[a-z][a-z0-9_.-]{0,63}")
        const val INVALID_MESSAGE = "plugin.bridge_message_invalid"
        const val MESSAGE_TOO_LARGE = "plugin.bridge_message_too_large"
    }
}

private fun bridgeFailure(code: String): AppResult.Failure = AppResult.Failure(
    AppError.Plugin(code = code, retryable = false),
)
