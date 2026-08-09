package app.openstory.plugin.api.selector.validation

import app.openstory.plugin.api.selector.SelectorValidationErrorCode
import app.openstory.plugin.api.selector.selectorFail
import java.net.URI
import java.util.Locale

internal object SelectorSyntaxValidator {
    fun validateRequestTemplate(
        template: String,
        allowedHosts: Set<String>,
        declarativeOrigin: String?,
        relativeRequiresOrigin: Boolean,
    ) {
        validateTemplateCharacters(template)
        val probe = replaceTemplateVariables(template)
        if (probe.startsWith("//")) {
            selectorFail(
                SelectorValidationErrorCode.PROTOCOL_RELATIVE_URL,
                "Protocol-relative request URLs are not allowed.",
            )
        }
        val uri = parseUri(probe)
        if (!uri.isAbsolute) {
            if (relativeRequiresOrigin && declarativeOrigin == null) {
                selectorFail(
                    SelectorValidationErrorCode.INVALID_DECLARATIVE_ORIGIN,
                    "Relative request URL requires a declarative origin.",
                )
            }
            return
        }
        validateAbsoluteUri(uri, allowedHosts)
    }

    fun validateCss(css: String) {
        if (css.isBlank()) {
            selectorFail(
                SelectorValidationErrorCode.BLANK_CSS_SELECTOR,
                "CSS selector must not be blank.",
            )
        }
        if (css.length > MAX_CSS_LENGTH || css.any(Char::isISOControl)) {
            selectorFail(
                SelectorValidationErrorCode.BLANK_CSS_SELECTOR,
                "CSS selector is invalid.",
            )
        }
    }

    fun validateAttribute(attribute: String) {
        if (attribute.isBlank() ||
            attribute.length > MAX_ATTRIBUTE_LENGTH ||
            attribute.any(Char::isISOControl)
        ) {
            selectorFail(
                SelectorValidationErrorCode.BLANK_ATTRIBUTE_NAME,
                "Attribute name is invalid.",
            )
        }
    }

    private fun validateTemplateCharacters(template: String) {
        if (template.isBlank()) {
            selectorFail(
                SelectorValidationErrorCode.BLANK_URL_TEMPLATE,
                "Request URL template must not be blank.",
            )
        }
        if (template.length > MAX_URL_TEMPLATE_LENGTH ||
            template.any(Char::isISOControl) ||
            '\\' in template
        ) {
            selectorFail(
                SelectorValidationErrorCode.INVALID_ABSOLUTE_URL,
                "Request URL template is invalid.",
            )
        }
    }

    private fun replaceTemplateVariables(template: String): String {
        val replaced = TEMPLATE_VARIABLE.replace(template, "value")
        if ('{' in replaced || '}' in replaced) {
            selectorFail(
                SelectorValidationErrorCode.INVALID_TEMPLATE_VARIABLE,
                "Request URL template contains an invalid variable.",
            )
        }
        return replaced
    }

    private fun parseUri(value: String): URI = runCatching { URI(value) }
        .getOrElse {
            selectorFail(
                SelectorValidationErrorCode.INVALID_ABSOLUTE_URL,
                "Request URL template is malformed.",
            )
        }

    private fun validateAbsoluteUri(
        uri: URI,
        allowedHosts: Set<String>,
    ) {
        if (!uri.scheme.equals("https", ignoreCase = true)) {
            selectorFail(
                SelectorValidationErrorCode.INSECURE_SCHEME,
                "Absolute request URLs must use HTTPS.",
            )
        }
        val host = uri.host?.lowercase(Locale.ROOT)
        if (host == null || uri.userInfo != null || uri.fragment != null) {
            selectorFail(
                SelectorValidationErrorCode.INVALID_ABSOLUTE_URL,
                "Absolute request URLs require a valid host and no user information or fragment.",
            )
        }
        if (host !in allowedHosts) {
            selectorFail(
                SelectorValidationErrorCode.UNDECLARED_HOST,
                "Request host is not declared by the plugin.",
            )
        }
    }

    private const val MAX_URL_TEMPLATE_LENGTH = 2_048
    private const val MAX_CSS_LENGTH = 1_024
    private const val MAX_ATTRIBUTE_LENGTH = 128
    private val TEMPLATE_VARIABLE = Regex("""\{[A-Za-z][A-Za-z0-9_]*\}""")
}
