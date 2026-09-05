package app.openstory.downloads.cache

import app.openstory.common.id.CanonicalChapterId
import app.openstory.common.id.ChapterReleaseId
import app.openstory.common.id.PluginId
import app.openstory.common.id.StoryId
import app.openstory.downloads.assets.ReaderAssetBlobId
import app.openstory.downloads.assets.ReaderAssetBlobReadLease
import app.openstory.downloads.assets.ReaderAssetBlobStore
import app.openstory.downloads.assets.ReaderAssetBlobWriteResult
import app.openstory.downloads.assets.ReaderAssetMetadata
import app.openstory.downloads.assets.ReaderAssetMetadataRepository
import app.openstory.downloads.blob.BlobChecksum
import app.openstory.downloads.blob.ChapterBlob
import app.openstory.downloads.blob.ChapterBlobKey
import app.openstory.downloads.blob.ChapterBlobNamespace
import app.openstory.downloads.blob.ChapterBlobStore
import app.openstory.reader.assets.ReaderAssetActiveProtections
import app.openstory.reader.assets.ReaderAssetIdentityHash
import app.openstory.reader.assets.ReaderAssetIdentityMode
import app.openstory.reader.assets.ReaderAssetKeyHash
import app.openstory.reader.assets.ReaderAssetKeySchemaVersion
import app.openstory.reader.assets.ReaderAssetPersistenceMode
import app.openstory.reader.assets.ReaderAssetProtectionClass
import app.openstory.reader.assets.ReaderAssetSourceNamespace
import app.openstory.reader.assets.ReaderContentVariant
import app.openstory.reader.assets.ReaderImageSetNamespace
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.coroutines.async
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.ExperimentalCoroutinesApi

@OptIn(ExperimentalCoroutinesApi::class)
class AutomaticCacheBudgetCoordinatorTest {
    @Test
    fun `document and image reservations spend one shared quota including pending bytes`() = runTest {
        val coordinator = coordinator(initialQuotaBytes = 100)
        val authority = assertNotNull(coordinator.captureWriteAuthority())

        val reservations = listOf(60L, 60L).map { bytes ->
            async { coordinator.reserve(bytes, authority) }
        }.awaitAll()

        assertEquals(1, reservations.count { it != null })
        val snapshot = coordinator.snapshot()
        assertEquals(60, snapshot.pendingReservationBytes)
        assertEquals(60, snapshot.totalAccountedBytes)
    }

    @Test
    fun `failed publication and repeated release return pending bytes exactly once`() = runTest {
        val coordinator = coordinator(initialQuotaBytes = 100)
        val authority = assertNotNull(coordinator.captureWriteAuthority())
        val reservation = assertNotNull(coordinator.reserve(60, authority))

        runCatching {
            coordinator.publishIfCurrent(authority, reservation) {
                error("metadata failed")
            }
        }
        coordinator.release(reservation)
        coordinator.release(reservation)

        assertEquals(0, coordinator.snapshot().pendingReservationBytes)
        assertNotNull(coordinator.reserve(100, authority))
    }

    @Test
    fun `quota zero revokes captured authority before publication and reenable never revives it`() = runTest {
        val coordinator = coordinator(initialQuotaBytes = 100)
        val stale = assertNotNull(coordinator.captureWriteAuthority())
        val reservation = assertNotNull(coordinator.reserve(40, stale))

        coordinator.updateQuota(0)

        assertNull(coordinator.captureWriteAuthority())
        assertIs<AutomaticCachePublicationResult.Revoked>(
            coordinator.publishIfCurrent(stale, reservation) { "must-not-publish" },
        )
        coordinator.updateQuota(100)
        assertNull(coordinator.reserve(1, stale))
        assertNotNull(coordinator.captureWriteAuthority())
    }

