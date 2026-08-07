package app.openstory.plugin.api

import java.net.URI
import java.util.Locale

internal val PLUGIN_ID_PATTERN =
    Regex("""[a-z0-9]+(?:[._-][a-z0-9]+)+""")

internal val SEMANTIC_VERSION_PATTERN =
    Regex("""\d+\.\d+\.\d+(?:[-+][0-9A-Za-z.-]+)?""")

internal val SHA_256_PATTERN = Regex("""[0-9a-f]{64}""")

internal val HOST_PATTERN =
    Regex("""(?:[a-z0-9](?:[a-z0-9-]*[a-z0-9])?\.)+[a-z0-9](?:[a-z0-9-]*[a-z0-9])?""")

internal val LANGUAGE_TAG_PATTERN =
    Regex("""[a-z]{2,8}(?:-[a-z0-9]{1,8})*""")

internal fun httpsHost(value: String): String? =
    runCatching {
        val uri = URI(value)
        val host = uri.host?.lowercase(Locale.ROOT)

        host?.takeIf {
            uri.isAbsolute &&
                uri.scheme.equals("https", ignoreCase = true) &&
                uri.userInfo == null &&
                host.matches(HOST_PATTERN)
        }
    }.getOrNull()

internal fun isHttpsUrl(value: String): Boolean = httpsHost(value) != null

internal fun requireStableId(
    value: String,
    label: String,
) {
    require(value.isNotBlank()) {
        "$label must not be blank."
    }
    require(value.none(Char::isWhitespace)) {
        "$label must not contain whitespace."
    }
    require(value.none(Char::isISOControl)) {
        "$label must not contain control characters."
    }
}

internal fun requireNormalizedLanguageTag(
    value: String,
    label: String = "Language tag",
) {
    require(value.matches(LANGUAGE_TAG_PATTERN)) {
        "$label must be a normalized lowercase language tag."
    }
}

internal fun requireNormalizedLanguageTags(
    values: Set<String>,
    label: String = "Language tags",
) {
    require(values.isNotEmpty()) {
        "$label must not be empty."
    }
    values.forEach { requireNormalizedLanguageTag(it, label) }
}

internal fun requireNonBlankDistinct(
    values: List<String>,
    label: String,
) {
    require(values.all(String::isNotBlank)) {
        "$label must not contain blank values."
    }
    require(values.distinct().size == values.size) {
        "$label must not contain duplicate values."
    }
}

internal fun compareSemanticVersions(
    left: String,
    right: String,
): Int? {
    if (!left.matches(SEMANTIC_VERSION_PATTERN) ||
        !right.matches(SEMANTIC_VERSION_PATTERN)
    ) {
        return null
    }

    val leftCore = left.substringBefore('-').substringBefore('+')
        .split('.')
        .map(String::toLong)
    val rightCore = right.substringBefore('-').substringBefore('+')
        .split('.')
        .map(String::toLong)

    return leftCore.zip(rightCore)
        .firstOrNull { (leftPart, rightPart) -> leftPart != rightPart }
        ?.let { (leftPart, rightPart) -> leftPart.compareTo(rightPart) }
        ?: 0
}
