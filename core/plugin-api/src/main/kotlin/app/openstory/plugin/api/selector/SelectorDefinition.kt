package app.openstory.plugin.api.selector

import app.openstory.plugin.api.selector.catalog.CatalogSelectorEndpoints
import app.openstory.plugin.api.selector.content.ContentSelectorEndpoints
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonPrimitive

@Serializable
data class SelectorDefinition(
    val schemaVersion: Int = CURRENT_SCHEMA_VERSION,
    val catalog: CatalogSelectorEndpoints? = null,
    val content: ContentSelectorEndpoints? = null,
) {
    companion object {
        const val CURRENT_SCHEMA_VERSION = 1
    }
}

class SelectorDefinitionDecoder(
    private val json: Json = SELECTOR_JSON,
) {
    fun decode(source: String): Result<SelectorDefinition> =
        runCatching {
            val root = json.parseToJsonElement(source) as? JsonObject
                ?: selectorFail(
                    SelectorValidationErrorCode.INVALID_DEFINITION,
                    "Selector definition root must be an object.",
                )

            if (
                root["schemaVersion"]?.jsonPrimitive?.intOrNull !=
                SelectorDefinition.CURRENT_SCHEMA_VERSION
            ) {
                selectorFail(
                    SelectorValidationErrorCode.UNSUPPORTED_SCHEMA_VERSION,
                    "Unsupported selector schema version.",
                )
            }

            json.decodeFromJsonElement(
                SelectorDefinition.serializer(),
                root,
            )
        }.recoverCatching { exception ->
            if (exception is SelectorValidationException) {
                throw exception
            }
            throw SelectorValidationException(
                code = SelectorValidationErrorCode.INVALID_DEFINITION,
                message = "Selector definition is invalid.",
                cause = exception,
            )
        }
}

internal val SELECTOR_JSON = Json {
    classDiscriminator = "type"
    ignoreUnknownKeys = false
    explicitNulls = false
    encodeDefaults = true
}
