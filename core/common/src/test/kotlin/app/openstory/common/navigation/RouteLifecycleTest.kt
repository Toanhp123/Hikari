package app.openstory.common.navigation

import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

class RouteLifecycleTest {
    @Test
    fun routeLifecycleHasAllThreeRequiredStates() {
        assertEquals(
            setOf("ACTIVE", "RETAINED", "RELEASED"),
            RouteLifecycle.entries.map { it.name }.toSet(),
        )
    }

    @Test
    fun routeLifecycleChangeHoldsEntryIdAndLifecycle() {
        val entryId = RouteEntryId.from("entry-1")
        val change = RouteLifecycleChange(entryId, RouteLifecycle.ACTIVE)
        assertEquals(entryId, change.entryId)
        assertEquals(RouteLifecycle.ACTIVE, change.lifecycle)
    }

    @Test
    fun routeLifecycleSourceEmitsChanges() = runTest {
        val flow = MutableSharedFlow<RouteLifecycleChange>()
        val source = object : RouteLifecycleSource {
            override val changes = flow
        }
        val entryId = RouteEntryId.from("entry-42")
        val expected = RouteLifecycleChange(entryId, RouteLifecycle.RETAINED)

        val job = backgroundScope.launch {
            flow.emit(expected)
        }
        val actual = source.changes.first()
        assertEquals(expected, actual)
    }
}
