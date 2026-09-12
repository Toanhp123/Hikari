package app.openstory.benchmark

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.LargeTest
import org.junit.Test
import org.junit.runner.RunWith

@LargeTest
@RunWith(AndroidJUnit4::class)
class HikariBenchmarkPreparationTest {
    @Test
    fun boundedOrphanOverflowPreparationPrunesToTheFrozenLimit() {
        inspectPreparation(BenchmarkPreparation.ORPHAN_OVERFLOW) {
            check(benchmarkDiagnostic("orphanRetentionRows") == 64)
            check(benchmarkDiagnostic("maxDiscoverTouched") <= MAX_DISCOVER_TOUCHED_STORIES)
            check(benchmarkDiagnostic("maxReleaseTouched") <= MAX_RELEASE_TOUCHED_STORIES)
        }
    }

    @Test
    fun pinPruneRacePreparationRetainsOnlyTheReleasedDetailedStory() {
        inspectPreparation(BenchmarkPreparation.PIN_PRUNE_RACE) {
            check(benchmarkDiagnostic("pinPruneRetentionRows") == 1)
            check(benchmarkDiagnostic("maxDiscoverTouched") <= MAX_DISCOVER_TOUCHED_STORIES)
            check(benchmarkDiagnostic("maxReleaseTouched") <= MAX_RELEASE_TOUCHED_STORIES)
        }
    }

    @Test
    fun pathologicalImagePreparationRejectsEncodedAndDimensionOverflow() {
        inspectPreparation(BenchmarkPreparation.PATHOLOGICAL_IMAGE_BOUNDS) {
            check(benchmarkDiagnostic("pathologicalImageRejections") == 2)
        }
    }

    private fun inspectPreparation(preparation: BenchmarkPreparation, assertions: () -> Unit) {
        try {
            prepareReturningFixture(preparation)
            assertions()
        } finally {
            benchmarkDevice().executeShellCommand("am force-stop $HIKARI_PACKAGE")
        }
    }
}
