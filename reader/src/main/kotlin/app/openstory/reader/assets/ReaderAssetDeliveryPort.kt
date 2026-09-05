package app.openstory.reader.assets

import java.security.MessageDigest
import java.util.Locale

class ReaderAssetPayload private constructor(
    private val encoded: ByteArray,
    val mimeType: String?,
    val sourceIntegrityHash: String?,
) {
    val sizeBytes: Int
        get() = encoded.size

    fun bytes(): ByteArray = encoded.copyOf()

    companion object {
        fun verifiedBounded(
            bytes: ByteArray,
            mimeType: String?,
            sourceIntegrityEvidence: String?,
        ): ReaderAssetPayload {
            require(bytes.isNotEmpty()) { "Reader asset payload must not be empty" }
            require(bytes.size <= ReaderAssetRuntimePolicy.MAX_READER_ASSET_BYTES) {
                "Reader asset payload exceeds the encoded byte limit"
            }
            val normalizedMimeType = mimeType?.trim()?.lowercase(Locale.ROOT)?.takeIf(String::isNotBlank)
            val integrityHash = sourceIntegrityEvidence?.trim()?.takeIf(String::isNotBlank)?.let { evidence ->
                MessageDigest.getInstance("SHA-256")
                    .digest(evidence.toByteArray(Charsets.UTF_8))
                    .toLowerHex()
            }
            return ReaderAssetPayload(bytes.copyOf(), normalizedMimeType, integrityHash)
        }
    }
}

data class ReaderAssetDeliveryRequest(
    val assetKey: ReaderPageAssetKey,
    val deliveryLocator: String,
) {
    init {
        require(deliveryLocator.isNotBlank()) { "Reader delivery locator must not be blank" }
    }
}

sealed interface ReaderAssetDeliveryResult {
    data class Success(val payload: ReaderAssetPayload) : ReaderAssetDeliveryResult
    data class Failure(val failure: ReaderAssetFailure) : ReaderAssetDeliveryResult
}

@JvmInline
value class ReaderAssetConsumerToken(val value: Long) {
    init {
        require(value > 0L) { "Reader asset consumer token must be positive" }
    }
}

sealed interface ReaderAssetRemoteOutcome {
    data class Success(val payload: ReaderAssetPayload) : ReaderAssetRemoteOutcome
    data class Failure(val failure: ReaderAssetFailure) : ReaderAssetRemoteOutcome
}

sealed interface ReaderAssetLoadOutcome {
    data class Local(val lease: ReaderAssetReadLease) : ReaderAssetLoadOutcome
    data class Remote(val payload: ReaderAssetPayload) : ReaderAssetLoadOutcome
    data class Failure(val failure: ReaderAssetFailure) : ReaderAssetLoadOutcome
}

fun interface ReaderAssetDeliveryPort {
    suspend fun fetch(request: ReaderAssetDeliveryRequest): ReaderAssetDeliveryResult
}
