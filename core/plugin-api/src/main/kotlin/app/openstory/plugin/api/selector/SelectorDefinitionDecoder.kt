package app.openstory.plugin.api.selector

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonPrimitive

sealed interface DecodedSelectorDefinition {
    data class V1(
        val definition: SelectorPluginDefinition,
    ) : DecodedSelectorDefinition

    data class V2(
        val definition: SelectorPluginDefinitionV2,
    ) : DecodedSelectorDefinition
}

class SelectorDefinitionDecoder(
    private val json: Json = SELECTOR_JSON,
) {
    fun decode(source: String): Result<DecodedSelectorDefinition> =
        runCatching {
            val root = json.parseToJsonElement(source) as? JsonObject
                ?: selectorFail(
                    SelectorValidationErrorCode.INVALID_DEFINITION,
                    "Selector definition root must be an object.",
                )

            when (root["schemaVersion"]?.jsonPrimitive?.intOrNull) {
                SelectorPluginDefinition.CURRENT_SCHEMA_VERSION ->
                    DecodedSelectorDefinition.V1(
                        json.decodeFromJsonElement(
                            SelectorPluginDefinition.serializer(),
                            root,
                        ),
                    )

                SelectorPluginDefinitionV2.CURRENT_SCHEMA_VERSION ->
                    DecodedSelectorDefinition.V2(
                        json.decodeFromJsonElement(
                            SelectorPluginDefinitionV2.serializer(),
                            root,
                        ),
                    )

                else -> selectorFail(
                    SelectorValidationErrorCode.UNSUPPORTED_SCHEMA_VERSION,
                    "Unsupported selector schema version.",
                )
            }
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
