package app.openstory.reader.assets

import app.openstory.common.id.PluginId
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertSame
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class ReaderAssetSingleFlightTest {
    @Test
    fun `visible and speculative consumers share one remote payload`() = runTest {
        val flight = ReaderAssetSingleFlight(backgroundScope)
        val key = readerAssetKey(1)
        val release = CompletableDeferred<Unit>()
        var producerCalls = 0
        val producer: suspend (ContentFetchDemand) -> ReaderAssetRemoteOutcome = {
            producerCalls++
            release.await()
            ReaderAssetRemoteOutcome.Success(readerAssetPayload(1))
        }

        val results = listOf(
            async { flight.acquireRemote(key, ContentFetchPriority.CRITICAL, token(1), producer) { null } },
            async { flight.acquireRemote(key, ContentFetchPriority.INTERACTIVE, token(2), producer) { null } },
            async { flight.acquireRemote(key, ContentFetchPriority.SPECULATIVE, token(3), producer) { null } },
        )
        runCurrent()

        assertEquals(1, producerCalls)
        release.complete(Unit)
        results.forEach { result -> assertIs<ReaderAssetRemoteOutcome.Success>(result.await()) }
    }

    @Test
    fun `critical joiner promotes the same demand without restarting producer`() = runTest {
        val flight = ReaderAssetSingleFlight(backgroundScope)
        val key = readerAssetKey(2)
        val release = CompletableDeferred<Unit>()
        val started = CompletableDeferred<ContentFetchDemand>()
        var producerCalls = 0
        val producer: suspend (ContentFetchDemand) -> ReaderAssetRemoteOutcome = { demand ->
            producerCalls++
            started.complete(demand)
            release.await()
            ReaderAssetRemoteOutcome.Success(readerAssetPayload(2))
        }
        val speculative = async {
            flight.acquireRemote(key, ContentFetchPriority.SPECULATIVE, token(4), producer) { null }
        }
        val demand = started.await()

        val critical = async {
            flight.acquireRemote(key, ContentFetchPriority.CRITICAL, token(5), producer) { null }
        }
        runCurrent()

        assertSame(demand, started.await())
        assertEquals(ContentFetchPriority.CRITICAL, demand.priority)
        assertEquals(1, producerCalls)
        release.complete(Unit)
        speculative.await()
        critical.await()
    }

    @Test
    fun `join and priority promotion emit aggregate diagnostics without restarting producer`() = runTest {
        val diagnostics = RecordingReaderAssetDiagnostics()
        val flight = ReaderAssetSingleFlight(backgroundScope, diagnostics)
        val key = readerAssetKey(22)
        val started = CompletableDeferred<Unit>()
        val release = CompletableDeferred<Unit>()
        val speculative = async {
            flight.acquireRemote(key, ContentFetchPriority.SPECULATIVE, token(220), {
                started.complete(Unit)
                release.await()
                ReaderAssetRemoteOutcome.Success(readerAssetPayload(22))
            }) { null }
        }
        started.await()

        val critical = async {
            flight.acquireRemote(key, ContentFetchPriority.CRITICAL, token(221), {
                error("joined work must not restart producer")
            }) { null }
        }
        runCurrent()

        assertTrue(ReaderAssetDiagnosticEvent.SingleFlightJoin in diagnostics.events)
        assertTrue(
            ReaderAssetDiagnosticEvent.PriorityPromotion(
                ContentFetchPriority.SPECULATIVE,
                ContentFetchPriority.CRITICAL,
            ) in diagnostics.events,
        )
        release.complete(Unit)
        speculative.await()
        critical.await()
    }

    @Test
    fun `different logical and security keys never join`() = runTest {
        val flight = ReaderAssetSingleFlight(backgroundScope)
        val source = ReaderAssetSourceNamespace.fromPluginId(PluginId("source"))
        val publicKey = readerAssetKey(3, sourceNamespace = source)
        val accountKey = readerAssetKey(
            3,
            sourceNamespace = source,
            securityScope = ReaderCacheSecurityScope.AccountScoped("account-a"),
        )
        var producerCalls = 0

        val results = listOf(publicKey, accountKey, readerAssetKey(4)).mapIndexed { index, key ->
            async {
                flight.acquireRemote(key, ContentFetchPriority.INTERACTIVE, token(10 + index), {
                    producerCalls++
                    ReaderAssetRemoteOutcome.Success(readerAssetPayload(10 + index))
                }) { null }
            }
        }

        results.forEach { it.await() }
        assertEquals(3, producerCalls)
    }

    @Test
    fun `security invalidation cancels non-public critical work but preserves public work`() = runTest {
        val flight = ReaderAssetSingleFlight(backgroundScope)
        val source = ReaderAssetSourceNamespace.fromPluginId(PluginId("source"))
        val accountKey = readerAssetKey(
            5,
            sourceNamespace = source,
            securityScope = ReaderCacheSecurityScope.AccountScoped("account-a"),
        )
        val publicKey = readerAssetKey(6, sourceNamespace = source)
        val accountStarted = CompletableDeferred<Unit>()
        val publicStarted = CompletableDeferred<Unit>()
        val publicRelease = CompletableDeferred<Unit>()
        val account = async {
            flight.acquireRemote(accountKey, ContentFetchPriority.CRITICAL, token(20), {
                accountStarted.complete(Unit)
                awaitCancellation()
            }) { null }
        }
        val public = async {
            flight.acquireRemote(publicKey, ContentFetchPriority.CRITICAL, token(21), {
                publicStarted.complete(Unit)
                publicRelease.await()
                ReaderAssetRemoteOutcome.Success(readerAssetPayload(6))
            }) { null }
        }
        accountStarted.await()
        publicStarted.await()

        flight.invalidateSecurityScopedSource(source)
        runCurrent()

        assertEquals(
            ReaderAssetRemoteOutcome.Failure(ReaderAssetFailure.RouteInvalidated),
            account.await(),
        )
        assertTrue(!public.isCompleted)
        publicRelease.complete(Unit)
        assertIs<ReaderAssetRemoteOutcome.Success>(public.await())
    }

    @Test
    fun `producer completion racing security invalidation cannot publish stale payload`() = runTest {
        val flight = ReaderAssetSingleFlight(backgroundScope)
        val source = ReaderAssetSourceNamespace.fromPluginId(PluginId("source"))
        val key = readerAssetKey(
            7,
            sourceNamespace = source,
            securityScope = ReaderCacheSecurityScope.AccountScoped("account-a"),
        )
        val started = CompletableDeferred<Unit>()
        val release = CompletableDeferred<Unit>()
        val result = async {
            flight.acquireRemote(key, ContentFetchPriority.CRITICAL, token(30), {
                started.complete(Unit)
                release.await()
                ReaderAssetRemoteOutcome.Success(readerAssetPayload(7))
            }) { null }
        }
        started.await()

        release.complete(Unit)
        flight.invalidateSecurityScopedSource(source)
        runCurrent()

        assertEquals(
            ReaderAssetRemoteOutcome.Failure(ReaderAssetFailure.RouteInvalidated),
            result.await(),
        )
    }

    @Test
    fun `security invalidation cancels matching background persistence`() = runTest {
        val flight = ReaderAssetSingleFlight(backgroundScope)
        val source = ReaderAssetSourceNamespace.fromPluginId(PluginId("source"))
        val key = readerAssetKey(
            10,
            sourceNamespace = source,
            securityScope = ReaderCacheSecurityScope.AccountScoped("account-a"),
        )
        val persistenceStarted = CompletableDeferred<Unit>()
        val persistenceCancelled = CompletableDeferred<Unit>()

        assertIs<ReaderAssetRemoteOutcome.Success>(
            flight.acquireRemote(
                key,
                ContentFetchPriority.CRITICAL,
                token(31),
                producer = { ReaderAssetRemoteOutcome.Success(readerAssetPayload(10)) },
                afterSuccess = {
                    backgroundScope.launch {
                        persistenceStarted.complete(Unit)
                        try {
                            awaitCancellation()
                        } finally {
                            persistenceCancelled.complete(Unit)
                        }
                    }
                },
            ),
        )
        persistenceStarted.await()

        flight.invalidateSecurityScopedSource(source)
        runCurrent()

        assertTrue(persistenceCancelled.isCompleted)
    }

    @Test
    fun `last speculative consumer cancellation may cancel unfinished producer`() = runTest {
        val flight = ReaderAssetSingleFlight(backgroundScope)
        val producerCancelled = CompletableDeferred<Unit>()
        val waiter = launch {
            flight.acquireRemote(readerAssetKey(8), ContentFetchPriority.SPECULATIVE, token(40), {
                try {
                    awaitCancellation()
                } finally {
                    producerCancelled.complete(Unit)
                }
            }) { null }
        }
        runCurrent()

        waiter.cancelAndJoin()
        runCurrent()

        assertTrue(producerCancelled.isCompleted)
    }

    @Test
    fun `successful entry stays joinable until leader persistence completes`() = runTest {
        val flight = ReaderAssetSingleFlight(backgroundScope)
        val key = readerAssetKey(9)
        val persistenceRelease = CompletableDeferred<Unit>()
        var producerCalls = 0
        val producer: suspend (ContentFetchDemand) -> ReaderAssetRemoteOutcome = {
            producerCalls++
            ReaderAssetRemoteOutcome.Success(readerAssetPayload(9))
        }
        val afterSuccess: (ReaderAssetPayload) -> kotlinx.coroutines.Job? = {
            backgroundScope.launch { persistenceRelease.await() }
        }

        assertIs<ReaderAssetRemoteOutcome.Success>(
            flight.acquireRemote(key, ContentFetchPriority.INTERACTIVE, token(50), producer, afterSuccess),
        )
        assertIs<ReaderAssetRemoteOutcome.Success>(
            flight.acquireRemote(key, ContentFetchPriority.SPECULATIVE, token(51), producer, afterSuccess),
        )
        assertEquals(1, producerCalls)

        persistenceRelease.complete(Unit)
        runCurrent()
        assertIs<ReaderAssetRemoteOutcome.Success>(
            flight.acquireRemote(key, ContentFetchPriority.INTERACTIVE, token(52), producer, afterSuccess),
        )
        assertEquals(2, producerCalls)
    }
}

