package app.openstory.downloads.assets

import app.openstory.common.FakeClock
import app.openstory.common.FakeMonotonicClock
import app.openstory.common.id.CanonicalChapterId
import app.openstory.common.id.ChapterReleaseId
import app.openstory.common.id.PluginId
import app.openstory.common.id.StoryId
import app.openstory.downloads.blob.BlobChecksum
import app.openstory.downloads.blob.ChapterBlob
import app.openstory.downloads.blob.ChapterBlobKey
import app.openstory.downloads.blob.ChapterBlobStore
import app.openstory.downloads.cache.AutomaticCacheBudgetCoordinator
import app.openstory.downloads.cache.AutomaticCacheRuntimePolicy
import app.openstory.downloads.cache.CacheEntry
import app.openstory.downloads.cache.CacheRepository
import app.openstory.downloads.reconcile.StorageWriteAdmission
import app.openstory.reader.assets.ReaderAssetCachePressure
import app.openstory.reader.assets.ReaderAssetClearScope
import app.openstory.reader.assets.ReaderAssetCommitFacts
import app.openstory.reader.assets.ReaderAssetCommitResult
import app.openstory.reader.assets.ReaderAssetDiagnosticEvent
import app.openstory.reader.assets.ReaderAssetDiagnosticsSink
import app.openstory.reader.assets.ReaderAssetActiveProtections
import app.openstory.reader.assets.ReaderAssetFailure
import app.openstory.reader.assets.ReaderAssetIdentityHash
import app.openstory.reader.assets.ReaderAssetIdentityMode
import app.openstory.reader.assets.ReaderAssetInvalidationReason
import app.openstory.reader.assets.ReaderAssetKeyHash
import app.openstory.reader.assets.ReaderAssetKeySchemaVersion
import app.openstory.reader.assets.ReaderAssetLocalPresence
import app.openstory.reader.assets.ReaderAssetOpenResult
import app.openstory.reader.assets.ReaderAssetPayload
import app.openstory.reader.assets.ReaderAssetPersistenceMode
import app.openstory.reader.assets.ReaderAssetProtectionClass
import app.openstory.reader.assets.ReaderAssetSourceNamespace
import app.openstory.reader.assets.ReaderCacheSecurityScope
import app.openstory.reader.assets.ReaderContentVariant
import app.openstory.reader.assets.ReaderImageSetNamespace
import app.openstory.reader.assets.ReaderPageAssetKey
import app.openstory.reader.assets.ReaderRuntimeAssetScopeId
import java.io.ByteArrayInputStream
import java.io.InputStream
import java.util.UUID
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class DownloadReaderAssetStoreTest {
    @Test
    fun `inspect reports every key and repairs metadata without blob`() = runTest {
        val fixture = fixture()
        val available = facts(1)
        val orphan = facts(2)
        val missing = facts(3)
        fixture.seed(available, bytes(1))
        fixture.seed(orphan, bytes(2), includeBlob = false)

        val result = fixture.store.inspect(setOf(available.key, orphan.key, missing.key))

        assertEquals(ReaderAssetLocalPresence.LOCAL_AVAILABLE, result[available.key])
        assertEquals(ReaderAssetLocalPresence.LOCAL_MISSING, result[orphan.key])
        assertEquals(ReaderAssetLocalPresence.LOCAL_MISSING, result[missing.key])
        assertEquals(3, result.size)
        assertNull(fixture.metadata.find(setOf(orphan.key.hash))[orphan.key.hash])
    }

    @Test
    fun `inspect maps metadata and filesystem failures to unavailable`() = runTest {
        val metadataFailure = fixture()
        val key = facts(4).key
        metadataFailure.metadata.failFind = true
        assertEquals(
            ReaderAssetLocalPresence.LOCAL_UNAVAILABLE,
            metadataFailure.store.inspect(setOf(key))[key],
        )

        val fileFailure = fixture()
        val facts = facts(5)
        fileFailure.seed(facts, bytes(5))
        fileFailure.blobs.failExists += fileFailure.metadata.single(facts.key.hash).blobId
        assertEquals(
            ReaderAssetLocalPresence.LOCAL_UNAVAILABLE,
            fileFailure.store.inspect(setOf(facts.key))[facts.key],
        )
    }

    @Test
    fun `available open keeps physical lease until reader closes`() = runTest {
        val fixture = fixture()
        val facts = facts(6)
        val payload = bytes(6)
        fixture.seed(facts, payload)
        val blobId = ReaderAssetBlobId(fixture.metadata.single(facts.key.hash).blobId)

        val result = assertIs<ReaderAssetOpenResult.Available>(fixture.store.openLocal(facts.key))

        assertEquals(1, fixture.blobs.activeLeases(blobId))
        assertContentEquals(payload, result.lease.openStream().use(InputStream::readBytes))
        result.lease.close()
        assertEquals(0, fixture.blobs.activeLeases(blobId))
    }

    @Test
    fun `checksum mismatch returns corrupt and removes only that generation`() = runTest {
        val fixture = fixture()
        val facts = facts(7)
        fixture.seed(facts, bytes(7), checksum = BlobChecksum.sha256(bytes(70)))
        val blobId = ReaderAssetBlobId(fixture.metadata.single(facts.key.hash).blobId)

        assertIs<ReaderAssetOpenResult.Corrupt>(fixture.store.openLocal(facts.key))
        runCurrent()

        assertNull(fixture.metadata.find(setOf(facts.key.hash))[facts.key.hash])
        assertTrue(blobId in fixture.blobs.normalDeletes)
    }

    @Test
    fun `durable authority fails closed and shared quota revokes pending publication`() = runTest {
        val fixture = fixture()
        val transient = facts(
            id = 8,
            persistenceMode = ReaderAssetPersistenceMode.TRANSIENT_ONLY,
            identityMode = ReaderAssetIdentityMode.TRUSTED_STABLE,
        )
        val unsafeIdentity = facts(9, identityMode = ReaderAssetIdentityMode.NON_PERSISTENT)
        assertNull(fixture.store.captureDurableWriteAuthority(transient))
        assertNull(fixture.store.captureDurableWriteAuthority(unsafeIdentity))

        val durable = facts(10)
        val authority = assertNotNull(fixture.store.captureDurableWriteAuthority(durable))
        fixture.budget.updateQuota(0L)

        assertIs<ReaderAssetCommitResult.Bypassed>(
            fixture.store.commit(durable, authority, payload(10)),
        )
        assertTrue(fixture.metadata.all().isEmpty())
        assertTrue(fixture.blobs.writeCalls.isEmpty())
    }

    @Test
    fun `account clear revokes captured authority without clearing public entries`() = runTest {
        val fixture = fixture()
        val publicFacts = facts(11)
        fixture.persist(publicFacts, payload(11))
        val accountFacts = facts(
            id = 12,
            securityScope = ReaderCacheSecurityScope.AccountScoped("account-a"),
        )
        fixture.persist(accountFacts, payload(12))
        assertEquals(
            "fc164f8250803ea8d41834f1de85821035d27d3747e83610789e0f8e5313b9c3",
            fixture.metadata.single(accountFacts.key.hash).securityScopeHash,
        )
        val authority = assertNotNull(fixture.store.captureDurableWriteAuthority(accountFacts))

        fixture.store.clearAutomatic(
            ReaderAssetClearScope.Account(accountFacts.sourceNamespace, "account-a"),
        )

        assertIs<ReaderAssetCommitResult.Bypassed>(
            fixture.store.commit(accountFacts, authority, payload(12)),
        )
        assertNotNull(fixture.metadata.find(setOf(publicFacts.key.hash))[publicFacts.key.hash])
        assertNull(fixture.metadata.find(setOf(accountFacts.key.hash))[accountFacts.key.hash])
    }

    @Test
    fun `metadata publication failure cleans the written generation`() = runTest {
        val fixture = fixture()
        val facts = facts(13)
        val authority = assertNotNull(fixture.store.captureDurableWriteAuthority(facts))
        fixture.metadata.failUpsert = true

        val result = fixture.store.commit(facts, authority, payload(13))
        runCurrent()

        assertEquals(
            ReaderAssetCommitResult.Degraded(ReaderAssetFailure.CacheStorageUnavailable),
            result,
        )
        assertEquals(1, fixture.blobs.normalDeletes.size)
        assertEquals(0L, fixture.budget.snapshot().pendingReservationBytes)
    }

    @Test
    fun `generation remains reconciliation protected while blob write is in flight`() = runTest {
        val fixture = fixture()
        val facts = facts(id = 88)
        val payload = payload(88)
        val authority = assertNotNull(fixture.store.captureDurableWriteAuthority(facts))
        val writeStarted = CompletableDeferred<ReaderAssetBlobId>()
        val releaseWrite = CompletableDeferred<Unit>()
        fixture.blobs.afterStoredWrite = { id ->
            writeStarted.complete(id)
            releaseWrite.await()
        }

        val commit = async { fixture.store.commit(facts, authority, payload) }
        val activeBlob = writeStarted.await()

        assertEquals(setOf(activeBlob), fixture.store.activeGenerationBlobIds())

        releaseWrite.complete(Unit)
        assertEquals(ReaderAssetCommitResult.Persisted, commit.await())
        assertTrue(fixture.store.activeGenerationBlobIds().isEmpty())
    }

    @Test
    fun `metadata publication failure records one commit failure diagnostic`() = runTest {
        val diagnostics = RecordingDownloadReaderAssetDiagnostics()
        val fixture = fixture(diagnostics = diagnostics)
        val facts = facts(130)
        val authority = assertNotNull(fixture.store.captureDurableWriteAuthority(facts))
        fixture.metadata.failUpsert = true

        val result = fixture.store.commit(facts, authority, payload(130))
        runCurrent()

        assertEquals(
            ReaderAssetCommitResult.Degraded(ReaderAssetFailure.CacheStorageUnavailable),
            result,
        )
        assertEquals(
            listOf(ReaderAssetDiagnosticEvent.CommitFailure(ReaderAssetFailure.CacheStorageUnavailable)),
            diagnostics.events.filterIsInstance<ReaderAssetDiagnosticEvent.CommitFailure>(),
        )
    }

    @Test
    fun `no space retries exactly once after bounded relief`() = runTest {
        val fixture = fixture()
        val victim = facts(14)
        fixture.seed(victim, bytes(14))
        val incoming = facts(15)
        fixture.blobs.writeOutcomes.addAll(listOf(WriteOutcome.NO_SPACE, WriteOutcome.STORED))

        val result = fixture.persist(incoming, payload(15))

        assertIs<ReaderAssetCommitResult.Persisted>(result)
        assertEquals(2, fixture.blobs.writeCalls.size)
        assertNull(fixture.metadata.find(setOf(victim.key.hash))[victim.key.hash])
        assertNotNull(fixture.metadata.find(setOf(incoming.key.hash))[incoming.key.hash])
    }

    @Test
    fun `second no space degrades without third write and releases reservation`() = runTest {
        val fixture = fixture()
        val facts = facts(16)
        val remotePayload = payload(16)
        fixture.blobs.writeOutcomes.addAll(listOf(WriteOutcome.NO_SPACE, WriteOutcome.NO_SPACE))
        val authority = assertNotNull(fixture.store.captureDurableWriteAuthority(facts))

        val result = fixture.store.commit(facts, authority, remotePayload)

        assertEquals(
            ReaderAssetCommitResult.Degraded(ReaderAssetFailure.CacheStorageUnavailable),
            result,
        )
        assertEquals(2, fixture.blobs.writeCalls.size)
        assertEquals(0L, fixture.budget.snapshot().pendingReservationBytes)
        assertContentEquals(bytes(16), remotePayload.bytes())
    }

    @Test
    fun `typed enospc records emergency commit failure and only physical reclaimed bytes`() = runTest {
        val diagnostics = RecordingDownloadReaderAssetDiagnostics()
        val fixture = fixture(diagnostics = diagnostics)
        val victim = facts(160)
        fixture.seed(victim, bytes(160, size = 4), accessedAt = 0L)
        fixture.blobs.writeOutcomes.addAll(listOf(WriteOutcome.NO_SPACE, WriteOutcome.NO_SPACE))

        val result = fixture.persist(facts(161), payload(161))

        assertEquals(
            ReaderAssetCommitResult.Degraded(ReaderAssetFailure.CacheStorageUnavailable),
            result,
        )
        assertTrue(ReaderAssetDiagnosticEvent.CachePressure(ReaderAssetCachePressure.EMERGENCY) in diagnostics.events)
        assertTrue(
            ReaderAssetDiagnosticEvent.CommitFailure(ReaderAssetFailure.CacheStorageUnavailable) in diagnostics.events,
        )
        assertEquals(
            4L,
            diagnostics.events.filterIsInstance<ReaderAssetDiagnosticEvent.EvictionBytes>()
                .sumOf { it.physicallyReclaimedBytes },
        )
    }

    @Test
    fun `leased relief victim does not block deletion of another victim`() = runTest {
        val fixture = fixture()
        val leasedVictim = facts(17)
        val nextVictim = facts(18)
        fixture.seed(leasedVictim, bytes(17), accessedAt = 0L)
        fixture.seed(nextVictim, bytes(18), accessedAt = 1L)
        val leasedBlob = ReaderAssetBlobId(fixture.metadata.single(leasedVictim.key.hash).blobId)
        val lease = assertNotNull(fixture.blobs.open(leasedBlob))
        fixture.blobs.writeOutcomes.addAll(listOf(WriteOutcome.NO_SPACE, WriteOutcome.STORED))

        val result = fixture.persist(facts(19), payload(19))

        assertIs<ReaderAssetCommitResult.Persisted>(result)
        assertTrue(leasedBlob in fixture.blobs.immediateDeletes)
        assertTrue(fixture.blobs.immediateDeletes.size >= 2)
        assertEquals(1, fixture.blobs.activeLeases(leasedBlob))
        lease.close()
    }

    @Test
    fun `unavailable write degrades without eviction retry`() = runTest {
        val fixture = fixture()
        fixture.blobs.writeOutcomes += WriteOutcome.UNAVAILABLE
        val facts = facts(20)

        val result = fixture.persist(facts, payload(20))

        assertEquals(
            ReaderAssetCommitResult.Degraded(ReaderAssetFailure.CacheStorageUnavailable),
            result,
        )
        assertEquals(1, fixture.blobs.writeCalls.size)
        assertTrue(fixture.blobs.immediateDeletes.isEmpty())
    }

    @Test
    fun `reconcile publishes latest protections before physical relief`() = runTest {
        val fixture = fixture()
        val protected = facts(24)
        val victim = facts(25)
        fixture.seed(protected, bytes(24), accessedAt = 0L)
        fixture.seed(victim, bytes(25), accessedAt = 1L)
        fixture.store.reconcile(
            ReaderAssetActiveProtections(
                mapOf(protected.key.hash to ReaderAssetProtectionClass.ACTIVE_INTERACTIVE),
            ),
        )
        fixture.blobs.writeOutcomes.addAll(listOf(WriteOutcome.NO_SPACE, WriteOutcome.STORED))

        assertIs<ReaderAssetCommitResult.Persisted>(fixture.persist(facts(26), payload(26)))

        assertNotNull(fixture.metadata.find(setOf(protected.key.hash))[protected.key.hash])
        assertNull(fixture.metadata.find(setOf(victim.key.hash))[victim.key.hash])
    }

    @Test
    fun `same-key replacement accounts only the current generation`() = runTest {
        val fixture = fixture(quotaBytes = 8L)
        val facts = facts(27)
        fixture.persist(facts, payload(27))

        assertIs<ReaderAssetCommitResult.Persisted>(fixture.persist(facts, payload(27)))
        runCurrent()

        assertEquals(4L, fixture.budget.snapshot().committedBytes)
        assertEquals(1, fixture.blobs.normalDeletes.size)
    }

    @Test
    fun `wall timestamps and monotonic access throttle remain independent`() = runTest {
        val fixture = fixture(wallMillis = 1_000L, monotonicNanos = 0L)
        val facts = facts(21)
        fixture.persist(facts, payload(21))
        var metadata = fixture.metadata.single(facts.key.hash)
        assertEquals(1_000L, metadata.createdAtEpochMillis)
        assertEquals(1_000L, metadata.lastAccessedAtEpochMillis)

        fixture.clock.advanceBy(1_000L)
        assertIs<ReaderAssetOpenResult.Available>(fixture.store.openLocal(facts.key)).lease.close()
        metadata = fixture.metadata.single(facts.key.hash)
        assertEquals(2_000L, metadata.lastAccessedAtEpochMillis)

        fixture.clock.advanceBy(-1_500L)
        assertIs<ReaderAssetOpenResult.Available>(fixture.store.openLocal(facts.key)).lease.close()
        assertEquals(2_000L, fixture.metadata.single(facts.key.hash).lastAccessedAtEpochMillis)

        fixture.monotonicClock.advanceByNanos(300_000L * 1_000_000L)
        fixture.clock.advanceBy(2_500L)
        assertIs<ReaderAssetOpenResult.Available>(fixture.store.openLocal(facts.key)).lease.close()
        assertEquals(3_000L, fixture.metadata.single(facts.key.hash).lastAccessedAtEpochMillis)

        fixture.clock.advanceBy(1_000L)
        fixture.store.markConsumed(facts.key)
        assertEquals(4_000L, fixture.metadata.single(facts.key.hash).lastConsumedAtEpochMillis)

        val reconstructed = fixture.reconstructStore()
        fixture.clock.advanceBy(1_000L)
        assertIs<ReaderAssetOpenResult.Available>(reconstructed.openLocal(facts.key)).lease.close()
        assertEquals(5_000L, fixture.metadata.single(facts.key.hash).lastAccessedAtEpochMillis)
    }

    @Test
    fun `cache pressure combines unified quota and physical reserve`() = runTest {
        val pressured = fixture(quotaBytes = 10L)
        pressured.seed(facts(22), bytes(22, size = 10))
        assertEquals(ReaderAssetCachePressure.PRESSURED, pressured.store.cachePressure())

        val emergency = fixture(writeAdmission = StorageWriteAdmission { false })
        assertEquals(ReaderAssetCachePressure.EMERGENCY, emergency.store.cachePressure())

        val normal = fixture()
        assertEquals(ReaderAssetCachePressure.NORMAL, normal.store.cachePressure())
    }

    @Test
    fun `explicit invalidation removes metadata and generation`() = runTest {
        val fixture = fixture()
        val facts = facts(23)
        fixture.seed(facts, bytes(23))
        val blobId = ReaderAssetBlobId(fixture.metadata.single(facts.key.hash).blobId)

        fixture.store.invalidate(facts.key, ReaderAssetInvalidationReason.CORRUPT)
        runCurrent()

        assertNull(fixture.metadata.find(setOf(facts.key.hash))[facts.key.hash])
        assertTrue(blobId in fixture.blobs.normalDeletes)
    }

    private fun kotlinx.coroutines.test.TestScope.fixture(
        quotaBytes: Long = 10_000L,
        wallMillis: Long = 100L,
        monotonicNanos: Long = 0L,
        writeAdmission: StorageWriteAdmission = StorageWriteAdmission.ALLOW_ALL,
        diagnostics: ReaderAssetDiagnosticsSink = ReaderAssetDiagnosticsSink.NO_OP,
    ) = StoreFixture(quotaBytes, wallMillis, monotonicNanos, writeAdmission, backgroundScope, diagnostics)

    private fun facts(
        id: Int,
        securityScope: ReaderCacheSecurityScope = ReaderCacheSecurityScope.Public,
        persistenceMode: ReaderAssetPersistenceMode = ReaderAssetPersistenceMode.DURABLE_AUTOMATIC,
        identityMode: ReaderAssetIdentityMode = ReaderAssetIdentityMode.TRUSTED_STABLE,
    ): ReaderAssetCommitFacts {
        val source = ReaderAssetSourceNamespace.fromPluginId(PluginId("source"))
        val imageSet = ReaderImageSetNamespace(hash(id + 100))
        val key = ReaderPageAssetKey(
            schemaVersion = ReaderAssetKeySchemaVersion(1),
            sourceNamespace = source,
            securityScope = securityScope,
            contentVariant = ReaderContentVariant.ORIGINAL,
            persistenceMode = persistenceMode,
            imageSetNamespace = imageSet,
            runtimeIsolationScope = if (persistenceMode == ReaderAssetPersistenceMode.TRANSIENT_ONLY) {
                ReaderRuntimeAssetScopeId(hash(id + 200))
            } else {
                null
            },
            pageIdentityHash = ReaderAssetIdentityHash(hash(id + 300)),
            hash = ReaderAssetKeyHash(hash(id + 400)),
        )
        return ReaderAssetCommitFacts(
            key = key,
            storyId = StoryId("story"),
            canonicalChapterId = CanonicalChapterId("chapter"),
            releaseId = ChapterReleaseId("release:$id"),
            sourceNamespace = source,
            securityScope = securityScope,
            contentVariant = ReaderContentVariant.ORIGINAL,
            identityMode = identityMode,
            persistenceMode = persistenceMode,
            imageSetNamespace = imageSet,
            imageOrdinal = id,
        )
    }

    private fun payload(id: Int) = ReaderAssetPayload.verifiedBounded(bytes(id), "image/jpeg", "etag:$id")
    private fun bytes(id: Int, size: Int = 4) = ByteArray(size) { (id + it).toByte() }
    private fun hash(seed: Int) = seed.toString(16).padStart(64, '0').takeLast(64)
}

