package app.openstory.benchmark

import androidx.benchmark.macro.FrameTimingMetric
import androidx.benchmark.macro.ExperimentalMetricApi
import androidx.benchmark.macro.StartupMode
import androidx.benchmark.macro.StartupTimingMetric
import androidx.benchmark.macro.junit4.MacrobenchmarkRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.LargeTest
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@LargeTest
@OptIn(ExperimentalMetricApi::class)
@RunWith(AndroidJUnit4::class)
class HikariMacrobenchmark {
    @get:Rule
    val benchmarkRule = MacrobenchmarkRule()

    @Test
    fun coldFreshInstall() {
        benchmarkRule.measureRepeated(
            packageName = HIKARI_PACKAGE,
            metrics = listOf(StartupTimingMetric()),
            compilationMode = benchmarkCompilationMode,
            startupMode = StartupMode.COLD,
            iterations = 5,
            setupBlock = {
                prepareFreshInstall()
                pressHome()
            },
            measureBlock = {
                startHikariAndWait(FIRST_RUN_TAG)
                check(benchmarkDiagnostic("activation") == 0)
                check(benchmarkDiagnostic("storage") == 0)
                check(benchmarkDiagnostic("acquisition") == 0)
                check(benchmarkDiagnostic("imageSessions") == 0)
                check(benchmarkDiagnostic("transport") == 0)
            },
        )
    }

    @Test
    fun coldReturningDiscover() {
        benchmarkRule.measureRepeated(
            packageName = HIKARI_PACKAGE,
            metrics = returningStartupMetrics(),
            compilationMode = benchmarkCompilationMode,
            startupMode = StartupMode.COLD,
            iterations = 5,
            setupBlock = {
                prepareReturningLaunch()
                pressHome()
            },
            measureBlock = {
                startHikariAndWait(DISCOVER_TAG)
                check(benchmarkDiagnostic("acquisition") == 0)
                check(benchmarkDiagnostic("discoverQueries") == 1)
            },
        )
    }

    @Test
    fun multiSectionDiscoverScroll() = measureDiscoverFrames(benchmarkRule) {
        scrollDiscoverToEndAndBack()
    }

    @Test
    fun openStoryMemoryHit() {
        var discoverDecodeCount = 0
        measureDiscoverFrames(
            rule = benchmarkRule,
            includeStoryTrace = true,
            setup = {
                waitForBenchmarkDiagnosticAtLeast("successfulDecodes", 1)
                waitForBenchmarkDiagnostic("coverJobs", 0)
                discoverDecodeCount = benchmarkDiagnostic("successfulDecodes")
                resetTransportDiagnostic()
            },
        ) {
            openFirstStory()
            check(benchmarkDiagnostic("transport") == 0)
            check(benchmarkDiagnostic("successfulDecodes") == discoverDecodeCount)
            check(benchmarkDiagnostic("storyQueries") in MIN_STORY_QUERIES..MAX_STORY_QUERIES)
        }
    }

    @Test
    fun openStoryDiskHit() {
        var discoverDecodeCount = 0
        measureDiscoverFrames(
            rule = benchmarkRule,
            preparation = BenchmarkPreparation.DISK_HIT,
            includeStoryTrace = true,
            setup = {
                waitForBenchmarkDiagnosticAtLeast("successfulDecodes", 1)
                waitForBenchmarkDiagnostic("coverJobs", 0)
                discoverDecodeCount = benchmarkDiagnostic("successfulDecodes")
                resetTransportDiagnostic()
                trimHikariDecodedMemory()
            },
        ) {
            openFirstStory()
            check(benchmarkDiagnostic("transport") == 0)
            check(benchmarkDiagnostic("successfulDecodes") > discoverDecodeCount)
            check(benchmarkDiagnostic("storyQueries") in MIN_STORY_QUERIES..MAX_STORY_QUERIES)
            assertDecodeEvidence()
        }
    }

