package app.openstory.downloads.blob

import app.openstory.common.id.ChapterReleaseId
import java.io.ByteArrayInputStream
import java.io.InputStream
import java.security.MessageDigest

private const val HEX_NIBBLE_SHIFT = 4

enum class ChapterBlobNamespace {
    AUTOMATIC_CACHE,
    EXPLICIT_DOWNLOAD,
}

data class ChapterBlobKey(
    val namespace: ChapterBlobNamespace,
    val releaseId: ChapterReleaseId,
    val contentFingerprint: String,
) {
    init {
        require(contentFingerprint.isNotBlank()) { "Content fingerprint must not be blank." }
    }
}

@JvmInline
value class BlobChecksum(
    val value: String,
) {
    init {
        require(value.matches(SHA256_HEX)) { "Blob checksum must be a lowercase SHA-256 hex string." }
    }

    companion object {
        private val SHA256_HEX = Regex("[0-9a-f]{64}")
        private val HEX = "0123456789abcdef".toCharArray()

        fun sha256(bytes: ByteArray): BlobChecksum = sha256(bytes, 0, bytes.size)

        internal fun sha256(bytes: ByteArray, offset: Int, length: Int): BlobChecksum {
            require(offset >= 0 && length >= 0 && offset <= bytes.size - length) {
                "Blob checksum range is outside the source bytes."
            }
            val digest = MessageDigest.getInstance("SHA-256").apply {
                update(bytes, offset, length)
            }.digest()
            val encoded = CharArray(digest.size * 2)
            digest.forEachIndexed { index, byte ->
                val unsigned = byte.toInt() and 0xff
                encoded[index * 2] = HEX[unsigned ushr HEX_NIBBLE_SHIFT]
                encoded[index * 2 + 1] = HEX[unsigned and 0x0f]
            }
            return BlobChecksum(encoded.concatToString())
        }
    }
}

class ChapterBlob private constructor(
    private val content: ByteArray,
    val checksum: BlobChecksum,
) {
    val sizeBytes: Int
        get() = content.size

    fun bytes(): ByteArray = content.copyOf()

    fun inputStream(): InputStream = ByteArrayInputStream(content)

    companion object {
        fun fromBytes(bytes: ByteArray): ChapterBlob {
            val content = bytes.copyOf()
            return ChapterBlob(content, BlobChecksum.sha256(content))
        }

        fun verified(bytes: ByteArray, checksum: BlobChecksum): ChapterBlob {
            val content = bytes.copyOf()
            require(BlobChecksum.sha256(content) == checksum) { "Blob checksum does not match its bytes." }
            return ChapterBlob(content, checksum)
        }

        fun verified(
            bytes: ByteArray,
            offset: Int,
            length: Int,
            checksum: BlobChecksum,
        ): ChapterBlob {
            require(offset >= 0 && length >= 0 && offset <= bytes.size - length) {
                "Blob payload range is outside the source bytes."
            }
            val content = ByteArray(length)
            bytes.copyInto(content, destinationOffset = 0, startIndex = offset, endIndex = offset + length)
            require(BlobChecksum.sha256(content) == checksum) { "Blob checksum does not match its bytes." }
            return ChapterBlob(content, checksum)
        }
    }
}
