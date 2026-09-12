package app.openstory.catalog.feature.seed

import android.content.Context
import app.openstory.catalog.feature.VariantCatalogBinding
import app.openstory.catalog.feature.fixture.BenchmarkCatalogPreparationEvidence
import app.openstory.catalog.runtime.CatalogCapabilityActivation
import app.openstory.catalog.runtime.CatalogRuntimeFactory
import app.openstory.catalog.runtime.acquisition.CatalogAcquisitionStatus
import app.openstory.catalog.runtime.source.CatalogSourceBinding
import kotlinx.coroutines.flow.first

public object BenchmarkCatalogFixture {
    public suspend fun prepare(
        context: Context,
        preparation: BenchmarkCatalogPreparation = BenchmarkCatalogPreparation.NORMAL,
    ): BenchmarkCatalogPreparationEvidence {
        val mutationPeaks = BenchmarkMutationPeaks()
        val baseBinding = requireNotNull(VariantCatalogBinding.binding)
        val benchmarkSource = BenchmarkCatalogSource(baseBinding.catalogSourceKey, preparation.sourceScenario)
        val pinPruneSource = benchmarkSource.takeIf {
            preparation == BenchmarkCatalogPreparation.PIN_PRUNE_RACE
        }?.let(::BenchmarkPinPruneSource)
        val binding = baseBinding.copy(
            acquisitionSource = pinPruneSource ?: benchmarkSource,
        )
        val runtimeFactory = CatalogRuntimeFactory(
            context = context.applicationContext,
            binding = binding,
            ownershipCallbacks = mutationPeaks.callbacks,
        )
        var orphanRetentionRows = 0
        var pinPruneRetentionRows = 0
        var pathologicalImageRejections = 0
        when (preparation) {
            BenchmarkCatalogPreparation.ORPHAN_OVERFLOW ->
                orphanRetentionRows = BenchmarkRetentionPreparation.prepareOrphanOverflow(context, runtimeFactory)

            BenchmarkCatalogPreparation.PIN_PRUNE_RACE ->
                pinPruneRetentionRows = BenchmarkRetentionPreparation.preparePinPruneRace(
                    context = context,
                    binding = binding,
                    runtimeFactory = runtimeFactory,
                    source = requireNotNull(pinPruneSource),
                )

            else -> {
                pathologicalImageRejections = prepareImportedState(
                    context,
                    preparation,
                    binding,
                    runtimeFactory,
                )
                if (preparation == BenchmarkCatalogPreparation.AGED_STORAGE)
                    BenchmarkRetentionPreparation.prepareAgedRefresh(context, runtimeFactory)
            }
        }
        return mutationPeaks.evidence(
            preparation = preparation,
            orphanRetentionRows = orphanRetentionRows,
            pinPruneRetentionRows = pinPruneRetentionRows,
            pathologicalImageRejections = pathologicalImageRejections,
        )
    }

    private suspend fun prepareImportedState(
        context: Context,
        preparation: BenchmarkCatalogPreparation,
        binding: CatalogSourceBinding,
        runtimeFactory: CatalogRuntimeFactory,
    ): Int {
        var pathologicalImageRejections = 0
        val session = runtimeFactory.createSession()
        try {
            val activation = session.activate() as? CatalogCapabilityActivation.Available
                ?: error("Benchmark Catalog source is unavailable.")
            BenchmarkCatalogStatePreparation.publishDiscoverGenerations(activation, preparation)
            BenchmarkCatalogStatePreparation.awaitPublishedSnapshots(activation, binding)
            if (preparation !in setOf(
                    BenchmarkCatalogPreparation.PERSISTED_EMPTY,
                    BenchmarkCatalogPreparation.OVERSIZED_DETAIL,
                )
            ) {
                BenchmarkCatalogStatePreparation.prepareFirstStoryDetail(activation)
            }
            if (preparation == BenchmarkCatalogPreparation.DISK_HIT) {
                BenchmarkCoverPreparation.primeDiskCover(context, activation)
            }
            if (preparation == BenchmarkCatalogPreparation.OVERSIZED_DETAIL) {
                assertOversizedDetailRejected(activation)
            }
            if (preparation == BenchmarkCatalogPreparation.PATHOLOGICAL_IMAGE_BOUNDS) {
                pathologicalImageRejections = BenchmarkCoverPreparation.assertPathologicalImageBoundsRejected(
                    context,
                    activation,
                )
            }
        } finally {
            session.close()
        }
        return pathologicalImageRejections
    }

    private suspend fun assertOversizedDetailRejected(activation: CatalogCapabilityActivation.Available) {
        val ref = BenchmarkCatalogStatePreparation.firstMangaCard(activation).ref
        val terminal = activation.storyDetailSession(ref).activate().first { state ->
            state.acquisition is CatalogAcquisitionStatus.Failed
        }
        check(terminal.acquisition is CatalogAcquisitionStatus.Failed) {
            "Oversized detail fixture was unexpectedly admitted."
        }
    }
}
