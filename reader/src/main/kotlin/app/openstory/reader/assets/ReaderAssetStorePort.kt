package app.openstory.reader.assets

import app.openstory.common.id.CanonicalChapterId
import app.openstory.common.id.ChapterReleaseId
import app.openstory.common.id.StoryId
import app.openstory.reader.routing.ReaderSessionId
import java.io.InputStream

enum class ReaderAssetLocalPresence { UNKNOWN, LOCAL_AVAILABLE, LOCAL_MISSING, LOCAL_UNAVAILABLE }

enum class ReaderAssetCachePressure { NORMAL, PRESSURED, EMERGENCY }

enum class ReaderAssetProtectionClass {
    ACTIVE_INTERACTIVE,
    ACTIVE_CONSUMED,
    RECENT_HISTORY_1,
    RECENT_HISTORY_2,
    CURRENT_AHEAD_SPECULATIVE,
    TRANSITION_SPECULATIVE,
}

data class ReaderAssetActiveProtections(
    val byKey: Map<ReaderAssetKeyHash, ReaderAssetProtectionClass>,
) {
    companion object {
        val EMPTY = ReaderAssetActiveProtections(emptyMap())
    }
}

interface ReaderAssetReadLease : AutoCloseable {
    val sizeBytes: Long
    fun openStream(): InputStream
}

sealed interface ReaderAssetOpenResult {
    data class Available(val lease: ReaderAssetReadLease) : ReaderAssetOpenResult
    data object Missing : ReaderAssetOpenResult
    data object Corrupt : ReaderAssetOpenResult
    data object Unavailable : ReaderAssetOpenResult
}

interface ReaderAssetDurableWriteAuthority

data class ReaderAssetCommitFacts(
    val key: ReaderPageAssetKey,
    val storyId: StoryId,
    val canonicalChapterId: CanonicalChapterId,
    val releaseId: ChapterReleaseId,
    val sourceNamespace: ReaderAssetSourceNamespace,
    val securityScope: ReaderCacheSecurityScope,
    val contentVariant: ReaderContentVariant,
    val identityMode: ReaderAssetIdentityMode,
    val persistenceMode: ReaderAssetPersistenceMode,
    val imageSetNamespace: ReaderImageSetNamespace,
    val imageOrdinal: Int,
) {
    init {
        require(imageOrdinal >= 0) { "Reader image ordinal must be non-negative" }
        require(key.sourceNamespace == sourceNamespace)
        require(key.securityScope == securityScope)
        require(key.contentVariant == contentVariant)
        require(key.persistenceMode == persistenceMode)
        require(key.imageSetNamespace == imageSetNamespace)
    }
}

sealed interface ReaderAssetCommitResult {
    data object Persisted : ReaderAssetCommitResult
    data object Bypassed : ReaderAssetCommitResult
    data class Degraded(val failure: ReaderAssetFailure) : ReaderAssetCommitResult
}

enum class ReaderAssetInvalidationReason { CORRUPT, SECURITY_SCOPE, EXPLICIT_CLEAR }

sealed interface ReaderAssetClearScope {
    data object AllAutomatic : ReaderAssetClearScope
    data class Source(val sourceNamespace: ReaderAssetSourceNamespace) : ReaderAssetClearScope
    data class Account(
        val sourceNamespace: ReaderAssetSourceNamespace,
        val stableNonSecretNamespace: String,
    ) : ReaderAssetClearScope {
        init {
            require(stableNonSecretNamespace.isNotBlank()) { "Account clear scope must not be blank" }
        }
    }

    data class AllAccountScopesForSource(
        val sourceNamespace: ReaderAssetSourceNamespace,
    ) : ReaderAssetClearScope
}

interface ReaderAssetStorePort {
    suspend fun inspect(keys: Set<ReaderPageAssetKey>): Map<ReaderPageAssetKey, ReaderAssetLocalPresence>
    suspend fun openLocal(key: ReaderPageAssetKey): ReaderAssetOpenResult
    suspend fun captureDurableWriteAuthority(facts: ReaderAssetCommitFacts): ReaderAssetDurableWriteAuthority?
    suspend fun commit(
        facts: ReaderAssetCommitFacts,
        authority: ReaderAssetDurableWriteAuthority,
        payload: ReaderAssetPayload,
    ): ReaderAssetCommitResult
    suspend fun markConsumed(key: ReaderPageAssetKey)
    suspend fun invalidate(key: ReaderPageAssetKey, reason: ReaderAssetInvalidationReason)
    suspend fun cachePressure(): ReaderAssetCachePressure
    suspend fun reconcile(activeProtections: ReaderAssetActiveProtections)
    suspend fun releaseSession(sessionId: ReaderSessionId)
    suspend fun clearAutomatic(scope: ReaderAssetClearScope)
}
