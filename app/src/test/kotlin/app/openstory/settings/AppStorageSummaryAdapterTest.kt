package app.openstory.settings

import app.openstory.cache.AutomaticCacheTestFixture
import app.openstory.cache.FakeSettingsRepository
import app.openstory.common.id.ChapterReleaseId
import app.openstory.downloads.DownloadRecord
import app.openstory.downloads.DownloadRepository
import app.openstory.downloads.DownloadState
import app.openstory.downloads.blob.ChapterBlobKey
import app.openstory.downloads.blob.ChapterBlobNamespace
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals

@OptIn(ExperimentalCoroutinesApi::class)
class AppStorageSummaryAdapterTest {
    @Test
    fun `summary combines completed downloads with unified document and image cache bytes`() = runTest {
        val fixture = AutomaticCacheTestFixture(initialQuotaBytes = 64L, reconciliationScope = backgroundScope)
        fixture.addDocument(ChapterReleaseId("release:document"), bytes = 30L)
        fixture.addAsset(id = 1, bytes = 40L)
        val downloads = MutableStateFlow(
            listOf(
                download("completed", DownloadState.COMPLETED, bytes = 50L),
                download("failed", DownloadState.FAILED, bytes = 500L),
            ),
        )
        val settings = MutableStateFlow(
            SettingsDefaults().defaultSettings().copy(automaticCacheQuotaBytes = 64L),
        )
        val adapter = AppStorageSummaryAdapter(
            fixture.coordinator,
            FakeDownloadRepository(downloads),
            FakeSettingsRepository(settings),
        )

        val summary = adapter.observe().first()

        assertEquals(120L, summary.totalBytes)
        assertEquals(70L, summary.automaticCacheBytes)
        assertEquals(64L, summary.automaticCacheQuotaBytes)
    }

    private fun download(id: String, state: DownloadState, bytes: Long) = DownloadRecord(
        key = ChapterBlobKey(
            ChapterBlobNamespace.EXPLICIT_DOWNLOAD,
            ChapterReleaseId("release:$id"),
            "fingerprint:$id",
        ),
        state = state,
        sizeBytes = bytes,
        updatedAtEpochMillis = 0L,
    )
}

private class FakeDownloadRepository(
    private val values: MutableStateFlow<List<DownloadRecord>>,
) : DownloadRepository {
    override fun observeAll(): Flow<List<DownloadRecord>> = values
    override suspend fun find(releaseId: ChapterReleaseId): DownloadRecord? =
        values.value.firstOrNull { it.key.releaseId == releaseId }
    override fun observe(releaseId: ChapterReleaseId): Flow<DownloadRecord?> =
        values.map { rows -> rows.firstOrNull { it.key.releaseId == releaseId } }
    override suspend fun save(record: DownloadRecord) {
        values.value = values.value.filterNot { it.key.releaseId == record.key.releaseId } + record
    }
}
