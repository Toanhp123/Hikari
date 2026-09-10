package app.openstory.catalog.domain.asset

import app.openstory.catalog.domain.limits.CatalogInputLimits
import app.openstory.catalog.domain.limits.strictUtf8

@JvmInline
value class RemoteHttpsUriV1 private constructor(val value: String) {
    companion object {
        fun parseAndNormalize(raw: String): RemoteHttpsUriV1 {
            val parts = parseAbsoluteHttps(raw)
            val querySuffix = parts.rawQuery?.let { "?$it" }.orEmpty()
            return RemoteHttpsUriV1("https://${parts.host}${parts.rawPath}$querySuffix")
        }

        fun resolveAndNormalize(
            current: RemoteHttpsUriV1,
            redirectTarget: String,
        ): RemoteHttpsUriV1 {
            validateRawUriText(redirectTarget, enforceLocatorLimit = true)
            return when {
                redirectTarget.startsWith("//") -> parseAndNormalize("https:$redirectTarget")
                hasScheme(redirectTarget) -> parseAndNormalize(redirectTarget)
                else -> {
                    val base = parseAbsoluteHttps(current.value)
                    val target = splitPathAndQuery(redirectTarget)
                    val resolvedPath = when {
                        target.rawPath.isEmpty() -> base.rawPath
                        target.rawPath.startsWith('/') -> removeDotSegments(target.rawPath)
                        else -> removeDotSegments(mergeRelativePath(base.rawPath, target.rawPath))
                    }
                    val resolvedQuery = when {
                        target.rawQuery != null -> target.rawQuery
                        target.rawPath.isEmpty() -> base.rawQuery
                        else -> null
                    }
                    val querySuffix = resolvedQuery?.let { "?$it" }.orEmpty()
                    parseAndNormalize("https://${base.host}$resolvedPath$querySuffix")
                }
            }
        }
    }
}

internal fun canonicalizeDnsHost(rawHost: String): String = DnsHostCanonicalizer.canonicalize(rawHost)

private fun parseAbsoluteHttps(raw: String): AbsoluteHttpsParts {
    validateRawUriText(raw, enforceLocatorLimit = true)
    val schemeEnd = raw.indexOf(':')
    require(schemeEnd > 0 && isValidScheme(raw.substring(0, schemeEnd)))
    require(raw.substring(0, schemeEnd).equals(HTTPS_SCHEME, ignoreCase = true))
    require(raw.startsWith(AUTHORITY_PREFIX, startIndex = schemeEnd + 1))

    val authorityStart = schemeEnd + HTTPS_AUTHORITY_OFFSET
    val authorityEnd = raw.indexOfFirstFrom(authorityStart) { it == '/' || it == '?' }
        .let { if (it < 0) raw.length else it }
    val authority = raw.substring(authorityStart, authorityEnd)
    val hostAndPort = parseAuthority(authority)
    require(hostAndPort.port == null || hostAndPort.port == HTTPS_PORT)

    val pathAndQuery = splitPathAndQuery(raw.substring(authorityEnd))
    require(pathAndQuery.rawPath.isEmpty() || pathAndQuery.rawPath.startsWith('/'))
    return AbsoluteHttpsParts(
        host = canonicalizeDnsHost(hostAndPort.host),
        rawPath = pathAndQuery.rawPath.ifEmpty { "/" },
        rawQuery = pathAndQuery.rawQuery,
    )
}

private fun validateRawUriText(raw: String, enforceLocatorLimit: Boolean) {
    if (enforceLocatorLimit) require(raw.length <= CatalogInputLimits.COVER_LOCATOR_CHARS)
    strictUtf8(raw)
    require(raw.none { it.isWhitespace() || it.isISOControl() || it in FORBIDDEN_RAW_URI_CHARS })
    require('#' !in raw)
    validatePercentEscapes(raw)
}

