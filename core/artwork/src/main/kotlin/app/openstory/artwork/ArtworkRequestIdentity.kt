package app.openstory.artwork

import java.nio.charset.StandardCharsets
import java.security.MessageDigest

@JvmInline
value class ArtworkAuthorityKey(val value: String) {
    init {
        require(value.isNotBlank())
    }
}

data class ArtworkRequestIdentity(
    val authority: ArtworkAuthorityKey,
    val stableAssetKey: String,
    val locator: String,
    val transformKey: String,
    val varyKey: String,
) {
    init {
        require(stableAssetKey.isNotBlank())
        require(locator.isNotBlank())
        require(transformKey.isNotBlank())
        require(varyKey.isNotBlank())
    }

    val encodedCacheKey: String
        get() = cacheKey(
            domain = "artwork-encoded-v1",
            authority.value,
            stableAssetKey,
            locator,
            varyKey,
        )

    val decodedCacheKey: String
        get() = cacheKey(
            domain = "artwork-decoded-v1",
            encodedCacheKey,
            transformKey,
        )

    private fun cacheKey(domain: String, vararg parts: String): String {
        val digest = MessageDigest.getInstance("SHA-256")
        digest.updateLengthPrefixed(domain)
        parts.forEach { part -> digest.updateLengthPrefixed(part) }
        return digest.digest().joinToString(separator = "") { byte -> "%02x".format(byte) }
    }

    private fun MessageDigest.updateLengthPrefixed(value: String) {
        val bytes = value.toByteArray(StandardCharsets.UTF_8)
        update((bytes.size ushr 24).toByte())
        update((bytes.size ushr 16).toByte())
        update((bytes.size ushr 8).toByte())
        update(bytes.size.toByte())
        update(bytes)
    }
}
