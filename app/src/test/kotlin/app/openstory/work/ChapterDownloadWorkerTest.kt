package app.openstory.work

import app.openstory.common.id.ChapterReleaseId
import app.openstory.downloads.DownloadRunResult
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlinx.coroutines.test.runTest

class ChapterDownloadWorkerTest {
    @Test
    fun `worker delegates one stable release id and fingerprint`() = runTest {
        var received: ChapterReleaseId? = null
        val decision = runChapterDownloadWork("release:1") { releaseId ->
            received = releaseId
            DownloadRunResult.COMPLETED
        }
        assertEquals(ChapterReleaseId("release:1"), received)
        assertEquals(ChapterDownloadWorkDecision.SUCCESS, decision)
    }

    @Test
    fun `retryable download result requests retry`() = runTest {
        assertEquals(
            ChapterDownloadWorkDecision.RETRY,
            runChapterDownloadWork("release:1") { DownloadRunResult.RETRY },
        )
    }

    @Test
    fun `invalid identity fails without delegation`() = runTest {
        var calls = 0
        val decision = runChapterDownloadWork(" ") { calls += 1; DownloadRunResult.COMPLETED }
        assertEquals(ChapterDownloadWorkDecision.FAILURE, decision)
        assertEquals(0, calls)
    }
}
