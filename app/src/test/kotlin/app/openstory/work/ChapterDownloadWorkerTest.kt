package app.openstory.work

import app.openstory.common.id.ChapterReleaseId
import app.openstory.downloads.DownloadRunResult
import app.openstory.downloads.blob.ChapterBlobNamespace
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlinx.coroutines.test.runTest

class ChapterDownloadWorkerTest {
    @Test
    fun `worker delegates one stable release id and fingerprint`() = runTest {
        var received: app.openstory.downloads.blob.ChapterBlobKey? = null
        val decision = runChapterDownloadWork("release:1", "fingerprint") { key ->
            received = key
            DownloadRunResult.COMPLETED
        }
        assertEquals(ChapterReleaseId("release:1"), received?.releaseId)
        assertEquals(ChapterBlobNamespace.EXPLICIT_DOWNLOAD, received?.namespace)
        assertEquals(ChapterDownloadWorkDecision.SUCCESS, decision)
    }

    @Test
    fun `retryable download result requests retry`() = runTest {
        assertEquals(
            ChapterDownloadWorkDecision.RETRY,
            runChapterDownloadWork("release:1", "fingerprint") { DownloadRunResult.RETRY },
        )
    }

    @Test
    fun `invalid identity fails without delegation`() = runTest {
        var calls = 0
        val decision = runChapterDownloadWork(" ", " ") { calls += 1; DownloadRunResult.COMPLETED }
        assertEquals(ChapterDownloadWorkDecision.FAILURE, decision)
        assertEquals(0, calls)
    }
}