    @Test
    fun `scoped invalidation revokes only the affected source and account authorities`() = runTest {
        val coordinator = coordinator(initialQuotaBytes = 100)
        val source = ReaderAssetSourceNamespace.fromPluginId(PluginId("source"))
        val otherSource = ReaderAssetSourceNamespace.fromPluginId(PluginId("other"))
        val accountHash = sha(700)
        val otherAccountHash = sha(701)
        val sourceAuthority = assertNotNull(
            coordinator.captureWriteAuthority(AutomaticCacheWriteScope.ReaderAssetSource(source)),
        )
        val accountAuthority = assertNotNull(
            coordinator.captureWriteAuthority(AutomaticCacheWriteScope.ReaderAssetAccount(source, accountHash)),
        )
        val otherAccountAuthority = assertNotNull(
            coordinator.captureWriteAuthority(AutomaticCacheWriteScope.ReaderAssetAccount(source, otherAccountHash)),
        )
        val unrelatedAuthority = assertNotNull(
            coordinator.captureWriteAuthority(AutomaticCacheWriteScope.ReaderAssetSource(otherSource)),
        )

        coordinator.clearAutomatic(AutomaticCacheInvalidationScope.ReaderAssetAccount(source, accountHash))

        assertNull(coordinator.reserve(0, accountAuthority))
        coordinator.release(assertNotNull(coordinator.reserve(0, sourceAuthority)))
        coordinator.release(assertNotNull(coordinator.reserve(0, otherAccountAuthority)))
        coordinator.clearAutomatic(AutomaticCacheInvalidationScope.AllReaderAssetAccountsForSource(source))
        assertNull(coordinator.reserve(0, otherAccountAuthority))
        coordinator.release(assertNotNull(coordinator.reserve(0, sourceAuthority)))
        coordinator.clearAutomatic(AutomaticCacheInvalidationScope.ReaderAssetSource(source))
        assertNull(coordinator.reserve(0, sourceAuthority))
        coordinator.release(assertNotNull(coordinator.reserve(0, unrelatedAuthority)))
    }

    @Test
    fun `clear cannot enter between final authority revalidation and metadata publication`() = runTest {
        val documents = FakeCacheRepository()
        val coordinator = coordinator(initialQuotaBytes = 100, documents = documents)
        val authority = assertNotNull(coordinator.captureWriteAuthority())
        val reservation = assertNotNull(coordinator.reserve(10, authority))
        val enteredPublication = CompletableDeferred<Unit>()
        val allowPublication = CompletableDeferred<Unit>()
        val entry = document("race", 10, 1)
        val publication = async {
            coordinator.publishIfCurrent(authority, reservation) {
                enteredPublication.complete(Unit)
                allowPublication.await()
                documents.upsert(entry)
            }
        }
        enteredPublication.await()

        val clear = async { coordinator.clearAutomatic(AutomaticCacheInvalidationScope.AllAutomatic) }
        runCurrent()
        assertFalse(clear.isCompleted)
        allowPublication.complete(Unit)

        assertIs<AutomaticCachePublicationResult.Published<Unit>>(publication.await())
        clear.await()
        assertTrue(documents.entries().isEmpty())
    }

    @Test
    fun `cancellation during final publication releases pending reservation`() = runTest {
        val coordinator = coordinator(initialQuotaBytes = 100)
        val authority = assertNotNull(coordinator.captureWriteAuthority())
        val reservation = assertNotNull(coordinator.reserve(60, authority))
        val enteredPublication = CompletableDeferred<Unit>()
        val publication = async {
            coordinator.publishIfCurrent(authority, reservation) {
                enteredPublication.complete(Unit)
                awaitCancellation()
            }
        }
        enteredPublication.await()

        publication.cancelAndJoin()

        assertEquals(0, coordinator.snapshot().pendingReservationBytes)
        assertNotNull(coordinator.reserve(100, authority))
    }

    @Test
    fun `clear deletes only the detached image generation and permits a post-clear same-key generation`() = runTest {
        val assets = FakeAssetMetadataRepository()
        val blobs = FakeAssetBlobStore()
        val key = hash(1)
        val old = asset(key = key, blobSeed = 1, bytes = 20)
        assets.upsert(old)
        var coordinator: AutomaticCacheBudgetCoordinator? = null
        blobs.onDeleteWhenUnleased = {
            val current = checkNotNull(coordinator)
            val authority = assertNotNull(current.captureWriteAuthority())
            val reservation = assertNotNull(current.reserve(20, authority))
            current.publishIfCurrent(authority, reservation) {
                assets.upsert(asset(key = key, blobSeed = 2, bytes = 20))
            }
        }
        coordinator = coordinator(
            initialQuotaBytes = 100,
            assets = assets,
            assetBlobs = blobs,
        )

        coordinator.clearAutomatic(AutomaticCacheInvalidationScope.AllAutomatic)

        assertEquals(blobId(1), blobs.normalDeletes.single())
        assertEquals(blobId(2).value, assets.all().single().blobId)
    }

