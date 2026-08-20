package app.openstory.benchmark

import androidx.benchmark.macro.BaselineProfileMode
import androidx.benchmark.macro.CompilationMode
import androidx.benchmark.macro.ExperimentalMetricApi
import androidx.benchmark.macro.FrameTimingMetric
import androidx.benchmark.macro.MemoryUsageMetric
import androidx.benchmark.macro.StartupMode
import androidx.benchmark.macro.StartupTimingMetric
import androidx.benchmark.macro.MacrobenchmarkScope
import androidx.benchmark.macro.junit4.MacrobenchmarkRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.LargeTest
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@LargeTest
@RunWith(AndroidJUnit4::class)
class HikariMacrobenchmark {
    @get:Rule
    val benchmarkRule = MacrobenchmarkRule()

    @Test
    fun coldStartup() {
        prepareBenchmarkFixture()
        benchmarkRule.measureRepeated(
            packageName = HIKARI_PACKAGE,
            metrics = listOf(StartupTimingMetric()),
            compilationMode = benchmarkCompilationMode,
            startupMode = StartupMode.COLD,
            iterations = 5,
            setupBlock = { pressHome() },
            measureBlock = { startHikari() },
        )
    }

    @Test
    fun homeLibraryHome() = measureNavigation {
        clickTag("navigation-library")
        clickTag("navigation-home")
    }

    @Test
    fun homeDiscoverHome() = measureNavigation {
        clickTag("navigation-discover")
        clickTag("navigation-home")
    }

    @Test
    fun homeDiscoverWarm() = measureNavigation(
        setup = {
            clickTag("navigation-discover")
            clickTag("navigation-home")
        },
    ) {
        clickTag("navigation-discover")
        clickTag("navigation-home")
    }

    @Test
    fun homeDiscoverLegacyTransitions() = measureNavigation(legacyNavigationTransitions = true) {
        clickTag("navigation-discover")
        clickTag("navigation-home")
    }

    @Test
    fun searchReopen() = measureNavigation(
        setup = { clickTag("navigation-discover") },
    ) {
        clickTag("discover-search")
        waitForTag("search-content")
        pressBackAndWait()
        clickTag("discover-search")
        waitForTag("search-content")
    }

    @Test
    fun storyTabs() = measureNavigation(
        setup = { openBenchmarkFixtureStory() },
    ) {
        clickTag("story-tab-sources")
        clickTag("story-tab-chapters")
        clickTag("story-tab-overview")
    }

    @Test
    fun storyTabSources() = measureStoryTab("story-tab-sources")

    @Test
    fun storyTabChapters() = measureStoryTab("story-tab-chapters")

    @Test
    fun readerNextTen() = measureNavigation(
        setup = {
            openBenchmarkFixtureStory()
            clickTag("story-read")
            waitForTag("reader-content")
        },
    ) {
        repeat(READER_NEXT_ACTION_COUNT) { clickTag("reader-next") }
    }

    @OptIn(ExperimentalMetricApi::class)
    @Test
    fun readerMemory() {
        benchmarkRule.measureRepeated(
            packageName = HIKARI_PACKAGE,
            metrics = listOf(MemoryUsageMetric(MemoryUsageMetric.Mode.Max)),
            compilationMode = benchmarkCompilationMode,
            startupMode = null,
            iterations = 5,
            setupBlock = {
                prepareBenchmarkFixture()
                killProcess()
                pressHome()
                startHikari()
                openBenchmarkFixtureStory()
                clickTag("story-read")
                waitForTag("reader-content")
            },
            measureBlock = {
                repeat(READER_NEXT_ACTION_COUNT) { clickTag("reader-next") }
            },
        )
    }

    @Test
    fun readerScrollLongChapter() = measureNavigation(
        setup = {
            openBenchmarkFixtureStory()
            clickTag("story-read")
            waitForTag("reader-content")
        },
    ) {
        swipeUpOnTag("reader-content", repetitions = SCROLL_SWIPE_COUNT)
    }

    @Test
    fun chaptersExpandAndScroll() = measureNavigation(
        setup = {
            openBenchmarkFixtureStory()
            clickTag("story-tab-chapters")
            waitForTag("chapter-list")
            clickTag("chapter-summary-first")
        },
    ) {
        swipeUpOnTag("chapter-list", repetitions = SCROLL_SWIPE_COUNT)
    }

    @Test
    fun chaptersScrollShadowEnabled() = measureChapterScroll(surfaceShadowsDisabled = false)

    @Test
    fun chaptersScrollShadowDisabled() = measureChapterScroll(surfaceShadowsDisabled = true)

    @Test
    fun libraryListScroll() = measureNavigation(
        setup = {
            clickTag("navigation-library")
            waitForTag("library-collection")
            clickTag("library-view-switch")
            waitForTag("library-collection")
        },
    ) {
        swipeUpOnTag("library-collection", repetitions = SCROLL_SWIPE_COUNT)
    }

    @Test
    fun discoverScroll() = measureNavigation(
        setup = {
            clickTag("navigation-discover")
            waitForTag("discover-list")
        },
    ) {
        swipeUpOnTag("discover-list", repetitions = SCROLL_SWIPE_COUNT)
    }

    @Test
    fun backdropEnabled() = measureNavigation(backdropDisabled = false) {
        clickTag("navigation-discover")
        clickTag("navigation-home")
    }

    @Test
    fun backdropDisabled() = measureNavigation(backdropDisabled = true) {
        clickTag("navigation-discover")
        clickTag("navigation-home")
    }

    private fun measureStoryTab(tabTag: String) = measureNavigation(
        setup = { openBenchmarkFixtureStory() },
    ) {
        repeat(STORY_TAB_MEASUREMENT_CYCLE_COUNT) {
            clickTag(tabTag)
            clickTag("story-tab-overview")
        }
    }

    private fun measureChapterScroll(surfaceShadowsDisabled: Boolean) = measureNavigation(
        surfaceShadowsDisabled = surfaceShadowsDisabled,
        setup = {
            openBenchmarkFixtureStory()
            clickTag("story-tab-chapters")
            waitForTag("chapter-list")
            clickTag("chapter-summary-first")
        },
    ) {
        swipeUpOnTag("chapter-list", repetitions = SCROLL_SWIPE_COUNT)
    }

    private fun measureNavigation(
        backdropDisabled: Boolean = false,
        surfaceShadowsDisabled: Boolean = false,
        legacyNavigationTransitions: Boolean = false,
        setup: MacrobenchmarkScope.() -> Unit = {},
        measure: MacrobenchmarkScope.() -> Unit,
    ) {
        benchmarkRule.measureRepeated(
            packageName = HIKARI_PACKAGE,
            metrics = listOf(FrameTimingMetric()),
            compilationMode = benchmarkCompilationMode,
            startupMode = null,
            iterations = 5,
            setupBlock = {
                prepareBenchmarkFixture()
                killProcess()
                pressHome()
                startHikari(
                    backdropDisabled = backdropDisabled,
                    surfaceShadowsDisabled = surfaceShadowsDisabled,
                    legacyNavigationTransitions = legacyNavigationTransitions,
                )
                setup()
            },
            measureBlock = measure,
        )
    }

    private val benchmarkCompilationMode = CompilationMode.Partial(
        baselineProfileMode = BaselineProfileMode.Require,
    )

    private companion object {
        const val SCROLL_SWIPE_COUNT = 6
        const val STORY_TAB_MEASUREMENT_CYCLE_COUNT = 3
    }
}
