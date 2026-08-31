package app.openstory.catalog.ui.state

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ContentStateContractTest {
    @Test
    fun pendingAndAuthoritativeEmptyAreDifferentStates() {
        val pending: ContentState<List<String>> = ContentState.Pending
        val empty: ContentState<List<String>> = ContentState.Ready(emptyList())

        assertIs<ContentState.Pending>(pending)
        assertEquals(emptyList(), assertIs<ContentState.Ready<List<String>>>(empty).value)
    }

    @Test
    fun refreshAttemptClearsOnlyPreviousRefreshFailure() {
        val oldFailure = CatalogUiFailure("refresh.old", retryable = true)
        val started = RefreshState(failure = oldFailure).startAttempt()

        assertTrue(started.inProgress)
        assertNull(started.failure)
    }

    @Test
    fun refreshSuccessLeavesRefreshFailureClear() {
        val completed = RefreshState(inProgress = true).completeSuccess()

        assertFalse(completed.inProgress)
        assertNull(completed.failure)
    }

    @Test
    fun refreshFailurePublishesOnlyTheCurrentAttemptFailure() {
        val failure = CatalogUiFailure("refresh.new", retryable = true)
        val completed = RefreshState(inProgress = true).completeFailure(failure)

        assertFalse(completed.inProgress)
        assertEquals(failure, completed.failure)
    }
}
