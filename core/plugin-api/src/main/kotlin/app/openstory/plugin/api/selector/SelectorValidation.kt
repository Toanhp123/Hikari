package app.openstory.plugin.api.selector

import java.net.URI
import java.util.Locale

enum class SelectorValidationErrorCode {
    EMPTY_PIPELINE,
    UNSUPPORTED_SCHEMA_VERSION,
    BLANK_URL_TEMPLATE,
    PROTOCOL_RELATIVE_URL,
    INSECURE_SCHEME,
    INVALID_ABSOLUTE_URL,
    UNDECLARED_HOST,
    TYPE_MISMATCH,
    BLANK_CSS_SELECTOR,
    BLANK_ATTRIBUTE_NAME,
}

class SelectorValidationException(
    val code: SelectorValidationErrorCode,
    message: String,
) : IllegalArgumentException(message)

object SelectorValidation {

    fun validate(
        definition: SelectorPluginDefinition,
        allowedHosts: Set<String>,
    ): Result<Unit> = runCatching {
        validateSchemaVersion(definition)
        validatePipeline(
            operations = definition.operations,
            allowedHosts = allowedHosts
                .map { it.lowercase(Locale.ROOT) }
                .toSet(),
        )
    }

    private fun validateSchemaVersion(
        definition: SelectorPluginDefinition,
    ) {
        if (
            definition.schemaVersion !=
            SelectorPluginDefinition.CURRENT_SCHEMA_VERSION
        ) {
            fail(
                code = SelectorValidationErrorCode.UNSUPPORTED_SCHEMA_VERSION,
                message =
                    "Unsupported selector schema version " +
                        "${definition.schemaVersion}.",
            )
        }
    }

    private fun validatePipeline(
        operations: List<SelectorOperation>,
        allowedHosts: Set<String>,
    ) {
        if (operations.isEmpty()) {
            fail(
                code = SelectorValidationErrorCode.EMPTY_PIPELINE,
                message = "Selector pipeline must not be empty.",
            )
        }

        var currentType = SelectorValueType.NONE

        operations.forEachIndexed { index, operation ->
            if (operation.inputType != currentType) {
                fail(
                    code = SelectorValidationErrorCode.TYPE_MISMATCH,
                    message =
                        "Operation $index expects ${operation.inputType} " +
                            "but received $currentType.",
                )
            }

            validateOperation(
                operation = operation,
                allowedHosts = allowedHosts,
            )

            currentType = operation.outputType
        }
    }

    private fun validateOperation(
        operation: SelectorOperation,
        allowedHosts: Set<String>,
    ) {
        when (operation) {
            is HttpGet -> validateRequestTemplate(
                template = operation.urlTemplate,
                allowedHosts = allowedHosts,
            )

            is SelectAll ->
                validateCss(operation.css)

            is SelectText ->
                validateCss(operation.css)

            is SelectAttribute -> {
                validateCss(operation.css)

                if (operation.attribute.isBlank()) {
                    fail(
                        code =
                            SelectorValidationErrorCode
                                .BLANK_ATTRIBUTE_NAME,
                        message = "Attribute name must not be blank.",
                    )
                }
            }

            is RemoveElements ->
                validateCss(operation.css)

            is NormalizeWhitespace ->
                Unit
        }
    }

    private fun validateCss(css: String) {
        if (css.isBlank()) {
            fail(
                code = SelectorValidationErrorCode.BLANK_CSS_SELECTOR,
                message = "CSS selector must not be blank.",
            )
        }
    }

    private fun validateRequestTemplate(
        template: String,
        allowedHosts: Set<String>,
    ) {
        if (template.isBlank()) {
            fail(
                code = SelectorValidationErrorCode.BLANK_URL_TEMPLATE,
                message = "Request URL template must not be blank.",
            )
        }

        val probeUrl = TEMPLATE_VARIABLE.replace(template, "value")

        if (probeUrl.startsWith("//")) {
            fail(
                code = SelectorValidationErrorCode.PROTOCOL_RELATIVE_URL,
                message = "Protocol-relative request URLs are not allowed.",
            )
        }

        val uri = runCatching {
            URI(probeUrl)
        }.getOrElse {
            fail(
                code = SelectorValidationErrorCode.INVALID_ABSOLUTE_URL,
                message = "Request URL template is malformed.",
            )
        }

        if (!uri.isAbsolute) {
            return
        }

        if (!uri.scheme.equals("https", ignoreCase = true)) {
            fail(
                code = SelectorValidationErrorCode.INSECURE_SCHEME,
                message = "Absolute request URLs must use HTTPS.",
            )
        }

        val host = uri.host?.lowercase(Locale.ROOT)

        if (host == null || uri.userInfo != null) {
            fail(
                code = SelectorValidationErrorCode.INVALID_ABSOLUTE_URL,
                message =
                    "Absolute request URLs require a valid host " +
                        "and no user information.",
            )
        }

        if (host !in allowedHosts) {
            fail(
                code = SelectorValidationErrorCode.UNDECLARED_HOST,
                message =
                    "Request host '$host' is not declared by the plugin.",
            )
        }
    }

    private fun fail(
        code: SelectorValidationErrorCode,
        message: String,
    ): Nothing = throw SelectorValidationException(
        code = code,
        message = message,
    )

    private val TEMPLATE_VARIABLE =
        Regex("""\{[A-Za-z][A-Za-z0-9_]*}""")
}
