package app.openstory.plugins.runtime.execution

import app.openstory.plugins.runtime.PluginCallResult
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement

@Serializable
data class BridgeRequest(
    val id: String,
    val method: String,
    val payload: JsonElement,
) {
    init {
        require(SAFE_CALL_ID.matches(id)) { "Bridge call id is invalid" }
        require(SAFE_METHOD.matches(method)) { "Bridge method is invalid" }
    }
}

@Serializable
data class BridgeResponse(
    val id: String,
    val result: JsonElement? = null,
    val error: BridgeError? = null,
) {
    init {
        require((result == null) != (error == null)) { "Bridge response must contain result or error" }
    }
}

@Serializable
data class BridgeError(
    val code: String,
    val retryable: Boolean = false,
)

internal fun PluginCallResult<JsonElement>.toBridgeResponse(id: String): BridgeResponse = when (this) {
    is PluginCallResult.Success -> BridgeResponse(id = id, result = value)
    is PluginCallResult.Failure -> BridgeResponse(
        id = id,
        error = BridgeError(code = code, retryable = retryable),
    )
}

private val SAFE_CALL_ID = Regex("call-[1-9][0-9]{0,8}")
private val SAFE_METHOD = Regex("[a-z]+(?:[.][a-z]+)+")
