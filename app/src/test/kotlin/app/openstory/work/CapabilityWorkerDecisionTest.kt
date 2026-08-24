package app.openstory.work

import app.openstory.chapters.sync.ChapterSyncFailure
import app.openstory.chapters.sync.ChapterSyncReport
import app.openstory.common.id.PluginId
import app.openstory.downloads.DownloadRunResult
import app.openstory.library.mapping.ContentMappingSearchFailure
import app.openstory.library.mapping.ContentMappingSearchReport
import app.openstory.library.mapping.ContentMappingSearchStage
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlinx.coroutines.test.runTest

class CapabilityWorkerDecisionTest {
    @Test
    fun retryableGlobalMappingFailureRequestsRetry() = runTest {
        val decision = runLibraryMappingWork("story:one") {
            ContentMappingSearchReport(
                stage = ContentMappingSearchStage.ALL,
                searchedPluginIds = emptyList(),
                queryVariants = emptyList(),
                candidates = emptyList(),
                failures = listOf(ContentMappingSearchFailure(null, "catalog.unavailable", true)),
            )
        }

        assertEquals(LibraryMappingWorkDecision.RETRY, decision)
    }

    @Test
    fun nonRetryableSourceChapterFailureDoesNotRetryGlobalWork() = runTest {
        val decision = runInitialChapterSyncWork("story:one") {
            ChapterSyncReport.Failure(
                listOf(ChapterSyncFailure(PluginId("org.example.source"), "auth.required", false)),
            )
        }

        assertEquals(InitialChapterSyncWorkDecision.SUCCESS, decision)
    }

    @Test
    fun cancelledDownloadCompletesWithoutRetry() = runTest {
        val decision = runChapterDownloadWork("release:one") { DownloadRunResult.CANCELLED }

        assertEquals(ChapterDownloadWorkDecision.SUCCESS, decision)
    }
}
