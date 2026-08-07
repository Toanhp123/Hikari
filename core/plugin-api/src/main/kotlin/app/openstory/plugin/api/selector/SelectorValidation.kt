package app.openstory.plugin.api.selector

import app.openstory.plugin.api.PluginManifest
import app.openstory.plugin.api.selector.validation.SelectorBindingValidator
import app.openstory.plugin.api.selector.validation.SelectorDefinitionValidator
import app.openstory.plugin.api.selector.validation.SelectorRequestValidator
import app.openstory.plugin.api.selector.validation.SelectorSyntaxValidator

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
        definition: SelectorDefinition,
        manifest: PluginManifest,
    ): Result<Unit> = SelectorDefinitionValidator.validate(
        definition = definition,
        manifest = manifest,
    )

    fun validateRequestPlan(
        request: SelectorRequestPlan,
        manifest: PluginManifest,
    ): Result<Unit> = SelectorRequestValidator.validate(
        request = request,
        manifest = manifest,
    )

    fun validateBinding(binding: SelectorBinding): Result<Unit> =
        SelectorBindingValidator.validate(binding)

    fun validateCssForContract(css: String): Result<Unit> = runCatching {
        SelectorSyntaxValidator.validateCss(css)
    }
}

internal fun selectorFail(
    code: SelectorValidationErrorCode,
    message: String,
): Nothing = throw SelectorValidationException(code, message)