    @Test
    fun storyBackToDiscover() {
        benchmarkRule.measureRepeated(
            packageName = HIKARI_PACKAGE,
            metrics = listOf(FrameTimingMetric()),
            compilationMode = benchmarkCompilationMode,
            iterations = 5,
            setupBlock = {
                prepareReturningLaunch()
                startHikariAndWait(DISCOVER_TAG)
                openFirstStory()
            },
            measureBlock = { backToDiscover() },
        )
    }

    @Test
    fun persistedEmptyReturningDiscover() {
        benchmarkRule.measureRepeated(
            packageName = HIKARI_PACKAGE,
            metrics = persistedStartupMetrics(),
            compilationMode = benchmarkCompilationMode,
            startupMode = StartupMode.COLD,
            iterations = 5,
            setupBlock = {
                prepareReturningLaunch(BenchmarkPreparation.PERSISTED_EMPTY)
                pressHome()
            },
            measureBlock = {
                startHikariAndWait(DISCOVER_TAG)
                check(benchmarkDiagnostic("acquisition") == 0)
                check(benchmarkDiagnostic("transport") == 0)
                check(benchmarkDiagnostic("discoverQueries") == 1)
            },
        )
    }

    @Test
    fun agedStorageReturningDiscover() {
        benchmarkRule.measureRepeated(
            packageName = HIKARI_PACKAGE,
            metrics = returningStartupMetrics(),
            compilationMode = benchmarkCompilationMode,
            startupMode = StartupMode.COLD,
            iterations = 5,
            setupBlock = {
                prepareReturningLaunch(BenchmarkPreparation.AGED_STORAGE)
                pressHome()
            },
            measureBlock = {
                startHikariAndWait(DISCOVER_TAG)
                check(benchmarkDiagnostic("acquisition") == 0)
                check(benchmarkDiagnostic("discoverQueries") == 1)
                check(benchmarkDiagnostic("agedRows") == EXPECTED_AGED_ROWS)
                check(benchmarkDiagnostic("maxDiscoverTouched") <= MAX_DISCOVER_TOUCHED_STORIES)
                check(benchmarkDiagnostic("maxReleaseTouched") <= MAX_RELEASE_TOUCHED_STORIES)
            },
        )
    }

    @Test
    fun longBrowseCacheStability() = measureDiscoverFrames(benchmarkRule) {
        longBrowseCycle()
        benchmarkDevice().pressHome()
        waitForBenchmarkDiagnostic("coverDemands", 0)
        waitForBenchmarkDiagnostic("coverJobs", 0)
        waitForBenchmarkDiagnostic("runtimeWork", 0)
        waitForBenchmarkDiagnostic("storyPins", 0)
        waitForBenchmarkDiagnostic("storyCollectors", 0)
        waitForBenchmarkDiagnostic("discoverCollectors", 0)
        check(benchmarkDiagnostic("peakCoverJobs") <= MAX_PEAK_COVER_JOBS)
        check(benchmarkDiagnostic("peakDecodedBytes") <= MAX_PEAK_DECODED_BYTES)
        check(benchmarkDiagnostic("peakEncodedBytes") <= MAX_PEAK_ENCODED_BYTES)
        check(benchmarkDiagnostic("maxDiscoverTouched") <= MAX_DISCOVER_TOUCHED_STORIES)
        check(benchmarkDiagnostic("maxReleaseTouched") <= MAX_RELEASE_TOUCHED_STORIES)
        assertDecodeEvidence()
    }

    private companion object {
        const val MIN_STORY_QUERIES = 1
        const val MAX_STORY_QUERIES = 4
        const val EXPECTED_AGED_ROWS = 5_000
        const val MAX_PEAK_COVER_JOBS = 8
        const val MAX_PEAK_DECODED_BYTES = 32 * 1_024 * 1_024
        const val MAX_PEAK_ENCODED_BYTES = 128 * 1_024 * 1_024
    }
}
