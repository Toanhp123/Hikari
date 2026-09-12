package app.openstory.catalog.feature.seed

import app.openstory.catalog.domain.write.CatalogMutationBounds
import app.openstory.catalog.feature.fixture.BenchmarkCatalogPreparationEvidence
import app.openstory.catalog.runtime.CatalogRuntimeOwnershipCallbacks
import java.util.concurrent.atomic.AtomicInteger

internal class BenchmarkMutationPeaks {
    private val maxDiscoverTouched = AtomicInteger()
    private val maxReleaseTouched = AtomicInteger()

    val callbacks = CatalogRuntimeOwnershipCallbacks(
        onDiscoverMutationTouched = { touched ->
            maxDiscoverTouched.updateAndGet { previous -> maxOf(previous, touched) }
        },
        onStoryReleaseMutationTouched = { touched ->
            maxReleaseTouched.updateAndGet { previous -> maxOf(previous, touched) }
        },
    )

    fun evidence(
        preparation: BenchmarkCatalogPreparation,
        orphanRetentionRows: Int = 0,
        pinPruneRetentionRows: Int = 0,
        pathologicalImageRejections: Int = 0,
    ): BenchmarkCatalogPreparationEvidence {
        val evidence = BenchmarkCatalogPreparationEvidence(
            agedUnrelatedRows = if (preparation == BenchmarkCatalogPreparation.AGED_STORAGE) {
                BENCHMARK_AGED_UNRELATED_ROWS
            } else {
                0
            },
            maxDiscoverTouchedStoryIds = maxDiscoverTouched.get(),
            maxReleaseTouchedStoryIds = maxReleaseTouched.get(),
            orphanRetentionRows = orphanRetentionRows,
            pinPruneRetentionRows = pinPruneRetentionRows,
            pathologicalImageRejections = pathologicalImageRejections,
        )
        check(evidence.maxDiscoverTouchedStoryIds <= CatalogMutationBounds.MAX_DISCOVER_TOUCHED_STORY_IDS)
        check(evidence.maxReleaseTouchedStoryIds <= CatalogMutationBounds.MAX_RELEASE_TOUCHED_STORY_IDS)
        return evidence
    }
}

internal const val BENCHMARK_AGED_UNRELATED_ROWS = 5_000
