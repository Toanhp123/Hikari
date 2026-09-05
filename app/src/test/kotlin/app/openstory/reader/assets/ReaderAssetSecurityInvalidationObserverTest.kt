package app.openstory.reader.assets

import app.openstory.common.id.PluginId
import app.openstory.plugins.runtime.auth.PluginSessionRecord
import app.openstory.plugins.runtime.auth.PluginSessionService
import app.openstory.plugins.runtime.auth.PluginSessionStatus
import app.openstory.plugins.runtime.auth.PluginSessionSummary
import app.openstory.plugins.runtime.capabilities.http.ManagedCredentialRequest
import app.openstory.reader.routing.ReaderNetworkFactsPort
import app.openstory.reader.routing.ReaderNetworkState
import app.openstory.reader.routing.ReaderSessionId
import java.io.InputStream
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest

@OptIn(ExperimentalCoroutinesApi::class)
class ReaderAssetSecurityInvalidationObserverTest {
    @Test
    fun `same status replacement login invalidates because credential generation changes`() = runTest {
        val sessions = MutablePluginSessionService()
        val store = RecordingClearStore()
        val coordinator = ReaderAssetCoordinator(
            store = store,
            networkFacts = ReaderNetworkFactsPort { ReaderNetworkState.UNKNOWN },
            coordinatorScope = backgroundScope,
        )
        val observer = ReaderAssetSecurityInvalidationObserver(sessions, store, coordinator)
        val pluginId = PluginId("org.example.reader")
        val source = ReaderAssetSourceNamespace.fromPluginId(pluginId)
        observer.start(backgroundScope)
        runCurrent()

        sessions.publish(summary(pluginId, PluginSessionStatus.AUTHENTICATED, generation = 1))
        runCurrent()
        assertEquals(emptyList(), store.clears)

        sessions.publish(summary(pluginId, PluginSessionStatus.AUTHENTICATED, generation = 2))
        runCurrent()

        assertEquals(
            listOf<ReaderAssetClearScope>(ReaderAssetClearScope.AllAccountScopesForSource(source)),
            store.clears,
        )
    }

    @Test
    fun `transition into logged out invalidates even when generation did not change`() = runTest {
        val sessions = MutablePluginSessionService()
        val store = RecordingClearStore()
        val coordinator = ReaderAssetCoordinator(
            store = store,
            networkFacts = ReaderNetworkFactsPort { ReaderNetworkState.UNKNOWN },
            coordinatorScope = backgroundScope,
        )
        val observer = ReaderAssetSecurityInvalidationObserver(sessions, store, coordinator)
        val pluginId = PluginId("org.example.reader")
        observer.start(backgroundScope)
        runCurrent()

        sessions.publish(summary(pluginId, PluginSessionStatus.AUTHENTICATED, generation = 7))
        runCurrent()
        sessions.publish(summary(pluginId, PluginSessionStatus.LOGGED_OUT, generation = 7))
        runCurrent()

        assertEquals(
            listOf<ReaderAssetClearScope>(
                ReaderAssetClearScope.AllAccountScopesForSource(
                    ReaderAssetSourceNamespace.fromPluginId(pluginId),
                ),
            ),
            store.clears,
        )
    }

    @Test
    fun `first installed-session emission is baseline and does not clear cache`() = runTest {
        val sessions = MutablePluginSessionService()
        val store = RecordingClearStore()
        val coordinator = ReaderAssetCoordinator(
            store = store,
            networkFacts = ReaderNetworkFactsPort { ReaderNetworkState.UNKNOWN },
            coordinatorScope = backgroundScope,
        )
        val observer = ReaderAssetSecurityInvalidationObserver(sessions, store, coordinator)
        observer.start(backgroundScope)
        runCurrent()

        sessions.publish(summary(PluginId("org.example.reader"), PluginSessionStatus.LOGGED_OUT, generation = 4))
        runCurrent()

        assertEquals(emptyList(), store.clears)
    }

    private fun summary(
        pluginId: PluginId,
        status: PluginSessionStatus,
        generation: Long,
    ) = PluginSessionSummary(
        pluginId = pluginId,
        status = status,
        expiresAtEpochMillis = if (status == PluginSessionStatus.AUTHENTICATED) 9_999 else null,
        credentialGeneration = generation,
    )
}

private class MutablePluginSessionService : PluginSessionService {
    private val summaries = MutableStateFlow<List<PluginSessionSummary>>(emptyList())

    fun publish(vararg values: PluginSessionSummary) {
        summaries.value = values.toList()
    }

    override fun observeInstalledSessions(): Flow<List<PluginSessionSummary>> = summaries

    override suspend fun completeVerifiedLogin(
        pluginId: PluginId,
        authenticationPolicyFingerprint: String,
        records: List<PluginSessionRecord>,
    ): PluginSessionSummary = error("not used")

    override suspend fun logout(pluginId: PluginId) = error("not used")
    override suspend fun sessionFor(request: ManagedCredentialRequest): List<PluginSessionRecord> = error("not used")
    override suspend fun summary(pluginId: PluginId): PluginSessionSummary = error("not used")
    override suspend fun invalidateChangedPolicies() = error("not used")
}

private class RecordingClearStore : ReaderAssetStorePort {
    val clears = mutableListOf<ReaderAssetClearScope>()

    override suspend fun inspect(
        keys: Set<ReaderPageAssetKey>,
    ) = emptyMap<ReaderPageAssetKey, ReaderAssetLocalPresence>()
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
    override suspend fun invalidate(key: ReaderPageAssetKey, reason: ReaderAssetInvalidationReason) = Unit
    override suspend fun cachePressure(): ReaderAssetCachePressure = ReaderAssetCachePressure.NORMAL
    override suspend fun reconcile(activeProtections: ReaderAssetActiveProtections) = Unit
    override suspend fun releaseSession(sessionId: ReaderSessionId) = Unit
    override suspend fun clearAutomatic(scope: ReaderAssetClearScope) {
        clears += scope
    }
}
