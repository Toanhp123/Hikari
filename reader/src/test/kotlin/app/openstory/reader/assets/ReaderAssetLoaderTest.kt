package app.openstory.reader.assets

import app.openstory.common.id.CanonicalChapterId
import app.openstory.common.id.ChapterReleaseId
import app.openstory.common.id.StoryId
import java.io.ByteArrayInputStream
import java.io.InputStream
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertIs
import kotlin.test.assertNotSame
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class ReaderAssetLoaderTest {
    @Test
    fun `local hit returns a fresh lease without entering remote single flight`() = runTest {
        val fixture = fixture()
        fixture.store.openResults += ReaderAssetOpenResult.Available(TestReadLease(byteArrayOf(1)))

        val result = fixture.load(1, ReaderAssetLocalPresence.LOCAL_AVAILABLE)

        assertIs<ReaderAssetLoadOutcome.Local>(result)
        assertEquals(0, fixture.delivery.fetchCalls)
        assertEquals(0, fixture.store.captureCalls)
    }

    @Test
    fun `two local consumers receive independently closable leases`() = runTest {
        val fixture = fixture()
        val firstLease = TestReadLease(byteArrayOf(1))
        val secondLease = TestReadLease(byteArrayOf(1))
        fixture.store.openResults.addAll(
            listOf(ReaderAssetOpenResult.Available(firstLease), ReaderAssetOpenResult.Available(secondLease)),
        )

        val first = assertIs<ReaderAssetLoadOutcome.Local>(
            fixture.load(2, ReaderAssetLocalPresence.LOCAL_AVAILABLE, consumer = token(2)),
        )
        val second = assertIs<ReaderAssetLoadOutcome.Local>(
            fixture.load(2, ReaderAssetLocalPresence.LOCAL_AVAILABLE, consumer = token(3)),
        )

        assertNotSame(first.lease, second.lease)
        first.lease.close()
        assertTrue(firstLease.closed)
        assertTrue(!secondLease.closed)
        second.lease.close()
        assertTrue(secondLease.closed)
    }

    @Test
    fun `unknown visible presence inspects once before opening local`() = runTest {
        val fixture = fixture()
        fixture.store.inspectedPresence = ReaderAssetLocalPresence.LOCAL_AVAILABLE
        fixture.store.openResults += ReaderAssetOpenResult.Available(TestReadLease(byteArrayOf(3)))

        assertIs<ReaderAssetLoadOutcome.Local>(
            fixture.load(3, ReaderAssetLocalPresence.UNKNOWN),
        )

        assertEquals(1, fixture.store.inspectCalls)
        assertEquals(1, fixture.store.openCalls)
        assertEquals(0, fixture.delivery.fetchCalls)
    }

    @Test
    fun `unknown inspection that remains unresolved bypasses cache without looping`() = runTest {
        val fixture = fixture()
        fixture.store.inspectedPresence = ReaderAssetLocalPresence.UNKNOWN
        fixture.delivery.outcomes += ReaderAssetDeliveryResult.Success(readerAssetPayload(30))

        assertIs<ReaderAssetLoadOutcome.Remote>(
            fixture.load(30, ReaderAssetLocalPresence.UNKNOWN),
        )

        assertEquals(1, fixture.store.inspectCalls)
        assertEquals(0, fixture.store.openCalls)
        assertEquals(1, fixture.delivery.fetchCalls)
        assertEquals(0, fixture.store.captureCalls)
        assertEquals(0, fixture.store.commitCalls)
    }

    @Test
    fun `cache unavailable uses one visible bypass and suppresses speculation`() = runTest {
        val fixture = fixture()
        fixture.delivery.outcomes += ReaderAssetDeliveryResult.Success(readerAssetPayload(4))

        assertIs<ReaderAssetLoadOutcome.Remote>(
            fixture.load(4, ReaderAssetLocalPresence.LOCAL_UNAVAILABLE),
        )
        assertEquals(1, fixture.delivery.fetchCalls)
        assertEquals(0, fixture.store.captureCalls)
        assertEquals(0, fixture.store.commitCalls)

        assertEquals(
            ReaderAssetLoadOutcome.Failure(ReaderAssetFailure.CacheStorageUnavailable),
            fixture.load(
                5,
                ReaderAssetLocalPresence.LOCAL_UNAVAILABLE,
                priority = ContentFetchPriority.SPECULATIVE,
            ),
        )
        assertEquals(1, fixture.delivery.fetchCalls)
    }

    @Test
    fun `corrupt local entry is invalidated and repaired by one remote generation`() = runTest {
        val fixture = fixture()
        fixture.store.openResults += ReaderAssetOpenResult.Corrupt
        fixture.delivery.outcomes += ReaderAssetDeliveryResult.Success(readerAssetPayload(6))

        assertIs<ReaderAssetLoadOutcome.Remote>(
            fixture.load(6, ReaderAssetLocalPresence.LOCAL_AVAILABLE),
        )

        assertEquals(1, fixture.store.invalidateCalls)
        assertEquals(1, fixture.delivery.fetchCalls)
    }

    @Test
    fun `leader captures authority before network and visible result does not await commit`() = runTest {
        val events = mutableListOf<String>()
        val commitRelease = CompletableDeferred<Unit>()
        val fixture = fixture(events = events, commitRelease = commitRelease)
        fixture.delivery.outcomes += ReaderAssetDeliveryResult.Success(readerAssetPayload(7))

        val result = async { fixture.load(7, ReaderAssetLocalPresence.LOCAL_MISSING) }
        runCurrent()

        assertTrue(result.isCompleted)
        assertIs<ReaderAssetLoadOutcome.Remote>(result.await())
        assertEquals(listOf("capture", "fetch", "commit-start"), events)
        assertEquals(1, fixture.store.commitCalls)
        commitRelease.complete(Unit)
        runCurrent()
    }

    @Test
    fun `quota revocation during network bypasses persistence without losing remote payload`() = runTest {
        val networkRelease = CompletableDeferred<Unit>()
        val fixture = fixture(networkRelease = networkRelease)
        fixture.store.commitResult = ReaderAssetCommitResult.Bypassed
        fixture.delivery.outcomes += ReaderAssetDeliveryResult.Success(readerAssetPayload(8))
        val first = async { fixture.load(8, ReaderAssetLocalPresence.LOCAL_MISSING, consumer = token(80)) }
        val second = async { fixture.load(8, ReaderAssetLocalPresence.LOCAL_MISSING, consumer = token(81)) }
        runCurrent()

        networkRelease.complete(Unit)

        assertIs<ReaderAssetLoadOutcome.Remote>(first.await())
        assertIs<ReaderAssetLoadOutcome.Remote>(second.await())
        runCurrent()
        assertEquals(1, fixture.delivery.fetchCalls)
        assertEquals(1, fixture.store.commitCalls)
    }

    @Test
    fun `retryable transport retries once after scheduler delay`() = runTest {
        val fixture = fixture()
        fixture.delivery.outcomes.addAll(
            listOf(
                ReaderAssetDeliveryResult.Failure(ReaderAssetFailure.TransportUnavailable(retryable = true)),
                ReaderAssetDeliveryResult.Success(readerAssetPayload(9)),
            ),
        )
        val result = async { fixture.load(9, ReaderAssetLocalPresence.LOCAL_MISSING) }
        runCurrent()
        assertEquals(1, fixture.delivery.fetchCalls)

        advanceTimeBy(249L)
        runCurrent()
        assertEquals(1, fixture.delivery.fetchCalls)
        advanceTimeBy(1L)
        runCurrent()

        assertEquals(2, fixture.delivery.fetchCalls)
        assertIs<ReaderAssetLoadOutcome.Remote>(result.await())
    }

    @Test
    fun `retryable transport stops after one retry and preserves final failure`() = runTest {
        val fixture = fixture()
        val finalFailure = ReaderAssetFailure.TransportUnavailable(retryable = true)
        fixture.delivery.outcomes.addAll(
            listOf(
                ReaderAssetDeliveryResult.Failure(finalFailure),
                ReaderAssetDeliveryResult.Failure(finalFailure),
            ),
        )
        val result = async { fixture.load(10, ReaderAssetLocalPresence.LOCAL_MISSING) }
        runCurrent()

        advanceTimeBy(ReaderAssetRuntimePolicy.TRANSIENT_ASSET_RETRY_DELAY_MILLIS)
        runCurrent()

        assertEquals(2, fixture.delivery.fetchCalls)
        assertEquals(ReaderAssetLoadOutcome.Failure(finalFailure), result.await())
    }

    @Test
    fun `cancelling speculative consumer aborts transient retry delay`() = runTest {
        val fixture = fixture()
        fixture.delivery.outcomes += ReaderAssetDeliveryResult.Failure(
            ReaderAssetFailure.TransportUnavailable(retryable = true),
        )
        val result = launch {
            fixture.load(
                11,
                ReaderAssetLocalPresence.LOCAL_MISSING,
                priority = ContentFetchPriority.SPECULATIVE,
            )
        }
        runCurrent()
        assertEquals(1, fixture.delivery.fetchCalls)

        result.cancelAndJoin()
        advanceTimeBy(ReaderAssetRuntimePolicy.TRANSIENT_ASSET_RETRY_DELAY_MILLIS)
        runCurrent()

        assertEquals(1, fixture.delivery.fetchCalls)
    }

    @Test
    fun `terminal delivery and control failures are preserved without retry`() = runTest {
        val failures = listOf(
            ReaderAssetFailure.DeliveryRejected(403),
            ReaderAssetFailure.Unauthorized,
            ReaderAssetFailure.AssetTooLarge,
            ReaderAssetFailure.InvalidPayload,
            ReaderAssetFailure.Preempted,
            ReaderAssetFailure.Superseded,
        )
        failures.forEachIndexed { index, failure ->
            val fixture = fixture()
            fixture.delivery.outcomes += ReaderAssetDeliveryResult.Failure(failure)

            assertEquals(
                ReaderAssetLoadOutcome.Failure(failure),
                fixture.load(20 + index, ReaderAssetLocalPresence.LOCAL_MISSING),
            )
            assertEquals(1, fixture.delivery.fetchCalls)
        }
    }


    @Test
    fun `local hit records disk diagnostic without network fetch`() = runTest {
        val diagnostics = RecordingReaderAssetDiagnostics()
        val fixture = fixture(diagnostics = diagnostics)
        fixture.store.openResults += ReaderAssetOpenResult.Available(TestReadLease(byteArrayOf(1)))

        assertIs<ReaderAssetLoadOutcome.Local>(
            fixture.load(31, ReaderAssetLocalPresence.LOCAL_AVAILABLE),
        )

        assertEquals(listOf<ReaderAssetDiagnosticEvent>(ReaderAssetDiagnosticEvent.DiskHit), diagnostics.events)
    }

    @Test
    fun `corrupt local fallback records corruption and one network fetch`() = runTest {
        val diagnostics = RecordingReaderAssetDiagnostics()
        val fixture = fixture(diagnostics = diagnostics)
        fixture.store.openResults += ReaderAssetOpenResult.Corrupt
        fixture.delivery.outcomes += ReaderAssetDeliveryResult.Success(readerAssetPayload(32))

        assertIs<ReaderAssetLoadOutcome.Remote>(
            fixture.load(32, ReaderAssetLocalPresence.LOCAL_AVAILABLE),
        )

        assertEquals(
            listOf(
                ReaderAssetDiagnosticEvent.Corruption,
                ReaderAssetDiagnosticEvent.NetworkFetch,
            ),
            diagnostics.events,
        )
    }

    @Test
    fun `payload bound accepts exactly sixteen mebibytes and rejects one byte more`() {
        val exact = ByteArray(ReaderAssetRuntimePolicy.MAX_READER_ASSET_BYTES)
        assertEquals(
            ReaderAssetRuntimePolicy.MAX_READER_ASSET_BYTES,
            ReaderAssetPayload.verifiedBounded(exact, "image/jpeg", null).sizeBytes,
        )
        assertFailsWith<IllegalArgumentException> {
            ReaderAssetPayload.verifiedBounded(
                ByteArray(ReaderAssetRuntimePolicy.MAX_READER_ASSET_BYTES + 1),
                "image/jpeg",
                null,
            )
        }
    }

    private fun kotlinx.coroutines.test.TestScope.fixture(
        events: MutableList<String> = mutableListOf(),
        networkRelease: CompletableDeferred<Unit>? = null,
        commitRelease: CompletableDeferred<Unit>? = null,
        diagnostics: ReaderAssetDiagnosticsSink = ReaderAssetDiagnosticsSink.NO_OP,
    ): LoaderFixture {
        val store = FakeReaderAssetStore(events, commitRelease)
        val delivery = FakeReaderAssetDelivery(events, networkRelease)
        val loader = ReaderAssetLoader(
            store = store,
            delivery = delivery,
            singleFlight = ReaderAssetSingleFlight(backgroundScope),
            fetchArbiter = ContentFetchArbiter(),
            persistenceScope = backgroundScope,
            diagnostics = diagnostics,
        )
        return LoaderFixture(loader, store, delivery)
    }
}