private class StoreFixture(
    quotaBytes: Long,
    wallMillis: Long,
    monotonicNanos: Long,
    private val writeAdmission: StorageWriteAdmission,
    reconciliationScope: kotlinx.coroutines.CoroutineScope,
    private val diagnostics: ReaderAssetDiagnosticsSink,
) {
    val metadata = FakeReaderAssetMetadataRepository()
    val blobs = FakeReaderAssetBlobStore()
    val clock = FakeClock(wallMillis)
    val monotonicClock = FakeMonotonicClock(monotonicNanos)
    val budget = AutomaticCacheBudgetCoordinator(
        cacheRepository = EmptyCacheRepository,
        documentBlobStore = EmptyChapterBlobStore,
        readerAssetMetadataRepository = metadata,
        readerAssetBlobStore = blobs,
        initialQuotaBytes = quotaBytes,
        reconciliationScope = reconciliationScope,
        diagnostics = diagnostics,
    )
    private var nextUuid = 1L
    val store = newStore()

    suspend fun seed(
        facts: ReaderAssetCommitFacts,
        bytes: ByteArray,
        checksum: BlobChecksum = BlobChecksum.sha256(bytes),
        includeBlob: Boolean = true,
        accessedAt: Long = 0L,
    ) {
        val blobId = ReaderAssetBlobId(hash(facts.imageOrdinal + 1_000))
        if (includeBlob) blobs.put(blobId, bytes)
        metadata.upsert(metadata(facts, blobId, bytes.size.toLong(), checksum, accessedAt))
    }

    suspend fun persist(facts: ReaderAssetCommitFacts, payload: ReaderAssetPayload): ReaderAssetCommitResult {
        val authority = requireNotNull(store.captureDurableWriteAuthority(facts))
        return store.commit(facts, authority, payload)
    }

    fun reconstructStore(): DownloadReaderAssetStore = newStore()

    private fun newStore() = DownloadReaderAssetStore(
        metadataRepository = metadata,
        blobStore = blobs,
        blobIdFactory = ReaderAssetBlobIdFactory { UUID(0L, nextUuid++) },
        budget = budget,
        clock = clock,
        monotonicClock = monotonicClock,
        writeAdmission = writeAdmission,
        runtimePolicy = AutomaticCacheRuntimePolicy(),
        diagnostics = diagnostics,
    )

    private fun metadata(
        facts: ReaderAssetCommitFacts,
        blobId: ReaderAssetBlobId,
        byteSize: Long,
        checksum: BlobChecksum,
        accessedAt: Long,
    ) = ReaderAssetMetadata(
        logicalAssetKeyHash = facts.key.hash,
        keySchemaVersion = facts.key.schemaVersion,
        storyId = facts.storyId,
        canonicalChapterId = facts.canonicalChapterId,
        chapterReleaseId = facts.releaseId,
        sourceNamespace = facts.sourceNamespace,
        securityScopeHash = null,
        contentVariant = facts.contentVariant,
        identityMode = facts.identityMode,
        persistenceMode = facts.persistenceMode,
        imageSetNamespaceHash = facts.imageSetNamespace,
        pageIdentityHash = facts.key.pageIdentityHash,
        pageOrdinal = facts.imageOrdinal,
        blobId = blobId.value,
        byteSize = byteSize,
        localBlobChecksum = checksum,
        sourceIntegrityHash = null,
        createdAtEpochMillis = accessedAt,
        lastAccessedAtEpochMillis = accessedAt,
        lastConsumedAtEpochMillis = null,
    )

    private fun hash(seed: Int) = seed.toString(16).padStart(64, '0').takeLast(64)
}

