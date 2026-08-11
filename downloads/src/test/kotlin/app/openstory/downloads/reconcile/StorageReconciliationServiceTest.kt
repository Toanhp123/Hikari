package app.openstory.downloads.reconcile

import app.openstory.common.id.ChapterReleaseId
import app.openstory.downloads.DownloadState
import app.openstory.downloads.blob.ChapterBlobKey
import app.openstory.downloads.blob.ChapterBlobNamespace
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
    ) = StorageReconciliationService(
        repository = repository,
        inventory = inventory,
        activeWriteWindowMillis = 500,
        now = { 1_000 },
    )

    private fun metadata(
        key: ChapterBlobKey,
        state: DownloadState? = null,
        updatedAt: Long = 900,
    ) = StorageMetadataEntry(key, state, updatedAt)

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
