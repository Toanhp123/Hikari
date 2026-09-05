package app.openstory.downloads.cache

import app.openstory.reader.assets.ReaderAssetSourceNamespace

sealed interface AutomaticCacheWriteScope {
    data object GlobalAutomatic : AutomaticCacheWriteScope

    data class ReaderAssetSource(
        val sourceNamespace: ReaderAssetSourceNamespace,
    ) : AutomaticCacheWriteScope

    data class ReaderAssetAccount(
        val sourceNamespace: ReaderAssetSourceNamespace,
        val securityScopeHash: String,
    ) : AutomaticCacheWriteScope {
        init {
            require(securityScopeHash.matches(SHA256)) {
                "Reader asset account scope must be a lowercase SHA-256 hash."
            }
        }
    }
}

@ConsistentCopyVisibility
data class AutomaticCacheWriteAuthority internal constructor(
    internal val globalEpoch: Long,
    internal val scopedEpoch: Long,
    internal val scope: AutomaticCacheWriteScope,
)

@ConsistentCopyVisibility
data class AutomaticCacheReservation internal constructor(
    val id: Long,
    val bytes: Long,
) {
    init {
        require(id > 0L)
        require(bytes >= 0L)
    }
}

sealed interface AutomaticCachePublicationResult<out T> {
    data class Published<T>(val value: T) : AutomaticCachePublicationResult<T>
    data object Revoked : AutomaticCachePublicationResult<Nothing>
}

sealed interface AutomaticCacheInvalidationScope {
    data object AllAutomatic : AutomaticCacheInvalidationScope

    data class ReaderAssetSource(
        val sourceNamespace: ReaderAssetSourceNamespace,
    ) : AutomaticCacheInvalidationScope

    data class ReaderAssetAccount(
        val sourceNamespace: ReaderAssetSourceNamespace,
        val securityScopeHash: String,
    ) : AutomaticCacheInvalidationScope {
        init {
            require(securityScopeHash.matches(SHA256)) {
                "Reader asset account scope must be a lowercase SHA-256 hash."
            }
        }
    }

    data class AllReaderAssetAccountsForSource(
        val sourceNamespace: ReaderAssetSourceNamespace,
    ) : AutomaticCacheInvalidationScope
}

private val SHA256 = Regex("[0-9a-f]{64}")