private class FakeReaderAssetMetadataRepository : ReaderAssetMetadataRepository {
    private val values = linkedMapOf<ReaderAssetKeyHash, ReaderAssetMetadata>()
    var failFind = false
    var failUpsert = false

    fun single(key: ReaderAssetKeyHash): ReaderAssetMetadata = requireNotNull(values[key])

    override suspend fun upsert(metadata: ReaderAssetMetadata) {
        if (failUpsert) error("metadata unavailable")
        values[metadata.logicalAssetKeyHash] = metadata
    }

    override suspend fun find(keys: Set<ReaderAssetKeyHash>): Map<ReaderAssetKeyHash, ReaderAssetMetadata> {
        if (failFind) error("metadata unavailable")
        return values.filterKeys { it in keys }
    }

    override suspend fun all() = values.values.toList()
    override suspend fun usageBytes() = values.values.sumOf(ReaderAssetMetadata::byteSize)
    override suspend fun detach(key: ReaderAssetKeyHash): ReaderAssetMetadata? = values.remove(key)
    override suspend fun detachAll(): List<ReaderAssetMetadata> = values.values.toList().also { values.clear() }
    override suspend fun detachSource(sourceNamespace: ReaderAssetSourceNamespace) =
        detachMatching { it.sourceNamespace == sourceNamespace }
    override suspend fun detachAccount(sourceNamespace: ReaderAssetSourceNamespace, securityScopeHash: String) =
        detachMatching { it.sourceNamespace == sourceNamespace && it.securityScopeHash == securityScopeHash }
    override suspend fun detachAllAccountsForSource(sourceNamespace: ReaderAssetSourceNamespace) =
        detachMatching { it.sourceNamespace == sourceNamespace && it.securityScopeHash != null }
    override suspend fun updateLastAccessed(key: ReaderAssetKeyHash, epochMillis: Long) {
        values[key]?.let { values[key] = it.copy(lastAccessedAtEpochMillis = epochMillis) }
    }
    override suspend fun updateLastConsumed(key: ReaderAssetKeyHash, epochMillis: Long) {
        values[key]?.let { values[key] = it.copy(lastConsumedAtEpochMillis = epochMillis) }
    }

