package app.openstory.reader.ui

import app.openstory.reader.assets.ReaderAssetActiveProtections
import app.openstory.reader.assets.ReaderAssetCachePressure
import app.openstory.reader.assets.ReaderAssetClearScope
import app.openstory.reader.assets.ReaderAssetCommitFacts
import app.openstory.reader.assets.ReaderAssetCommitResult
import app.openstory.reader.assets.ReaderAssetCoordinator
import app.openstory.reader.assets.ReaderAssetDurableWriteAuthority
import app.openstory.reader.assets.ReaderAssetInvalidationReason
import app.openstory.reader.assets.ReaderAssetLocalPresence
import app.openstory.reader.assets.ReaderAssetOpenResult
import app.openstory.reader.assets.ReaderAssetPayload
import app.openstory.reader.assets.ReaderAssetStorePort
import app.openstory.reader.assets.ReaderPageAssetKey
import app.openstory.reader.routing.ReaderNetworkFactsPort
import app.openstory.reader.routing.ReaderNetworkState
import app.openstory.reader.routing.ReaderSessionId
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob

internal fun testReaderAssetCoordinator(): ReaderAssetCoordinator = ReaderAssetCoordinator(
    store = TestReaderAssetStore,
    networkFacts = ReaderNetworkFactsPort { ReaderNetworkState.OFFLINE },
    coordinatorScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate),
)

private object TestReaderAssetStore : ReaderAssetStorePort {
    override suspend fun inspect(keys: Set<ReaderPageAssetKey>): Map<ReaderPageAssetKey, ReaderAssetLocalPresence> =
        keys.associateWith { ReaderAssetLocalPresence.LOCAL_MISSING }

    override suspend fun openLocal(key: ReaderPageAssetKey): ReaderAssetOpenResult = ReaderAssetOpenResult.Missing

    override suspend fun captureDurableWriteAuthority(
        facts: ReaderAssetCommitFacts,
    ): ReaderAssetDurableWriteAuthority? = null

    override suspend fun commit(
        facts: ReaderAssetCommitFacts,
        authority: ReaderAssetDurableWriteAuthority,
        payload: ReaderAssetPayload,
    ): ReaderAssetCommitResult = ReaderAssetCommitResult.Bypassed

    override suspend fun markConsumed(key: ReaderPageAssetKey) = Unit

    override suspend fun invalidate(
        key: ReaderPageAssetKey,
        reason: ReaderAssetInvalidationReason,
    ) = Unit

    override suspend fun cachePressure(): ReaderAssetCachePressure = ReaderAssetCachePressure.NORMAL

    override suspend fun reconcile(activeProtections: ReaderAssetActiveProtections) = Unit

    override suspend fun releaseSession(sessionId: ReaderSessionId) = Unit

    override suspend fun clearAutomatic(scope: ReaderAssetClearScope) = Unit
}
