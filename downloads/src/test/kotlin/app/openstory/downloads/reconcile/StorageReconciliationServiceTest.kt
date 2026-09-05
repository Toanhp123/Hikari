package app.openstory.downloads.reconcile

import app.openstory.common.id.ChapterReleaseId
import app.openstory.downloads.DownloadState
import app.openstory.downloads.blob.ChapterBlobKey
import app.openstory.downloads.assets.ReaderAssetBlobId
import app.openstory.downloads.blob.ChapterBlobNamespace
import app.openstory.reader.assets.ReaderAssetKeyHash
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlinx.coroutines.test.runTest

class StorageReconciliationServiceTest {
    @Test
    fun `orphan blobs and interrupted temp writes are deleted`() = runTest {
        val orphan = StorageArtifactId("orphan")
        val interrupted = StorageArtifactId("interrupted")
        val inventory = FakeStorageInventory(
            snapshot = StorageInventorySnapshot(
                presentKeys = emptySet(),
                orphanArtifacts = listOf(orphan),
                interruptedWriteArtifacts = listOf(interrupted),
            ),
        )
        val repository = FakeReconciliationRepository()

        val report = service(repository, inventory).reconcile()

        assertEquals(listOf(orphan, interrupted), inventory.deleted)
        assertEquals(2, report.deletedArtifactCount)
    }

    @Test
    fun `artifact that gains metadata during reconciliation is not deleted`() = runTest {
        val artifact = StorageArtifactId("new-download")
        val key = downloadKey()
        val repository = FakeReconciliationRepository(
            afterCommit = listOf(metadata(key, DownloadState.COMPLETED)),
        )
        val inventory = FakeStorageInventory(
            snapshots = ArrayDeque(
                listOf(
                    StorageInventorySnapshot(orphanArtifacts = listOf(artifact)),
                    StorageInventorySnapshot(presentKeys = setOf(key)),
                ),
            ),
        )

        val report = service(repository, inventory).reconcile()

        assertTrue(inventory.deleted.isEmpty())
        assertEquals(0, report.deletedArtifactCount)
    }

    @Test
    fun `missing cache blob removes stale cache metadata`() = runTest {
        val repository = FakeReconciliationRepository(metadata(cacheKey()))

        service(repository, FakeStorageInventory()).reconcile()

        assertEquals(listOf(cacheKey()), repository.committed.removedMetadata)
    }

    @Test
    fun `missing completed download becomes integrity failure without deleting protected content`() = runTest {
        val repository = FakeReconciliationRepository(
            metadata(downloadKey(), DownloadState.COMPLETED),
        )
        val inventory = FakeStorageInventory()

        service(repository, inventory).reconcile()

        assertEquals(
            listOf(StorageDownloadFailure(downloadKey(), "download.integrity_missing")),
            repository.committed.failedDownloads,
        )
        assertTrue(inventory.deleted.isEmpty())
    }

    @Test
    fun `stale running metadata becomes interrupted failure`() = runTest {
        val repository = FakeReconciliationRepository(
            metadata(downloadKey(), DownloadState.RUNNING, updatedAt = 100),
        )

        service(repository, FakeStorageInventory()).reconcile()

        assertEquals(
            listOf(StorageDownloadFailure(downloadKey(), "download.interrupted")),
            repository.committed.failedDownloads,
        )
    }

    @Test
    fun `missing reader asset blob detaches stale generation only after complete inventory scan`() = runTest {
        val entry = readerEntry(1)
        val readerStore = FakeReaderAssetReconciliationStore(listOf(entry))
        val readerInventory = FakeReaderAssetReconciliationInventory(
            snapshots = ArrayDeque(
                listOf(
                    ReaderAssetStorageInventorySnapshot(scanComplete = true),
                    ReaderAssetStorageInventorySnapshot(scanComplete = true),
                ),
            ),
        )

        val report = service(
            FakeReconciliationRepository(),
            FakeStorageInventory(),
            readerStore,
            readerInventory,
        ).reconcile()

        assertEquals(listOf(entry), readerStore.detachedMissing)
        assertEquals(1, report.removedReaderAssetMetadataCount)
    }