    private fun detachMatching(predicate: (ReaderAssetMetadata) -> Boolean): List<ReaderAssetMetadata> =
        values.values.filter(predicate).also { rows -> rows.forEach { values.remove(it.logicalAssetKeyHash) } }
}

private enum class WriteOutcome { STORED, NO_SPACE, UNAVAILABLE }

private class FakeReaderAssetBlobStore : ReaderAssetBlobStore {
    private val values = linkedMapOf<ReaderAssetBlobId, ByteArray>()
    private val leases = mutableMapOf<ReaderAssetBlobId, Int>()
    private val pendingDeletes = mutableSetOf<ReaderAssetBlobId>()
    val writeOutcomes = ArrayDeque<WriteOutcome>()
    val writeCalls = mutableListOf<ReaderAssetBlobId>()
    val immediateDeletes = mutableListOf<ReaderAssetBlobId>()
    val normalDeletes = mutableListOf<ReaderAssetBlobId>()
    val failExists = mutableSetOf<String>()
    var afterStoredWrite: suspend (ReaderAssetBlobId) -> Unit = { }

    fun put(id: ReaderAssetBlobId, bytes: ByteArray) {
        values[id] = bytes.copyOf()
    }

    fun activeLeases(id: ReaderAssetBlobId): Int = leases[id] ?: 0

