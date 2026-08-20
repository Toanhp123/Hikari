package app.openstory.catalog.metadata

import app.openstory.common.Clock
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class CatalogMetadataPolicyTest {
    @Test
    fun artworkIsFreshAtSevenDaysAndStaleAfterBoundary() {
        val clock = MutableClock(1_000_000_000L)
        val policy = CatalogMetadataPolicy(clock)
        val stamp = CatalogMetadataStamp(
            pluginVersion = "1.2.3",
            resolvedAtEpochMillis = clock.now - CatalogMetadataPolicy.ARTWORK_TTL_MILLIS,
        )

        assertTrue(policy.isFresh(CatalogMetadataLevel.Artwork, stamp, "1.2.3"))
        clock.now += 1
        assertFalse(policy.isFresh(CatalogMetadataLevel.Artwork, stamp, "1.2.3"))
    }

    @Test
    fun fullIsFreshAtTwentyFourHoursAndStaleAfterBoundary() {
        val clock = MutableClock(2_000_000_000L)
        val policy = CatalogMetadataPolicy(clock)
        val stamp = CatalogMetadataStamp(
            pluginVersion = "2.0.0",
            resolvedAtEpochMillis = clock.now - CatalogMetadataPolicy.FULL_TTL_MILLIS,
        )

        assertTrue(policy.isFresh(CatalogMetadataLevel.Full, stamp, "2.0.0"))
        clock.now += 1
        assertFalse(policy.isFresh(CatalogMetadataLevel.Full, stamp, "2.0.0"))
    }

    @Test
    fun pluginVersionMismatchIsStaleInsideTtl() {
        val clock = MutableClock(3_000_000_000L)
        val policy = CatalogMetadataPolicy(clock)
        val stamp = CatalogMetadataStamp("1", clock.now)

        assertFalse(policy.isFresh(CatalogMetadataLevel.Full, stamp, "2"))
    }

    @Test
    fun futureTimestampUsesZeroAge() {
        val clock = MutableClock(100L)
        val policy = CatalogMetadataPolicy(clock)
        val stamp = CatalogMetadataStamp("1", 200L)

        assertTrue(policy.isFresh(CatalogMetadataLevel.Artwork, stamp, "1"))
    }

    @Test
    fun retryCooldownExpiresAfterFiveMinutes() {
        val clock = MutableClock(10_000L)
        val policy = CatalogMetadataPolicy(clock)
        val recordedAt = clock.now

        assertTrue(policy.isRetryCooldownActive(recordedAt))
        clock.now += CatalogMetadataPolicy.AUTO_RETRY_COOLDOWN_MILLIS
        assertTrue(policy.isRetryCooldownActive(recordedAt))
        clock.now += 1
        assertFalse(policy.isRetryCooldownActive(recordedAt))
    }

    @Test
    fun summaryRejectsDetailsFreshnessCheck() {
        val clock = MutableClock(10_000L)
        val policy = CatalogMetadataPolicy(clock)
        val stamp = CatalogMetadataStamp("1", clock.now)

        assertFailsWith<IllegalArgumentException> {
            policy.isFresh(CatalogMetadataLevel.Summary, stamp, "1")
        }
    }

    private class MutableClock(
        var now: Long,
    ) : Clock {
        override fun nowEpochMillis(): Long = now
    }
}
