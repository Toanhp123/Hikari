package app.openstory.benchmark

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
            compilationMode = CompilationMode.DEFAULT,
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
            compilationMode = CompilationMode.DEFAULT,
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
    fun backdropEnabled() = measureNavigation(backdropDisabled = false) {
        clickTag("navigation-discover")
        clickTag("navigation-home")
    }

    @Test
    fun backdropDisabled() = measureNavigation(backdropDisabled = true) {
        clickTag("navigation-discover")
        clickTag("navigation-home")
    }

    private fun measureNavigation(
        backdropDisabled: Boolean = false,
        setup: MacrobenchmarkScope.() -> Unit = {},
        measure: MacrobenchmarkScope.() -> Unit,
    ) {
        benchmarkRule.measureRepeated(
            packageName = HIKARI_PACKAGE,
            metrics = listOf(FrameTimingMetric()),
            compilationMode = CompilationMode.DEFAULT,
            startupMode = null,
            iterations = 5,
            setupBlock = {
                prepareBenchmarkFixture()
                killProcess()
                pressHome()
                startHikari(backdropDisabled)
                setup()
            },
            measureBlock = measure,
        )
    }
}
