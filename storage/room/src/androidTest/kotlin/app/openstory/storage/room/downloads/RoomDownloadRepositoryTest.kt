package app.openstory.storage.room.downloads

import android.content.Context
import androidx.room.Room
import androidx.room.RoomDatabase
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
import kotlin.test.assertTrue
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.flow.first
import org.junit.runner.RunWith
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.Executor

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

    @Test
    fun underQuotaSnapshotSkipsAutomaticCacheEntryMaterialization() = runTest {
        val queries = CopyOnWriteArrayList<String>()
        val queryDatabase = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext<Context>(),
            OpenStoryDatabase::class.java,
        ).setQueryCallback(
            RoomDatabase.QueryCallback { sql, _ -> queries += sql },
            Executor(Runnable::run),
        ).build()
        try {
            val queryRepository = RoomDownloadRepository(queryDatabase)
            queryRepository.upsert(
                entry(key(ChapterBlobNamespace.AUTOMATIC_CACHE, "cache-fast-path")).copy(sizeBytes = 4),
            )
            queries.clear()

            val snapshot = queryRepository.quotaSnapshot(10)

            assertEquals(4, snapshot.usageBytes)
            assertTrue(snapshot.entriesByLru.isEmpty())
            assertEquals(1, queries.count { sql -> sql.contains("SUM(size_bytes)", ignoreCase = true) })
            assertTrue(
                queries.none { sql ->
                    sql.contains("SELECT * FROM chapter_storage_entries", ignoreCase = true) &&
                        sql.contains("AUTOMATIC_CACHE", ignoreCase = true)
                },
            )
        } finally {
            queryDatabase.close()
        }
    }

    @Test
    fun readerMetadataInspectionUsesOneBoundedQueryForThirtyTwoReleases() = runTest {
        val queries = CopyOnWriteArrayList<String>()
        val queryDatabase = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext<Context>(),
            OpenStoryDatabase::class.java,
        ).setQueryCallback(
            RoomDatabase.QueryCallback { sql, _ -> queries += sql },
            Executor(Runnable::run),
        ).build()
        try {
            val queryRepository = RoomDownloadRepository(queryDatabase)
            val releaseIds = (0 until 32).mapTo(linkedSetOf()) { index ->
                ChapterReleaseId("reader-release-$index")
            }
            releaseIds.forEach { releaseId ->
                queryRepository.upsert(
                    entry(
                        ChapterBlobKey(
                            ChapterBlobNamespace.AUTOMATIC_CACHE,
                            releaseId,
                            "fingerprint-${releaseId.value}",
                        ),
                    ),
                )
            }
            queries.clear()

            val metadata = queryRepository.entriesFor(releaseIds)

            assertEquals(32, metadata.size)
            assertEquals(releaseIds, metadata.mapTo(linkedSetOf()) { it.releaseId })
            val boundedSelects = queries.filter { sql ->
                sql.contains("SELECT * FROM chapter_storage_entries", ignoreCase = true) &&
                    sql.contains("chapter_release_id IN", ignoreCase = true) &&
                    sql.contains("EXPLICIT_DOWNLOAD", ignoreCase = true) &&
                    sql.contains("AUTOMATIC_CACHE", ignoreCase = true)
            }
            assertEquals(1, boundedSelects.size)
            val storageEntrySelects = queries.filter { sql ->
                sql.trimStart().startsWith("SELECT", ignoreCase = true) &&
                    sql.contains("FROM chapter_storage_entries", ignoreCase = true)
            }
            assertEquals(
                1,
                storageEntrySelects.size,
                "Reader metadata inspection must issue one storage metadata SELECT; " +
                    "Room-internal bookkeeping queries are outside this contract. Queries=$queries",
            )
        } finally {
            queryDatabase.close()
        }
    }

    @Test
    fun quotaSnapshotIgnoresExplicitDownloadsAndKeepsLruOrder() = runTest {
        val oldest = key(ChapterBlobNamespace.AUTOMATIC_CACHE, "cache-old")
        val newest = key(ChapterBlobNamespace.AUTOMATIC_CACHE, "cache-new")
        repository.upsert(entry(oldest).copy(sizeBytes = 4, lastAccessedAtEpochMillis = 1))
        repository.upsert(entry(newest).copy(sizeBytes = 6, lastAccessedAtEpochMillis = 2))
        repository.save(download("explicit", updatedAt = 3, state = DownloadState.COMPLETED))

        val snapshot = repository.quotaSnapshot(0)

        assertEquals(10, snapshot.usageBytes)
        assertEquals(listOf(oldest, newest), snapshot.entriesByLru.map(CacheEntry::key))
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
