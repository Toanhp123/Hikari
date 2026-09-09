package app.openstory.benchmark

import androidx.benchmark.macro.StartupMode
import androidx.benchmark.macro.StartupTimingMetric
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
            },
        )
    }

    @Test
    fun coldReturningLaunch() {
        benchmarkRule.measureRepeated(
            packageName = HIKARI_PACKAGE,
            metrics = listOf(StartupTimingMetric()),
            compilationMode = benchmarkCompilationMode,
            startupMode = StartupMode.COLD,
            iterations = 5,
            setupBlock = {
                prepareReturningLaunch()
                pressHome()
            },
            measureBlock = {
                startHikariAndWait(DISCOVER_TAG)
            },
        )
    }
}