    override suspend fun writeAtomic(id: ReaderAssetBlobId, bytes: ByteArray): ReaderAssetBlobWriteResult {
        writeCalls += id
        return when (writeOutcomes.removeFirstOrNull() ?: WriteOutcome.STORED) {
            WriteOutcome.STORED -> {
                put(id, bytes)
                afterStoredWrite(id)
                ReaderAssetBlobWriteResult.Stored(
                    StoredReaderAssetBlob(id, bytes.size.toLong(), BlobChecksum.sha256(bytes)),
                )
            }
            WriteOutcome.NO_SPACE -> ReaderAssetBlobWriteResult.NoSpace
            WriteOutcome.UNAVAILABLE -> ReaderAssetBlobWriteResult.Unavailable(IllegalStateException("io"))
        }
    }

    override suspend fun open(id: ReaderAssetBlobId): ReaderAssetBlobReadLease? {
        val bytes = values[id]?.copyOf() ?: return null
        leases[id] = activeLeases(id) + 1
        return object : ReaderAssetBlobReadLease {
            private var closed = false
            override val sizeBytes: Long = bytes.size.toLong()
            override fun openStream(): InputStream = ByteArrayInputStream(bytes)
            override fun close() {
                if (closed) return
                closed = true
                val remaining = activeLeases(id) - 1
                if (remaining == 0) {
                    leases.remove(id)
                    if (pendingDeletes.remove(id)) values.remove(id)
                } else {
                    leases[id] = remaining
                }
            }
        }
    }