private class LoaderFixture(
    private val loader: ReaderAssetLoader,
    val store: FakeReaderAssetStore,
    val delivery: FakeReaderAssetDelivery,
) {
    suspend fun load(
        seed: Int,
        presence: ReaderAssetLocalPresence,
        priority: ContentFetchPriority = ContentFetchPriority.CRITICAL,
        consumer: ReaderAssetConsumerToken = token(seed + 100),
    ): ReaderAssetLoadOutcome {
        val key = readerAssetKey(seed)
        val descriptor = ReaderPageAssetDescriptor(
            key = key,
            uiBlockId = "image-$seed",
            stableAssetId = "asset-$seed",
            imageOrdinal = seed,
            deliveryLocator = "https://cdn.example/$seed.jpg",
            locatorFingerprint = ReaderAssetIdentity.locatorFingerprint("https://cdn.example/$seed.jpg"),
        )
        return loader.load(
            facts = ReaderAssetCommitFacts(
                key = key,
                storyId = StoryId("story"),
                canonicalChapterId = CanonicalChapterId("chapter"),
                releaseId = ChapterReleaseId("release"),
                sourceNamespace = key.sourceNamespace,
                securityScope = key.securityScope,
                contentVariant = key.contentVariant,
                identityMode = ReaderAssetIdentityMode.TRUSTED_STABLE,
                persistenceMode = key.persistenceMode,
                imageSetNamespace = key.imageSetNamespace,
                imageOrdinal = descriptor.imageOrdinal,
            ),
            descriptor = descriptor,
            localPresence = presence,
            priority = priority,
            consumer = consumer,
        )
    }
}