    @Test
    fun `stale corruption repair preserves current metadata and deletes only the stale generation`() = runTest {
        val assets = FakeAssetMetadataRepository()
        val blobs = FakeAssetBlobStore()
        val key = hash(4)
        val stale = asset(key = key, blobSeed = 4, bytes = 20)
        val current = asset(key = key, blobSeed = 5, bytes = 20)
        assets.upsert(current)
        val coordinator = coordinator(
            initialQuotaBytes = 100,
            assets = assets,
            assetBlobs = blobs,
        )

        coordinator.invalidateReaderAssetGeneration(stale)
        runCurrent()

        assertEquals(current, assets.find(setOf(key))[key])
        assertEquals(listOf(blobId(4)), blobs.normalDeletes)
    }

    @Test
    fun `normal reconciliation evicts only unprotected classes and stops before progress protection`() = runTest {
        val evictionEvents = mutableListOf<String>()
        val documents = FakeCacheRepository(evictionEvents)
        val assets = FakeAssetMetadataRepository(evictionEvents)
        val documentBlobs = FakeChapterBlobStore()
        val assetBlobs = FakeAssetBlobStore()
        val warmDocument = document("warm", bytes = 10, accessedAt = 2)
        val progressDocument = document("progress", bytes = 10, accessedAt = 1)
        documents.upsert(warmDocument)
        documents.upsert(progressDocument)
        val cold = asset(hash(1), 1, 10, accessedAt = 1, consumedAt = null)
        val consumed = asset(hash(2), 2, 10, accessedAt = 2, consumedAt = 2)
        val recent = asset(hash(3), 3, 10, accessedAt = 3, consumedAt = 3)
        assets.upsert(cold)
        assets.upsert(consumed)
        assets.upsert(recent)
        val coordinator = coordinator(
            initialQuotaBytes = 5,
            documents = documents,
            documentBlobs = documentBlobs,
            assets = assets,
            assetBlobs = assetBlobs,
        )
        coordinator.updateProgressProtectedReleaseIds(setOf(progressDocument.key.releaseId))

        coordinator.reconcile(
            ReaderAssetActiveProtections(
                mapOf(recent.logicalAssetKeyHash to ReaderAssetProtectionClass.RECENT_HISTORY_2),
            ),
        )

        assertEquals(
            listOf(
                "asset:${cold.logicalAssetKeyHash.value}",
                "document:${warmDocument.key.releaseId.value}",
                "asset:${consumed.logicalAssetKeyHash.value}",
            ),
            evictionEvents,
        )
        assertEquals(listOf(recent), assets.all())
        assertTrue(progressDocument in documents.entries())
        assertTrue(coordinator.snapshot().activeProtectedOverflowBytes > 0)
    }

    @Test
    fun `over quota inventory asynchronously reconciles toward the low watermark`() = runTest {
        val documents = FakeCacheRepository()
        val blobs = FakeChapterBlobStore()
        repeat(11) { index -> documents.upsert(document("d$index", 10, index.toLong())) }
        val coordinator = coordinator(
            initialQuotaBytes = 100,
            documents = documents,
            documentBlobs = blobs,
            reconciliationScope = this,
        )

        coordinator.updateQuota(100)
        advanceUntilIdle()

        assertTrue(coordinator.snapshot().committedBytes <= 90)
    }

    @Test
    fun `denied reservation at exactly full quota schedules room for a later commit`() = runTest {
        val documents = FakeCacheRepository()
        repeat(10) { index -> documents.upsert(document("full-$index", 10, index.toLong())) }
        val coordinator = coordinator(
            initialQuotaBytes = 100,
            documents = documents,
            reconciliationScope = this,
        )
        val authority = assertNotNull(coordinator.captureWriteAuthority())

        assertNull(coordinator.reserve(1, authority))
        advanceUntilIdle()

        assertTrue(coordinator.snapshot().committedBytes <= 90)
        assertNotNull(coordinator.reserve(1, authority))
    }

