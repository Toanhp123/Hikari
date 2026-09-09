package app.openstory.catalog.domain.limits

import java.io.ByteArrayOutputStream
import java.security.MessageDigest

internal fun domainSeparatedSha256(
    prefix: String,
    fields: List<ByteArray>,
): String {
    val input = ByteArrayOutputStream()
    input.write(prefix.toByteArray(Charsets.US_ASCII))
    input.write(0)
    fields.forEach { bytes ->
        require(bytes.size.toLong() <= UINT32_MAX)
        input.write(uint32BigEndian(bytes.size))
        input.write(bytes)
    }
    return MessageDigest.getInstance("SHA-256")
        .digest(input.toByteArray())
        .joinToString(separator = "") { byte -> "%02x".format(byte.toInt() and BYTE_MASK) }
}

private fun uint32BigEndian(value: Int): ByteArray = byteArrayOf(
    (value ushr HIGH_BYTE_SHIFT).toByte(),
    (value ushr MID_HIGH_BYTE_SHIFT).toByte(),
    (value ushr MID_LOW_BYTE_SHIFT).toByte(),
    value.toByte(),
)

private const val UINT32_MAX = 0xffff_ffffL
private const val BYTE_MASK = 0xff
private const val HIGH_BYTE_SHIFT = 24
private const val MID_HIGH_BYTE_SHIFT = 16
private const val MID_LOW_BYTE_SHIFT = 8