    override suspend fun exists(id: ReaderAssetBlobId): Boolean {
        if (id.value in failExists) error("filesystem unavailable")
        return id in values
    }

    override suspend fun hasActiveReadLease(id: ReaderAssetBlobId): Boolean = activeLeases(id) > 0

    override suspend fun tryDeleteNowIfUnleased(id: ReaderAssetBlobId): Boolean {
        immediateDeletes += id
        if (activeLeases(id) > 0) return false
        values.remove(id)
        return true
    }

    override suspend fun deleteWhenUnleased(id: ReaderAssetBlobId) {
        normalDeletes += id
        if (activeLeases(id) > 0) {
            pendingDeletes += id
        } else {
            values.remove(id)
        }
    }
}

private object EmptyCacheRepository : CacheRepository {
    override suspend fun entries() = emptyList<CacheEntry>()
    override suspend fun upsert(entry: CacheEntry) = Unit
    override suspend fun touch(key: ChapterBlobKey, accessedAtEpochMillis: Long) = Unit
    override suspend fun commitEviction(keys: List<ChapterBlobKey>) = emptyList<ChapterBlobKey>()
}

private object EmptyChapterBlobStore : ChapterBlobStore {
    override suspend fun read(key: ChapterBlobKey): ChapterBlob? = null
    override suspend fun write(key: ChapterBlobKey, blob: ChapterBlob) = Unit
    override suspend fun delete(key: ChapterBlobKey) = Unit
}

private class RecordingDownloadReaderAssetDiagnostics : ReaderAssetDiagnosticsSink {
    val events = mutableListOf<ReaderAssetDiagnosticEvent>()

    override fun record(event: ReaderAssetDiagnosticEvent) {
        events += event
    }
}
