package app.openstory.common.retention

import app.openstory.common.navigation.RouteEntryId
import app.openstory.common.navigation.RouteLifecycle
import org.junit.Assert.assertEquals
import org.junit.Test

class RetainedPayloadBudgetTest {
    @Test
    fun exceedingBudgetCompactsOldestInactiveOwnersDeterministically() {
        val budget = ProcessRetainedPayloadBudget(maxUnits = 240)
        val active = owner("active", units = 100, RouteLifecycle.ACTIVE, recency = 1)
        val older = owner("older", units = 30, RouteLifecycle.RETAINED, recency = 2)
        val sameRecencyB = owner("same-b", units = 40, RouteLifecycle.RETAINED, recency = 3)
        val sameRecencyA = owner("same-a", units = 50, RouteLifecycle.RETAINED, recency = 3)
        val newest = owner("newest", units = 100, RouteLifecycle.RETAINED, recency = 4)

        assertEquals(RetentionDecision(emptyList()), budget.update(active))
        assertEquals(RetentionDecision(emptyList()), budget.update(older))
        assertEquals(RetentionDecision(emptyList()), budget.update(sameRecencyB))
        assertEquals(RetentionDecision(emptyList()), budget.update(sameRecencyA))
        assertEquals(
            RetentionDecision(
                listOf(RouteEntryId.from("older"), RouteEntryId.from("same-a")),
            ),
            budget.update(newest),
        )
    }

    @Test
    fun releasedAndPreviouslyCompactedOwnersNoLongerConsumeBudget() {
        val budget = ProcessRetainedPayloadBudget(maxUnits = 10)
        val first = owner("first", units = 8, RouteLifecycle.RETAINED, recency = 1)
        val second = owner("second", units = 8, RouteLifecycle.RETAINED, recency = 2)

        assertEquals(RetentionDecision(emptyList()), budget.update(first))
        assertEquals(
            RetentionDecision(listOf(RouteEntryId.from("first"))),
            budget.update(second),
        )
        assertEquals(RetentionDecision(emptyList()), budget.update(second.copy(recency = 3)))
        budget.release(second.entryId)
        assertEquals(RetentionDecision(emptyList()), budget.update(first.copy(recency = 4)))
    }

    @Test(expected = IllegalArgumentException::class)
    fun negativeOwnerUnitsAreRejected() {
        ProcessRetainedPayloadBudget(maxUnits = 10).update(
            owner("invalid", units = -1, RouteLifecycle.RETAINED, recency = 1),
        )
    }

    private fun owner(
        id: String,
        units: Int,
        lifecycle: RouteLifecycle,
        recency: Long,
    ) = RetainedPayloadOwner(
        entryId = RouteEntryId.from(id),
        units = units,
        lifecycle = lifecycle,
        recency = recency,
    )
}
