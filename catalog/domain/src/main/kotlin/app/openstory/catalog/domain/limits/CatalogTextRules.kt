package app.openstory.catalog.domain.limits

import java.nio.ByteBuffer
import java.nio.charset.CharacterCodingException
import java.nio.charset.CodingErrorAction
import java.nio.charset.StandardCharsets

internal fun strictUtf8(value: String): ByteArray {
    return try {
        StandardCharsets.UTF_8.newEncoder()
            .onMalformedInput(CodingErrorAction.REPORT)
            .onUnmappableCharacter(CodingErrorAction.REPORT)
            .encode(java.nio.CharBuffer.wrap(value))
            .toByteArray()
    } catch (exception: CharacterCodingException) {
        throw IllegalArgumentException("Malformed UTF-16 input", exception)
    }
}

internal fun requireUtf8Bound(
    value: String,
    maximumBytes: Int,
    requireNonBlank: Boolean = false,
): ByteArray {
    if (requireNonBlank) require(value.isNotBlank())
    val bytes = strictUtf8(value)
    require(bytes.size <= maximumBytes)
    return bytes
}

internal fun requireScalarBound(
    value: String,
    maximumScalars: Int,
    requireNonBlank: Boolean = false,
) {
    strictUtf8(value)
    if (requireNonBlank) require(value.isNotBlank())
    require(value.codePointCount(0, value.length) <= maximumScalars)
}

private fun ByteBuffer.toByteArray(): ByteArray {
    val result = ByteArray(remaining())
    get(result)
    return result
}