internal fun readerAssetKey(
    seed: Int,
    sourceNamespace: ReaderAssetSourceNamespace =
        ReaderAssetSourceNamespace.fromPluginId(PluginId("source")),
    securityScope: ReaderCacheSecurityScope = ReaderCacheSecurityScope.Public,
): ReaderPageAssetKey = ReaderPageAssetKey(
    schemaVersion = ReaderAssetKeySchemaVersion(1),
    sourceNamespace = sourceNamespace,
    securityScope = securityScope,
    contentVariant = ReaderContentVariant.ORIGINAL,
    persistenceMode = ReaderAssetPersistenceMode.DURABLE_AUTOMATIC,
    imageSetNamespace = ReaderImageSetNamespace(testHash(seed + 100)),
    runtimeIsolationScope = null,
    pageIdentityHash = ReaderAssetIdentityHash(testHash(seed + 200)),
    hash = ReaderAssetKeyHash(testHash(seed + 300)),
)

internal fun readerAssetPayload(seed: Int): ReaderAssetPayload =
    ReaderAssetPayload.verifiedBounded(byteArrayOf(seed.toByte(), (seed + 1).toByte()), "image/jpeg", null)

internal fun token(value: Int) = ReaderAssetConsumerToken(value.toLong())

internal fun testHash(seed: Int): String = seed.toString(16).padStart(64, '0').takeLast(64)