private fun validatePercentEscapes(raw: String) {
    raw.forEachIndexed { index, character ->
        if (character == '%') {
            require(index + PERCENT_ESCAPE_WIDTH <= raw.lastIndex)
            require(raw[index + 1].isHexDigit() && raw[index + 2].isHexDigit())
        }
    }
}

private fun splitPathAndQuery(value: String): PathAndQuery {
    val queryStart = value.indexOf('?')
    return if (queryStart < 0) {
        PathAndQuery(value, null)
    } else {
        PathAndQuery(value.substring(0, queryStart), value.substring(queryStart + 1))
    }
}

private fun parseAuthority(authority: String): HostAndPort {
    require(authority.isNotEmpty() && '@' !in authority)
    require('[' !in authority && ']' !in authority)
    val colon = authority.lastIndexOf(':')
    if (colon < 0) return HostAndPort(authority, null)
    require(authority.indexOf(':') == colon)
    val portText = authority.substring(colon + 1)
    require(portText.isNotEmpty() && portText.all(Char::isDigit))
    return HostAndPort(authority.substring(0, colon), portText.toInt())
}

private fun hasScheme(value: String): Boolean {
    val colon = value.indexOf(':')
    if (colon <= 0) return false
    val pathBoundary = listOf(value.indexOf('/'), value.indexOf('?'))
        .filter { it >= 0 }
        .minOrNull() ?: value.length
    return colon < pathBoundary && isValidScheme(value.substring(0, colon))
}

private fun isValidScheme(value: String): Boolean =
    value.isNotEmpty() && value.first().isAsciiLetter() && value.drop(1).all { character ->
        character.isAsciiLetterOrDigit() || character == '+' || character == '-' || character == '.'
    }

private fun mergeRelativePath(basePath: String, relativePath: String): String =
    basePath.substringBeforeLast('/', missingDelimiterValue = "") + "/" + relativePath

private fun removeDotSegments(path: String): String {
    var input = path
    val output = StringBuilder()
    while (input.isNotEmpty()) {
        input = when {
            input.startsWith("../") -> input.removePrefix("../")
            input.startsWith("./") -> input.removePrefix("./")
            input.startsWith("/./") -> input.removePrefix("/.")
            input == "/." -> "/"
            input.startsWith("/../") -> {
                removeLastPathSegment(output)
                input.removePrefix("/..")
            }
            input == "/.." -> {
                removeLastPathSegment(output)
                "/"
            }
            input == "." || input == ".." -> ""
            else -> moveFirstPathSegment(input, output)
        }
    }
    return output.toString().ifEmpty { "/" }
}

private fun moveFirstPathSegment(input: String, output: StringBuilder): String {
    val nextSlash = input.indexOf('/', startIndex = if (input.startsWith('/')) 1 else 0)
    val segmentEnd = if (nextSlash < 0) input.length else nextSlash
    output.append(input.substring(0, segmentEnd))
    return input.substring(segmentEnd)
}

private fun removeLastPathSegment(output: StringBuilder) {
    val slash = output.lastIndexOf('/')
    output.delete(if (slash < 0) 0 else slash, output.length)
}

private fun String.indexOfFirstFrom(startIndex: Int, predicate: (Char) -> Boolean): Int {
    for (index in startIndex until length) {
        if (predicate(this[index])) return index
    }
    return -1
}

private data class AbsoluteHttpsParts(
    val host: String,
    val rawPath: String,
    val rawQuery: String?,
)

private data class PathAndQuery(
    val rawPath: String,
    val rawQuery: String?,
)

private data class HostAndPort(
    val host: String,
    val port: Int?,
)

private const val HTTPS_SCHEME = "https"
private const val AUTHORITY_PREFIX = "//"
private const val HTTPS_AUTHORITY_OFFSET = 3
private const val HTTPS_PORT = 443
private const val PERCENT_ESCAPE_WIDTH = 2
private const val FORBIDDEN_RAW_URI_CHARS = "\\\"<>^`{|}"
