package app.openstory.plugin.api.selector

import app.openstory.plugin.api.PluginManifest

enum class SelectorValidationErrorCode {
    EMPTY_PIPELINE,
    EMPTY_DEFINITION,
    EMPTY_ENDPOINT_GROUP,
    UNSUPPORTED_SCHEMA_VERSION,
    INVALID_DEFINITION,
    BLANK_URL_TEMPLATE,
    PROTOCOL_RELATIVE_URL,
    INSECURE_SCHEME,
    INVALID_ABSOLUTE_URL,
    UNDECLARED_HOST,
    TYPE_MISMATCH,
    BLANK_CSS_SELECTOR,
    BLANK_ATTRIBUTE_NAME,
    INVALID_TEMPLATE_VARIABLE,
    INVALID_REQUEST_LIMIT,
    INVALID_DECLARATIVE_ORIGIN,
    EXCESSIVE_OPERATION_COUNT,
    EXCESSIVE_BINDING_DEPTH,
    EXCESSIVE_BINDING_COUNT,
    INVALID_BINDING_TYPE,
    INVALID_BINDING_PATH,
    INVALID_CONSTANT,
    DUPLICATE_ENDPOINT,
    DUPLICATE_FIELD_BINDING,
    OUTPUT_TYPE_MISMATCH,
    INVALID_TIMESTAMP_CONFIGURATION,
}

class SelectorValidationException(
    val code: SelectorValidationErrorCode,
    message: String,
    cause: Throwable? = null,
) : IllegalArgumentException(message, cause)

object SelectorValidation {
    fun validate(
        definition: SelectorPluginDefinition,
        allowedHosts: Set<String>,
    ): Result<Unit> = SelectorRequestPlanValidation.validateV1(
        definition = definition,
        allowedHosts = allowedHosts,
    )

    fun validate(
        definition: SelectorPluginDefinitionV2,
        manifest: PluginManifest,
    ): Result<Unit> = SelectorV2DefinitionValidation.validate(
        definition = definition,
        manifest = manifest,
    )

    fun validateRequestPlan(
        request: SelectorRequestPlan,
        manifest: PluginManifest,
    ): Result<Unit> = SelectorRequestPlanValidation.validate(
        request = request,
        manifest = manifest,
    )

    fun validateBinding(binding: SelectorBinding): Result<Unit> =
        SelectorBindingValidation.validate(binding)

    fun validateCssForContract(css: String): Result<Unit> = runCatching {
        SelectorSyntaxValidation.validateCss(css)
    }
}
