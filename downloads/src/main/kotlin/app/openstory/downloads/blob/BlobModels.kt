package app.openstory.downloads.blob

import app.openstory.common.id.ChapterReleaseId
import java.security.MessageDigest

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

        fun sha256(bytes: ByteArray): BlobChecksum = BlobChecksum(
            MessageDigest.getInstance("SHA-256")
                .digest(bytes)
                .joinToString(separator = "") { byte -> "%02x".format(byte) },
        )
    }
}

class ChapterBlob private constructor(
    private val content: ByteArray,
    val checksum: BlobChecksum,
) {
    fun bytes(): ByteArray = content.copyOf()

    companion object {
        fun fromBytes(bytes: ByteArray): ChapterBlob = verified(bytes, BlobChecksum.sha256(bytes))

        fun verified(bytes: ByteArray, checksum: BlobChecksum): ChapterBlob {
            require(BlobChecksum.sha256(bytes) == checksum) { "Blob checksum does not match its bytes." }
            return ChapterBlob(bytes.copyOf(), checksum)
        }
    }
}
