package app.openstory.benchmark

import androidx.benchmark.macro.junit4.BaselineProfileRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.LargeTest
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@LargeTest
@RunWith(AndroidJUnit4::class)
class BaselineProfileGenerator {
    @get:Rule
    val baselineProfileRule = BaselineProfileRule()

    @Test
    fun startup() = baselineProfileRule.collect(
        packageName = HIKARI_PACKAGE,
        includeInStartupProfile = true,
    ) {
        pressHome()
        startHikari()
    }

    @Test
    fun criticalJourneys() = baselineProfileRule.collect(
        packageName = HIKARI_PACKAGE,
    ) {
        pressHome()
        startHikari()
        clickTag("navigation-library")
        clickTag("navigation-home")
        clickTag("navigation-discover")
        clickTag("discover-search")
        waitForTag("search-content")
        pressBackAndWait()

        benchmarkStoryTitle()?.let { storyTitle ->
            clickTag("navigation-library")
            clickText(storyTitle)
            waitForTag("story-overview-pull-refresh")
            clickTag("story-tab-sources")
            clickTag("story-tab-chapters")
            clickTag("story-tab-overview")
            clickTag("story-read")
            waitForTag("reader-content")
            repeat(10) { clickTag("reader-next") }
        }
    }
}
