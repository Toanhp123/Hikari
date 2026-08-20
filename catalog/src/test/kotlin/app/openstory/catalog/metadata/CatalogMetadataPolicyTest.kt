package app.openstory.catalog.metadata

import app.openstory.common.Clock
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class CatalogMetadataPolicyTest {
    private val clock = MutableClock(1_000_000_000L)
    private val policy = CatalogMetadataPolicy(clock)

    @Test
    fun fullIsFreshAtTwentyFourHoursAndStaleAfterBoundary() {
        val stamp = CatalogMetadataStamp(
            pluginVersion = "1.2.3",
            resolvedAtEpochMillis = clock.now - CatalogMetadataPolicy.FULL_TTL_MILLIS,
        )

        assertTrue(policy.isFresh(CatalogMetadataLevel.Full, stamp, "1.2.3"))
        clock.now += 1
        assertFalse(policy.isFresh(CatalogMetadataLevel.Full, stamp, "1.2.3"))
    }

    @Test
    fun pluginVersionMismatchIsImmediatelyStale() {
        val stamp = CatalogMetadataStamp("1.2.3", clock.now)

        assertFalse(policy.isFresh(CatalogMetadataLevel.Full, stamp, "2.0.0"))
    }

    @Test
    fun futureTimestampUsesZeroAge() {
        val stamp = CatalogMetadataStamp("1", clock.now + 10_000L)

        assertTrue(policy.isFresh(CatalogMetadataLevel.Full, stamp, "1"))
    }

    @Test
    fun automaticRetryCooldownIsActiveThroughBoundary() {
        val failedAt = clock.now

        assertTrue(policy.isRetryCooldownActive(failedAt))
        clock.now += CatalogMetadataPolicy.AUTO_RETRY_COOLDOWN_MILLIS - 1
        assertTrue(policy.isRetryCooldownActive(failedAt))
        clock.now += 1
        assertTrue(policy.isRetryCooldownActive(failedAt))
        clock.now += 1
        assertFalse(policy.isRetryCooldownActive(failedAt))
    }

    @Test
    fun summaryDoesNotUseDetailsFreshnessPolicy() {
        val stamp = CatalogMetadataStamp("1", clock.now)

        assertFailsWith<IllegalArgumentException> {
            policy.isFresh(CatalogMetadataLevel.Summary, stamp, "1")
        }
    }

    private class MutableClock(var now: Long) : Clock {
        override fun nowEpochMillis(): Long = now
    }
}