    @Test
    fun `incomplete reader asset scan never infers missing metadata`() = runTest {
        val entry = readerEntry(2)
        val readerStore = FakeReaderAssetReconciliationStore(listOf(entry))
        val readerInventory = FakeReaderAssetReconciliationInventory(
            snapshot = ReaderAssetStorageInventorySnapshot(scanComplete = false),
        )

        service(
            FakeReconciliationRepository(),
            FakeStorageInventory(),
            readerStore,
            readerInventory,
        ).reconcile()

        assertTrue(readerStore.detachedMissing.isEmpty())
    }

    @Test
    fun `reader asset orphan that gains metadata before revalidation is not deleted`() = runTest {
        val entry = readerEntry(3)
        val artifact = StorageArtifactId("reader-asset-blob:${entry.blobId.value}")
        val readerStore = FakeReaderAssetReconciliationStore(
            initial = emptyList(),
            afterFirstRead = listOf(entry),
        )
        val readerInventory = FakeReaderAssetReconciliationInventory(
            snapshots = ArrayDeque(
                listOf(
                    ReaderAssetStorageInventorySnapshot(
                        orphanArtifacts = listOf(artifact),
                        scanComplete = true,
                    ),
                    ReaderAssetStorageInventorySnapshot(
                        presentBlobIds = setOf(entry.blobId),
                        scanComplete = true,
                    ),
                ),
            ),
        )

        val report = service(
            FakeReconciliationRepository(),
            FakeStorageInventory(),
            readerStore,
            readerInventory,
        ).reconcile()

        assertTrue(readerInventory.deleted.isEmpty())
        assertEquals(0, report.deletedReaderAssetArtifactCount)
    }

    @Test
    fun `active unpublished reader generation is protected in every reconciliation scan`() = runTest {
        val activeBlob = ReaderAssetBlobId(sha(404))
        val readerStore = FakeReaderAssetReconciliationStore(activeBlobIds = setOf(activeBlob))
        val readerInventory = FakeReaderAssetReconciliationInventory()

        service(
            FakeReconciliationRepository(),
            FakeStorageInventory(),
            readerStore,
            readerInventory,
        ).reconcile()

        assertEquals(listOf(setOf(activeBlob), setOf(activeBlob)), readerInventory.expectedScans)
        assertTrue(readerInventory.deleted.isEmpty())
    }

    @Test
    fun `reader asset cleanup defensively deletes at most bounded artifacts per pass`() = runTest {
        val artifacts = List(80) { StorageArtifactId("reader-orphan-$it") }
        val readerInventory = FakeReaderAssetReconciliationInventory(
            snapshot = ReaderAssetStorageInventorySnapshot(
                orphanArtifacts = artifacts,
                scanComplete = false,
            ),
        )

        service(
            FakeReconciliationRepository(),
            FakeStorageInventory(),
            FakeReaderAssetReconciliationStore(),
            readerInventory,
            readerAssetScanLimit = 32,
        ).reconcile()

        assertEquals(32, readerInventory.deleted.size)
    }

    @Test
    fun `low space refuses new writes without evicting completed downloads`() = runTest {
        val key = downloadKey()
        val repository = FakeReconciliationRepository(metadata(key, DownloadState.COMPLETED))
        val inventory = FakeStorageInventory(
            snapshot = StorageInventorySnapshot(presentKeys = setOf(key)),
            canStore = false,
        )
        val service = service(repository, inventory)

        assertFalse(service.canStore(1))
        service.reconcile()

        assertEquals(StorageMetadataRepairPlan(), repository.committed)
        assertTrue(inventory.deleted.isEmpty())
    }

    @Test
    fun `non-negative payload is admitted when capacity policy allows it`() {
        assertTrue(service(FakeReconciliationRepository(), FakeStorageInventory()).canStore(1))
    }

    private fun service(
        repository: StorageReconciliationRepository,
        inventory: StorageReconciliationInventory,
        readerAssets: ReaderAssetReconciliationStore? = null,
        readerAssetInventory: ReaderAssetReconciliationInventory? = null,
        readerAssetScanLimit: Int = 64,
    ) = StorageReconciliationService(
        repository = repository,
        inventory = inventory,
        readerAssets = readerAssets,
        readerAssetInventory = readerAssetInventory,
        readerAssetScanLimit = readerAssetScanLimit,
        activeWriteWindowMillis = 500,
        now = { 1_000 },
    )

    private fun metadata(
        key: ChapterBlobKey,
        state: DownloadState? = null,
        updatedAt: Long = 900,
    ) = StorageMetadataEntry(key, state, updatedAt)