    @Test
    fun `emergency maintenance degrades protected classes only after ordinary victims in frozen order`() = runTest {
        val evictionEvents = mutableListOf<String>()
        val documents = FakeCacheRepository(evictionEvents)
        val assets = FakeAssetMetadataRepository(evictionEvents)
        val blobs = FakeAssetBlobStore()
        val progress = document("progress-emergency", 10, 1)
        documents.upsert(progress)
        val ordinary = asset(hash(11), 11, 10, accessedAt = 0)
        val recent2 = asset(hash(12), 12, 10, consumedAt = 2)
        val recent1 = asset(hash(13), 13, 10, consumedAt = 3)
        val consumed = asset(hash(14), 14, 10, consumedAt = 4)
        val interactive = asset(hash(15), 15, 10, consumedAt = 5)
        listOf(ordinary, recent2, recent1, consumed, interactive).forEach { assets.upsert(it) }
        val coordinator = coordinator(
            initialQuotaBytes = 1_000,
            documents = documents,
            assets = assets,
            assetBlobs = blobs,
        )
        coordinator.updateProgressProtectedReleaseIds(setOf(progress.key.releaseId))
        coordinator.replaceActiveProtections(
            ReaderAssetActiveProtections(
                mapOf(
                    recent2.logicalAssetKeyHash to ReaderAssetProtectionClass.RECENT_HISTORY_2,
                    recent1.logicalAssetKeyHash to ReaderAssetProtectionClass.RECENT_HISTORY_1,
                    consumed.logicalAssetKeyHash to ReaderAssetProtectionClass.ACTIVE_CONSUMED,
                    interactive.logicalAssetKeyHash to ReaderAssetProtectionClass.ACTIVE_INTERACTIVE,
                ),
            ),
        )

        val report = coordinator.relieveEmergencyPressure { false }

        assertEquals(6, report.victimsProcessed)
        assertEquals(60L, report.physicallyReclaimedBytes)
        assertFalse(report.reserveRestored)
        assertEquals(
            listOf(
                "asset:${ordinary.logicalAssetKeyHash.value}",
                "document:${progress.key.releaseId.value}",
                "asset:${recent2.logicalAssetKeyHash.value}",
                "asset:${recent1.logicalAssetKeyHash.value}",
                "asset:${consumed.logicalAssetKeyHash.value}",
                "asset:${interactive.logicalAssetKeyHash.value}",
            ),
            evictionEvents,
        )
    }

    @Test
    fun `emergency maintenance never detaches an image with an active read lease`() = runTest {
        val assets = FakeAssetMetadataRepository()
        val leased = asset(hash(90), 90, 10, consumedAt = 1)
        assets.upsert(leased)
        val blobs = FakeAssetBlobStore(
            immediateDeleteResult = { false },
            activeLease = { true },
        )
        val coordinator = coordinator(
            initialQuotaBytes = 1_000,
            assets = assets,
            assetBlobs = blobs,
        )
        coordinator.replaceActiveProtections(
            ReaderAssetActiveProtections(
                mapOf(leased.logicalAssetKeyHash to ReaderAssetProtectionClass.ACTIVE_INTERACTIVE),
            ),
        )

        val report = coordinator.relieveEmergencyPressure { false }

        assertEquals(listOf(leased), assets.all())
        assertEquals(0L, report.physicallyReclaimedBytes)
        assertFalse(report.madeProgress)
        assertFalse(report.hasMoreVictims)
        assertTrue(blobs.immediateDeletes.isEmpty())
    }

