package app.openstory.plugin.host.selector

import java.net.URI

data class SelectorExecutionContext(
    val origin: String? = null,
) {
    init {
        origin?.let(::validateOrigin)
    }

    internal fun resolveUrlTemplate(
        urlTemplate: String,
    ): String? {
        val probeUrl =
            templateVariable.replace(
                urlTemplate,
                "value",
            )

        return when {
            URI(probeUrl).isAbsolute -> urlTemplate
            origin == null -> null
            else -> resolveRelativeTemplate(origin, urlTemplate)
        }
    }
}

private fun resolveRelativeTemplate(
    origin: String,
    urlTemplate: String,
): String {
    val escapedTemplate = urlTemplate
        .replace(oldValue = "{", newValue = "%7B")
        .replace(oldValue = "}", newValue = "%7D")

    return URI(origin)
        .resolve(escapedTemplate)
        .toASCIIString()
        .replace(oldValue = "%7B", newValue = "{", ignoreCase = true)
        .replace(oldValue = "%7D", newValue = "}", ignoreCase = true)
}

private fun validateOrigin(
    origin: String,
) {
    val uri =
        runCatching {
            URI(origin)
        }.getOrElse {
            throw IllegalArgumentException(
                "Selector execution origin must be a valid URI.",
            )
        }

    require(uri.isAbsolute) {
        "Selector execution origin must be absolute."
    }

    require(
        uri.scheme.equals(
            other = "https",
            ignoreCase = true,
        ),
    ) {
        "Selector execution origin must use HTTPS."
    }

    require(
        uri.host != null &&
            uri.userInfo == null,
    ) {
        "Selector execution origin must have a valid host and no user information."
    }

    require(
        uri.rawQuery == null &&
            uri.rawFragment == null,
    ) {
        "Selector execution origin must not contain a query or fragment."
    }
}

private val templateVariable =
    Regex("""\{[A-Za-z][A-Za-z0-9_]*}""")
