package app.openstory.benchmark

import androidx.benchmark.macro.ExperimentalMetricApi
import androidx.benchmark.macro.FrameTimingMetric
import androidx.benchmark.macro.StartupTimingMetric
import androidx.benchmark.macro.TraceSectionMetric
import androidx.benchmark.macro.junit4.MacrobenchmarkRule

@OptIn(ExperimentalMetricApi::class)
internal fun measureDiscoverFrames(
    rule: MacrobenchmarkRule,
    preparation: BenchmarkPreparation = BenchmarkPreparation.NORMAL,
    includeStoryTrace: Boolean = false,
    setup: () -> Unit = {},
    measure: () -> Unit,
) {
    val fixturePreparation = FrameFixturePreparationTracker()
    rule.measureRepeated(
        packageName = HIKARI_PACKAGE,
        metrics = buildList {
            add(FrameTimingMetric())
            if (includeStoryTrace) add(traceMetric("HikariV2:story-detail-content-ready"))
        },
        compilationMode = benchmarkCompilationMode,
        iterations = 5,
        setupBlock = {
            prepareReturningLaunch(
                preparation = preparation,
                clearPackageData = fixturePreparation.shouldClearData(),
            )
            startHikariAndWait(DISCOVER_TAG)
            setup()
        },
        measureBlock = { measure() },
    )
}

@OptIn(ExperimentalMetricApi::class)
internal fun returningStartupMetrics() = persistedStartupMetrics() + listOf(
    traceMetric("HikariV2:discover-first-cover"),
    traceMetric("HikariV2:discover-content-ready"),
)

@OptIn(ExperimentalMetricApi::class)
internal fun persistedStartupMetrics() = listOf(
    StartupTimingMetric(),
    traceMetric("HikariV2:catalog-storage-ready"),
    traceMetric("HikariV2:discover-first-snapshot"),
)

@OptIn(ExperimentalMetricApi::class)
private fun traceMetric(name: String) = TraceSectionMetric(
    sectionName = name,
    mode = TraceSectionMetric.Mode.First,
)

internal fun assertDecodeEvidence() {
    check(benchmarkDiagnostic("successfulDecodes") > 0)
    check(benchmarkDiagnostic("maxDecodeWidth") in 1..MAX_BOUNDED_DECODE_DIMENSION)
    check(benchmarkDiagnostic("maxDecodeHeight") in 1..MAX_BOUNDED_DECODE_DIMENSION)
    check(benchmarkDiagnostic("originalSizeDecodes") == 0)
    check(benchmarkDiagnostic("mainThreadDecodes") == 0)
}

internal const val MAX_DISCOVER_TOUCHED_STORIES = 57
internal const val MAX_RELEASE_TOUCHED_STORIES = 2
private const val MAX_BOUNDED_DECODE_DIMENSION = 8_192