    @Test
    fun `emergency maintenance skips leased prefix and reaches later unleased victims without spinning`() = runTest {
        val assets = FakeAssetMetadataRepository()
        val leasedBlobIds = mutableSetOf<ReaderAssetBlobId>()
        val blobs = FakeAssetBlobStore(activeLease = leasedBlobIds::contains)
        val protections = mutableMapOf<ReaderAssetKeyHash, ReaderAssetProtectionClass>()
        repeat(40) { index ->
            val metadata = asset(hash(index + 200), index + 200, 10, consumedAt = index.toLong())
            assets.upsert(metadata)
            protections[metadata.logicalAssetKeyHash] = ReaderAssetProtectionClass.ACTIVE_INTERACTIVE
            if (index < 32) leasedBlobIds += ReaderAssetBlobId(metadata.blobId)
        }
        val coordinator = coordinator(
            initialQuotaBytes = 1_000,
            assets = assets,
            assetBlobs = blobs,
            reconciliationScope = this,
        )
        coordinator.replaceActiveProtections(ReaderAssetActiveProtections(protections))

        coordinator.requestEmergencyReconciliation { false }
        advanceUntilIdle()

        assertEquals(8, blobs.immediateDeletes.size)
        assertEquals(32, assets.all().size)
        assertTrue(assets.all().all { ReaderAssetBlobId(it.blobId) in leasedBlobIds })
    }

    @Test
    fun `emergency maintenance schedules another bounded pass after 32 victims`() = runTest {
        val assets = FakeAssetMetadataRepository()
        val blobs = FakeAssetBlobStore()
        val protections = mutableMapOf<ReaderAssetKeyHash, ReaderAssetProtectionClass>()
        repeat(40) { index ->
            val metadata = asset(hash(index + 100), index + 100, 10, consumedAt = index.toLong())
            assets.upsert(metadata)
            protections[metadata.logicalAssetKeyHash] = ReaderAssetProtectionClass.ACTIVE_INTERACTIVE
        }
        val coordinator = AutomaticCacheBudgetCoordinator(
            cacheRepository = FakeCacheRepository(),
            documentBlobStore = FakeChapterBlobStore(),
            readerAssetMetadataRepository = assets,
            readerAssetBlobStore = blobs,
            initialQuotaBytes = 1_000,
            reconciliationScope = this,
        )
        coordinator.replaceActiveProtections(ReaderAssetActiveProtections(protections))

        coordinator.requestEmergencyReconciliation { false }
        advanceUntilIdle()

        assertEquals(40, blobs.immediateDeletes.size)
        assertTrue(assets.all().isEmpty())
    }

    @Test
    fun `physical relief examines at most 32 unprotected victims and leased image reclaims zero bytes`() = runTest {
        val assets = FakeAssetMetadataRepository()
        val blobs = FakeAssetBlobStore(immediateDeleteResult = { id -> id != blobId(1) })
        repeat(40) { index -> assets.upsert(asset(hash(index + 1), index + 1, 10)) }
        val coordinator = coordinator(
            initialQuotaBytes = 1_000,
            assets = assets,
            assetBlobs = blobs,
        )

        val reclaimed = coordinator.relievePhysicalPressure(requiredBytes = 1_000)

        assertEquals(32, blobs.immediateDeletes.size)
        assertEquals(310, reclaimed)
    }

    @Test
    fun `physical relief returns partial bytes without degrading recent or active protection`() = runTest {
        val assets = FakeAssetMetadataRepository()
        val blobs = FakeAssetBlobStore()
        val ordinary = asset(hash(1), 1, 10)
        val recent = asset(hash(2), 2, 90, consumedAt = 2)
        assets.upsert(ordinary)
        assets.upsert(recent)
        val coordinator = coordinator(
            initialQuotaBytes = 1_000,
            assets = assets,
            assetBlobs = blobs,
        )
        coordinator.reconcile(
            ReaderAssetActiveProtections(
                mapOf(recent.logicalAssetKeyHash to ReaderAssetProtectionClass.RECENT_HISTORY_1),
            ),
        )

        val reclaimed = coordinator.relievePhysicalPressure(100)

        assertEquals(10, reclaimed)
        assertEquals(listOf(recent), assets.all())
        assertEquals(listOf(blobId(1)), blobs.immediateDeletes)
    }

    @Test
    fun `runtime policy freezes durable cache watermarks touch interval and relief bound`() {
        assertEquals(
            AutomaticCacheRuntimePolicy(
                highWatermarkBasisPoints = 10_000,
                lowWatermarkBasisPoints = 9_000,
                assetAccessTouchIntervalMillis = 300_000L,
                maxEnospcEvictionVictims = 32,
            ),
            AutomaticCacheRuntimePolicy(),
        )
    }