private class FakeReaderAssetStore(
    private val events: MutableList<String>,
    private val commitRelease: CompletableDeferred<Unit>?,
) : ReaderAssetStorePort {
    val openResults = ArrayDeque<ReaderAssetOpenResult>()
    var inspectedPresence = ReaderAssetLocalPresence.LOCAL_MISSING
    var commitResult: ReaderAssetCommitResult = ReaderAssetCommitResult.Persisted
    var inspectCalls = 0
    var openCalls = 0
    var captureCalls = 0
    var commitCalls = 0
    var invalidateCalls = 0

    override suspend fun inspect(keys: Set<ReaderPageAssetKey>): Map<ReaderPageAssetKey, ReaderAssetLocalPresence> {
        inspectCalls++
        return keys.associateWith { inspectedPresence }
    }

    override suspend fun openLocal(key: ReaderPageAssetKey): ReaderAssetOpenResult {
        openCalls++
        return openResults.removeFirstOrNull() ?: ReaderAssetOpenResult.Missing
    }

    override suspend fun captureDurableWriteAuthority(
        facts: ReaderAssetCommitFacts,
    ): ReaderAssetDurableWriteAuthority {
        captureCalls++
        events += "capture"
        return TestDurableAuthority
    }

    override suspend fun commit(
        facts: ReaderAssetCommitFacts,
        authority: ReaderAssetDurableWriteAuthority,
        payload: ReaderAssetPayload,
    ): ReaderAssetCommitResult {
        commitCalls++
        events += "commit-start"
        commitRelease?.await()
        return commitResult
    }

    override suspend fun invalidate(key: ReaderPageAssetKey, reason: ReaderAssetInvalidationReason) {
        invalidateCalls++
    }

    override suspend fun markConsumed(key: ReaderPageAssetKey) = Unit
    override suspend fun cachePressure() = ReaderAssetCachePressure.NORMAL
    override suspend fun reconcile(activeProtections: ReaderAssetActiveProtections) = Unit
    override suspend fun releaseSession(sessionId: app.openstory.reader.routing.ReaderSessionId) = Unit
    override suspend fun clearAutomatic(scope: ReaderAssetClearScope) = Unit
}

private object TestDurableAuthority : ReaderAssetDurableWriteAuthority

private class FakeReaderAssetDelivery(
    private val events: MutableList<String>,
    private val networkRelease: CompletableDeferred<Unit>?,
) : ReaderAssetDeliveryPort {
    val outcomes = ArrayDeque<ReaderAssetDeliveryResult>()
    var fetchCalls = 0

    override suspend fun fetch(request: ReaderAssetDeliveryRequest): ReaderAssetDeliveryResult {
        fetchCalls++
        events += "fetch"
        networkRelease?.await()
        return outcomes.removeFirst()
    }
}

private class TestReadLease(
    private val bytes: ByteArray,
) : ReaderAssetReadLease {
    var closed = false
        private set

    override val sizeBytes: Long = bytes.size.toLong()

    override fun openStream(): InputStream = ByteArrayInputStream(bytes)

    override fun close() {
        closed = true
    }
}
