package app.openstory.reader.routing

import app.openstory.common.id.PluginId
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.async
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

@OptIn(ExperimentalCoroutinesApi::class)
class ContentSourceExecutionLaneTest {
    private val sourceId = PluginId("source")

    @Test
    fun foregroundPreemptsOnlyActiveReaderPrefetch() = runTest {
        val lane = ContentSourceExecutionLane()
        val prefetchEntered = CompletableDeferred<Unit>()
        val prefetch = async {
            try {
                lane.withSource(sourceId, ContentSourceWorkPriority.PREFETCH) {
                    prefetchEntered.complete(Unit)
                    CompletableDeferred<Unit>().await()
                }
                null
            } catch (cancelled: CancellationException) {
                cancelled
            }
        }
        prefetchEntered.await()

        val foreground = async {
            lane.withSource(sourceId, ContentSourceWorkPriority.FOREGROUND) { "foreground" }
        }

        assertEquals("foreground", foreground.await())
        assertIs<ReaderPrefetchPreemptedException>(prefetch.await())
    }

    @Test
    fun activeUserWorkIsNotPreemptedAndQueueUsesForegroundUserPrefetchOrder() = runTest {
        val lane = ContentSourceExecutionLane()
        val entered = CompletableDeferred<Unit>()
        val release = CompletableDeferred<Unit>()
        val order = mutableListOf<String>()
        val activeUser = launch {
            lane.withSource(sourceId, ContentSourceWorkPriority.USER_WORK) {
                entered.complete(Unit)
                release.await()
                order += "active-user"
            }
        }
        entered.await()
        val prefetch = launch {
            lane.withSource(sourceId, ContentSourceWorkPriority.PREFETCH) { order += "prefetch" }
        }
        val queuedUser = launch {
            lane.withSource(sourceId, ContentSourceWorkPriority.USER_WORK) { order += "queued-user" }
        }
        val foreground = launch {
            lane.withSource(sourceId, ContentSourceWorkPriority.FOREGROUND) { order += "foreground" }
        }
        runCurrent()

        release.complete(Unit)
        activeUser.join()
        foreground.join()
        queuedUser.join()
        prefetch.join()

        assertEquals(listOf("active-user", "foreground", "queued-user", "prefetch"), order)
    }
}