    @Test
    fun `explicit downloads never enter automatic committed usage or eviction`() = runTest {
        val documents = FakeCacheRepository()
        val blobs = FakeChapterBlobStore()
        val explicit = document("download", 500, 1, ChapterBlobNamespace.EXPLICIT_DOWNLOAD)
        val automatic = document("automatic", 20, 2)
        documents.upsert(explicit)
        documents.upsert(automatic)
        val coordinator = coordinator(
            initialQuotaBytes = 100,
            documents = documents,
            documentBlobs = blobs,
        )

        assertEquals(20, coordinator.snapshot().committedBytes)
        coordinator.relievePhysicalPressure(100)
        assertEquals(listOf(explicit), documents.entries())
        assertTrue(explicit.key !in blobs.deleted)
    }

    private fun TestScope.coordinator(
        initialQuotaBytes: Long,
        documents: CacheRepository = FakeCacheRepository(),
        documentBlobs: ChapterBlobStore = FakeChapterBlobStore(),
        assets: ReaderAssetMetadataRepository = FakeAssetMetadataRepository(),
        assetBlobs: ReaderAssetBlobStore = FakeAssetBlobStore(),
        reconciliationScope: kotlinx.coroutines.CoroutineScope = backgroundScope,
    ) = AutomaticCacheBudgetCoordinator(
        cacheRepository = documents,
        documentBlobStore = documentBlobs,
        readerAssetMetadataRepository = assets,
        readerAssetBlobStore = assetBlobs,
        initialQuotaBytes = initialQuotaBytes,
        reconciliationScope = reconciliationScope,
    )

    private fun document(
        id: String,
        bytes: Long,
        accessedAt: Long,
        namespace: ChapterBlobNamespace = ChapterBlobNamespace.AUTOMATIC_CACHE,
    ) = CacheEntry(
        key = ChapterBlobKey(namespace, ChapterReleaseId("release:$id"), "fingerprint:$id"),
        checksum = BlobChecksum.sha256(id.encodeToByteArray()),
        sizeBytes = bytes,
        lastAccessedAtEpochMillis = accessedAt,
    )

    private fun asset(
        key: ReaderAssetKeyHash,
        blobSeed: Int,
        bytes: Long,
        accessedAt: Long = 0,
        consumedAt: Long? = null,
    ) = ReaderAssetMetadata(
        logicalAssetKeyHash = key,
        keySchemaVersion = ReaderAssetKeySchemaVersion(1),
        storyId = StoryId("story"),
        canonicalChapterId = CanonicalChapterId("chapter"),
        chapterReleaseId = ChapterReleaseId("release:image"),
        sourceNamespace = ReaderAssetSourceNamespace.fromPluginId(PluginId("source")),
        securityScopeHash = null,
        contentVariant = ReaderContentVariant.ORIGINAL,
        identityMode = ReaderAssetIdentityMode.TRUSTED_STABLE,
        persistenceMode = ReaderAssetPersistenceMode.DURABLE_AUTOMATIC,
        imageSetNamespaceHash = ReaderImageSetNamespace(sha(90)),
        pageIdentityHash = ReaderAssetIdentityHash(sha(blobSeed + 100)),
        pageOrdinal = blobSeed,
        blobId = blobId(blobSeed).value,
        byteSize = bytes,
        localBlobChecksum = BlobChecksum.sha256(byteArrayOf(blobSeed.toByte())),
        sourceIntegrityHash = null,
        createdAtEpochMillis = 0,
        lastAccessedAtEpochMillis = accessedAt,
        lastConsumedAtEpochMillis = consumedAt,
    )

    private fun hash(seed: Int) = ReaderAssetKeyHash(sha(seed))
    private fun blobId(seed: Int) = ReaderAssetBlobId(sha(seed + 1_000))
    private fun sha(seed: Int) = seed.toString(16).padStart(64, '0').takeLast(64)
}

private class FakeCacheRepository(
    private val detachEvents: MutableList<String> = mutableListOf(),
) : CacheRepository {
    private val values = linkedMapOf<ChapterBlobKey, CacheEntry>()

    override suspend fun entries(): List<CacheEntry> = values.values.toList()
    override suspend fun upsert(entry: CacheEntry) { values[entry.key] = entry }
    override suspend fun touch(key: ChapterBlobKey, accessedAtEpochMillis: Long) = Unit
    override suspend fun commitEviction(keys: List<ChapterBlobKey>): List<ChapterBlobKey> = keys.mapNotNull { key ->
        values.remove(key)?.also { detachEvents += "document:${it.key.releaseId.value}" }?.key
    }
}

