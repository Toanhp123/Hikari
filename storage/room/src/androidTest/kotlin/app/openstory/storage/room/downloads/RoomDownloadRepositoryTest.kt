package app.openstory.storage.room.downloads

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.openstory.common.id.ChapterReleaseId
import app.openstory.downloads.blob.BlobChecksum
import app.openstory.downloads.blob.ChapterBlobKey
import app.openstory.downloads.blob.ChapterBlobNamespace
import app.openstory.downloads.cache.CacheEntry
import app.openstory.downloads.DownloadState
import app.openstory.downloads.DownloadRecord
import app.openstory.downloads.reconcile.StorageDownloadFailure
import app.openstory.downloads.reconcile.StorageMetadataRepairPlan
import app.openstory.storage.room.OpenStoryDatabase
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.flow.first
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class RoomDownloadRepositoryTest {
    private lateinit var database: OpenStoryDatabase
    private lateinit var repository: RoomDownloadRepository

    @BeforeTest
    fun setUp() {
        database = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext<Context>(),
            OpenStoryDatabase::class.java,
        ).build()
        repository = RoomDownloadRepository(database)
    }

    @AfterTest
    fun tearDown() {
        database.close()
    }

    @Test
    fun evictionTransactionRemovesAutomaticMetadataButNeverExplicitDownloads() = runTest {
        val automatic = key(ChapterBlobNamespace.AUTOMATIC_CACHE, "automatic")
        val explicit = key(ChapterBlobNamespace.EXPLICIT_DOWNLOAD, "explicit")
        repository.upsert(entry(automatic))
        database.downloadDao().upsert(
            ChapterStorageEntryEntity(
                namespace = explicit.namespace.name,
                chapterReleaseId = explicit.releaseId.value,
                contentFingerprint = explicit.contentFingerprint,
                checksum = BlobChecksum.sha256("explicit".encodeToByteArray()).value,
                sizeBytes = 8,
                lastAccessedAtEpochMillis = 1,
                pinned = true,
                current = false,
                downloadState = "COMPLETED",
                failureReason = null,
                attempt = 1,
                updatedAtEpochMillis = 1,
            ),
        )

        val removed = repository.commitEviction(listOf(explicit, automatic))

        assertEquals(listOf(automatic), removed)
        assertEquals(listOf(explicit), repository.entries().map(CacheEntry::key))
        assertNotNull(
            database.downloadDao().find(
                explicit.namespace.name,
                explicit.releaseId.value,
                explicit.contentFingerprint,
            ),
        )
    }

    @Test
    fun reconciliationAtomicallyRemovesMissingCacheAndFailsMissingDownload() = runTest {
        val automatic = key(ChapterBlobNamespace.AUTOMATIC_CACHE, "automatic-reconcile")
        val explicit = key(ChapterBlobNamespace.EXPLICIT_DOWNLOAD, "explicit-reconcile")
        repository.upsert(entry(automatic))
        database.downloadDao().upsert(
            ChapterStorageEntryEntity(
                namespace = explicit.namespace.name,
                chapterReleaseId = explicit.releaseId.value,
                contentFingerprint = explicit.contentFingerprint,
                checksum = BlobChecksum.sha256("explicit".encodeToByteArray()).value,
                sizeBytes = 8,
                lastAccessedAtEpochMillis = 1,
                pinned = true,
                current = false,
                downloadState = DownloadState.COMPLETED.name,
                failureReason = null,
                attempt = 1,
                updatedAtEpochMillis = 1,
            ),
        )

        repository.commit(
            StorageMetadataRepairPlan(
                removedMetadata = listOf(automatic),
                failedDownloads = listOf(
                    StorageDownloadFailure(explicit, "download.integrity_missing"),
                ),
            ),
            updatedAtEpochMillis = 10,
        )

        assertNull(
            database.downloadDao().find(
                automatic.namespace.name,
                automatic.releaseId.value,
                automatic.contentFingerprint,
            ),
        )
        val failed = repository.find(explicit.releaseId)
        assertEquals(DownloadState.FAILED, failed?.state)
        assertEquals("download.integrity_missing", failed?.failureReason)
        assertNull(failed?.checksum)
    }

    @Test
    fun completedCountProjectsOnlyCompletedExplicitDownloads() = runTest {
        repository.save(download("completed", updatedAt = 200, state = DownloadState.COMPLETED))
        repository.save(download("queued", updatedAt = 100, state = DownloadState.QUEUED))
        repository.upsert(entry(key(ChapterBlobNamespace.AUTOMATIC_CACHE, "cache-only")))

        assertEquals(1, repository.observeCompletedCount().first())
    }

    @Test
    fun observeAllOrdersDownloadsByUpdateDescendingThenReleaseIdentity() = runTest {
        repository.save(download("release-z", updatedAt = 200))
        repository.save(download("release-a", updatedAt = 200))
        repository.save(download("release-old", updatedAt = 100))
        repository.upsert(entry(key(ChapterBlobNamespace.AUTOMATIC_CACHE, "cache-only")))

        val observed = repository.observeAll().first()

        assertEquals(
            listOf("release-a", "release-z", "release-old"),
            observed.map { it.key.releaseId.value },
        )
    }

    private fun key(namespace: ChapterBlobNamespace, id: String) = ChapterBlobKey(
        namespace,
        ChapterReleaseId(id),
        "fingerprint-$id",
    )

    private fun entry(key: ChapterBlobKey) = CacheEntry(
        key = key,
        checksum = BlobChecksum.sha256(key.releaseId.value.encodeToByteArray()),
        sizeBytes = 8,
        lastAccessedAtEpochMillis = 1,
    )

    private fun download(
        id: String,
        updatedAt: Long,
        state: DownloadState = DownloadState.QUEUED,
    ) = DownloadRecord(
        key = key(ChapterBlobNamespace.EXPLICIT_DOWNLOAD, id),
        state = state,
        updatedAtEpochMillis = updatedAt,
    )
}
