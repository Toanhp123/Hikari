package app.openstory.benchmark

import android.graphics.Rect
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.SmallTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@SmallTest
@RunWith(AndroidJUnit4::class)
class HikariBenchmarkDriverTest {
    @Test
    fun boundedScrollStopsImmediatelyOnceTargetBecomesVisible() {
        var visibilityChecks = 0
        var scrolls = 0
        val found = scrollUntilVisible(
            maxAttempts = 12,
            isVisible = { visibilityChecks++ > 0 },
            scroll = { scrolls++ },
        )

        assertTrue(found)
        assertEquals(1, scrolls)
    }

    @Test
    fun discoverSwipeUsesTheBaselineInsetAndDirection() {
        val down = discoverSwipeCoordinates(Rect(10, 100, 210, 1100), towardEnd = true)
        val up = discoverSwipeCoordinates(Rect(10, 100, 210, 1100), towardEnd = false)

        assertEquals("SwipeCoordinates(x=110, startY=900, endY=300)", down.toString())
        assertEquals("SwipeCoordinates(x=110, startY=300, endY=900)", up.toString())
    }

    @Test
    fun frameFixtureClearsDataOnlyForTheFirstPreparation() {
        val tracker = FrameFixturePreparationTracker()

        assertEquals(true, tracker.shouldClearData())
        assertEquals(false, tracker.shouldClearData())
        assertEquals(false, tracker.shouldClearData())
    }
}