private class FakeChapterBlobStore : ChapterBlobStore {
    val deleted = mutableListOf<ChapterBlobKey>()
    override suspend fun read(key: ChapterBlobKey): ChapterBlob? = null
    override suspend fun write(key: ChapterBlobKey, blob: ChapterBlob) = Unit
    override suspend fun delete(key: ChapterBlobKey) { deleted += key }
}

private class FakeAssetMetadataRepository(
    private val detachEvents: MutableList<String> = mutableListOf(),
) : ReaderAssetMetadataRepository {
    private val values = linkedMapOf<ReaderAssetKeyHash, ReaderAssetMetadata>()

    override suspend fun upsert(metadata: ReaderAssetMetadata) { values[metadata.logicalAssetKeyHash] = metadata }
    override suspend fun find(keys: Set<ReaderAssetKeyHash>) = values.filterKeys { it in keys }
    override suspend fun all() = values.values.toList()
    override suspend fun usageBytes() = values.values.sumOf(ReaderAssetMetadata::byteSize)
    override suspend fun detach(key: ReaderAssetKeyHash): ReaderAssetMetadata? =
        values.remove(key)?.also { detachEvents += "asset:${it.logicalAssetKeyHash.value}" }
    override suspend fun detachAll(): List<ReaderAssetMetadata> = values.values.toList().also {
        it.forEach { row -> detachEvents += "asset:${row.logicalAssetKeyHash.value}" }
        values.clear()
    }
    override suspend fun detachSource(sourceNamespace: ReaderAssetSourceNamespace) =
        detachMatching { it.sourceNamespace == sourceNamespace }
    override suspend fun detachAccount(sourceNamespace: ReaderAssetSourceNamespace, securityScopeHash: String) =
        detachMatching { it.sourceNamespace == sourceNamespace && it.securityScopeHash == securityScopeHash }
    override suspend fun detachAllAccountsForSource(sourceNamespace: ReaderAssetSourceNamespace) =
        detachMatching { it.sourceNamespace == sourceNamespace && it.securityScopeHash != null }
    override suspend fun updateLastAccessed(key: ReaderAssetKeyHash, epochMillis: Long) = Unit
    override suspend fun updateLastConsumed(key: ReaderAssetKeyHash, epochMillis: Long) = Unit

    private fun detachMatching(predicate: (ReaderAssetMetadata) -> Boolean): List<ReaderAssetMetadata> =
        values.values.filter(predicate).also { rows ->
            rows.forEach { row ->
                values.remove(row.logicalAssetKeyHash)
                detachEvents += "asset:${row.logicalAssetKeyHash.value}"
            }
        }
}

private class FakeAssetBlobStore(
    private val immediateDeleteResult: (ReaderAssetBlobId) -> Boolean = { true },
    private val activeLease: (ReaderAssetBlobId) -> Boolean = { false },
) : ReaderAssetBlobStore {
    val immediateDeletes = mutableListOf<ReaderAssetBlobId>()
    val normalDeletes = mutableListOf<ReaderAssetBlobId>()
    var onDeleteWhenUnleased: suspend () -> Unit = {}

    override suspend fun writeAtomic(id: ReaderAssetBlobId, bytes: ByteArray): ReaderAssetBlobWriteResult =
        error("not used")
    override suspend fun open(id: ReaderAssetBlobId): ReaderAssetBlobReadLease? = null
    override suspend fun exists(id: ReaderAssetBlobId): Boolean = false
    override suspend fun hasActiveReadLease(id: ReaderAssetBlobId): Boolean = activeLease(id)
    override suspend fun tryDeleteNowIfUnleased(id: ReaderAssetBlobId): Boolean {
        immediateDeletes += id
        return immediateDeleteResult(id)
    }
    override suspend fun deleteWhenUnleased(id: ReaderAssetBlobId) {
        normalDeletes += id
        onDeleteWhenUnleased()
    }
}