    private fun readerEntry(seed: Int) = ReaderAssetReconciliationEntry(
        logicalAssetKeyHash = ReaderAssetKeyHash(sha(seed)),
        blobId = ReaderAssetBlobId(sha(seed + 100)),
    )

    private fun sha(seed: Int) = seed.toString(16).padStart(64, '0').takeLast(64)

    private fun cacheKey() = key(ChapterBlobNamespace.AUTOMATIC_CACHE, "cache")
    private fun downloadKey() = key(ChapterBlobNamespace.EXPLICIT_DOWNLOAD, "download")
    private fun key(namespace: ChapterBlobNamespace, id: String) = ChapterBlobKey(
        namespace,
        ChapterReleaseId(id),
        "fingerprint-$id",
    )
}

private class FakeReconciliationRepository(
    private vararg val values: StorageMetadataEntry,
    private val afterCommit: List<StorageMetadataEntry>? = null,
) : StorageReconciliationRepository {
    var committed = StorageMetadataRepairPlan()
    private var didCommit = false

    override suspend fun storageEntries(): List<StorageMetadataEntry> =
        if (didCommit) afterCommit ?: values.toList() else values.toList()

    override suspend fun commit(plan: StorageMetadataRepairPlan, updatedAtEpochMillis: Long) {
        committed = plan
        didCommit = true
    }
}

private class FakeStorageInventory(
    private val snapshot: StorageInventorySnapshot = StorageInventorySnapshot(),
    private val snapshots: ArrayDeque<StorageInventorySnapshot>? = null,
    private val canStore: Boolean = true,
) : StorageReconciliationInventory, StorageWriteAdmission {
    val deleted = mutableListOf<StorageArtifactId>()

    override suspend fun scan(
        expectedKeys: Set<ChapterBlobKey>,
        staleBeforeEpochMillis: Long,
    ): StorageInventorySnapshot = snapshots?.removeFirst() ?: snapshot

    override suspend fun delete(artifacts: List<StorageArtifactId>) {
        deleted += artifacts
    }

    override fun canStore(payloadBytes: Long): Boolean = canStore
}

private class FakeReaderAssetReconciliationStore(
    initial: List<ReaderAssetReconciliationEntry> = emptyList(),
    private val afterFirstRead: List<ReaderAssetReconciliationEntry>? = null,
    private val activeBlobIds: Set<ReaderAssetBlobId> = emptySet(),
) : ReaderAssetReconciliationStore {
    private val values = initial.associateByTo(linkedMapOf(), ReaderAssetReconciliationEntry::logicalAssetKeyHash)
    private var reads = 0
    val detachedMissing = mutableListOf<ReaderAssetReconciliationEntry>()

    override suspend fun reconciliationEntries(): List<ReaderAssetReconciliationEntry> {
        reads += 1
        if (reads > 1 && afterFirstRead != null) {
            values.clear()
            afterFirstRead.associateByTo(values, ReaderAssetReconciliationEntry::logicalAssetKeyHash)
        }
        return values.values.toList()
    }

    override suspend fun activeGenerationBlobIds(): Set<ReaderAssetBlobId> = activeBlobIds

    override suspend fun detachMissingGeneration(expected: ReaderAssetReconciliationEntry): Boolean {
        val current = values[expected.logicalAssetKeyHash]
        if (current != expected) return false
        values.remove(expected.logicalAssetKeyHash)
        detachedMissing += expected
        return true
    }
}

private class FakeReaderAssetReconciliationInventory(
    private val snapshot: ReaderAssetStorageInventorySnapshot = ReaderAssetStorageInventorySnapshot(),
    private val snapshots: ArrayDeque<ReaderAssetStorageInventorySnapshot>? = null,
) : ReaderAssetReconciliationInventory {
    val deleted = mutableListOf<StorageArtifactId>()
    val expectedScans = mutableListOf<Set<ReaderAssetBlobId>>()

    override suspend fun scanReaderAssets(
        expectedBlobIds: Set<ReaderAssetBlobId>,
        staleBeforeEpochMillis: Long,
        limit: Int,
    ): ReaderAssetStorageInventorySnapshot {
        expectedScans += expectedBlobIds.toSet()
        return snapshots?.removeFirst() ?: snapshot
    }

    override suspend fun deleteReaderAssetArtifacts(artifacts: List<StorageArtifactId>) {
        deleted += artifacts
    }
}
