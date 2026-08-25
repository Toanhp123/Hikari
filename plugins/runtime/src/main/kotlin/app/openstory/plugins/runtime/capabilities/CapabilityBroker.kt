package app.openstory.plugins.runtime.capabilities

import app.openstory.common.id.PluginId
import app.openstory.plugins.runtime.PluginCallResult
import app.openstory.plugins.runtime.capabilities.html.HtmlCapability
import app.openstory.plugins.runtime.capabilities.html.HtmlQueryRequest
import app.openstory.plugins.runtime.capabilities.html.HtmlQueryResponse
import app.openstory.plugins.runtime.capabilities.http.PluginHttpCapability
import app.openstory.plugins.runtime.capabilities.http.PluginHttpRequest
import app.openstory.plugins.runtime.capabilities.http.PluginHttpResponse
import app.openstory.plugins.runtime.capabilities.http.PluginRequestPolicy
import app.openstory.plugins.runtime.capabilities.log.SafeLogEvent
import app.openstory.plugins.runtime.capabilities.log.SafePluginLogger
import app.openstory.plugins.runtime.auth.PluginSessionService
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

fun interface CapabilityDispatcher {
    suspend fun dispatch(
        pluginId: PluginId,
        operation: String?,
        method: String,
        payload: JsonElement,
        requestPolicy: PluginRequestPolicy,
    ): PluginCallResult<JsonElement>
}

class CapabilityBroker(
    private val http: PluginHttpCapability,
    private val html: HtmlCapability,
    private val logger: SafePluginLogger,
    private val sessions: PluginSessionService? = null,
    private val json: Json = Json,
) : CapabilityDispatcher {
    override suspend fun dispatch(
        pluginId: PluginId,
        operation: String?,
        method: String,
        payload: JsonElement,
        requestPolicy: PluginRequestPolicy,
    ): PluginCallResult<JsonElement> = try {
        when (method) {
            "http.execute" -> http.execute(
                json.decodeFromJsonElement(PluginHttpRequest.serializer(), payload),
                requestPolicy,
            ).mapJson { json.encodeToJsonElement(PluginHttpResponse.serializer(), it) }
            "html.query" -> html.query(
                json.decodeFromJsonElement(HtmlQueryRequest.serializer(), payload),
            ).mapJson { json.encodeToJsonElement(HtmlQueryResponse.serializer(), it) }
            "log.safe" -> logger.log(
                pluginId,
                operation,
                json.decodeFromJsonElement(SafeLogEvent.serializer(), payload),
            ).mapJson { json.parseToJsonElement("null") }
            "auth.getState" -> sessions?.summary(pluginId)?.let { summary ->
                PluginCallResult.Success(
                    buildJsonObject {
                        put("status", summary.status.name.lowercase())
                        summary.expiresAtEpochMillis?.let { put("expiresAtEpochMillis", it) }
                    },
                )
            } ?: PluginCallResult.Failure("plugin.auth_unavailable", retryable = false)
            else -> PluginCallResult.Failure("plugin.capability_denied", retryable = false)
        }
    } catch (_: RuntimeException) {
        PluginCallResult.Failure("plugin.capability_payload_invalid", retryable = false)
    }
}

private inline fun <T> PluginCallResult<T>.mapJson(transform: (T) -> JsonElement): PluginCallResult<JsonElement> =
    when (this) {
        is PluginCallResult.Success -> PluginCallResult.Success(transform(value))
        is PluginCallResult.Failure -> this
    }
