package app.openstory.catalog.feature.seed

import android.content.Context
import app.openstory.catalog.domain.model.CatalogMediaType
import app.openstory.catalog.domain.read.DiscoverPersistenceState
import app.openstory.catalog.runtime.CatalogCapabilityActivation
import app.openstory.catalog.runtime.CatalogRuntimeFactory
import app.openstory.catalog.runtime.acquisition.CatalogAcquisitionResult
import app.openstory.catalog.runtime.fixture.BenchmarkAgedCatalogFixture
import app.openstory.catalog.runtime.source.CatalogSourceBinding
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.first

internal object BenchmarkRetentionPreparation {
    suspend fun prepareOrphanOverflow(
        context: Context,
        runtimeFactory: CatalogRuntimeFactory,
    ): Int {
        val session = runtimeFactory.createSession()
        try {
            val activation = session.activate() as? CatalogCapabilityActivation.Available
                ?: error("Orphan-overflow benchmark Catalog source is unavailable.")
            var previousStoryIds = emptySet<String>()
            repeat(ORPHAN_GENERATION_COUNT) {
                check(activation.acquireDiscover(CatalogMediaType.MANGA) == CatalogAcquisitionResult.Success)
                val cards = activation.discoverSession(CatalogMediaType.MANGA).states.first { state ->
                    val published = state.persistence as? DiscoverPersistenceState.Published
                    val currentStoryIds = published?.cards?.mapTo(linkedSetOf()) { it.ref.storyId.value }.orEmpty()
                    published != null && currentStoryIds != previousStoryIds
                }.let { state -> (state.persistence as DiscoverPersistenceState.Published).cards }
                cards.distinctBy { it.ref.storyId }.forEach { card ->
                    BenchmarkCatalogStatePreparation.prepareStoryDetail(activation, card.ref)
                }
                previousStoryIds = cards.mapTo(linkedSetOf()) { it.ref.storyId.value }
            }
        } finally {
            session.close()
        }
        return BenchmarkAgedCatalogFixture.orphanRetentionCount(context.applicationContext).also { count ->
            check(count == ORPHAN_RETENTION_LIMIT) {
                "Orphan-overflow preparation retained $count rows instead of $ORPHAN_RETENTION_LIMIT."
            }
        }
    }

    suspend fun preparePinPruneRace(
        context: Context,
        binding: CatalogSourceBinding,
        runtimeFactory: CatalogRuntimeFactory,
        source: BenchmarkPinPruneSource,
    ): Int {
        val session = runtimeFactory.createSession()
        try {
            val activation = session.activate() as? CatalogCapabilityActivation.Available
                ?: error("Pin/prune benchmark Catalog source is unavailable.")
            BenchmarkCatalogStatePreparation.publishDiscoverGenerations(
                activation,
                BenchmarkCatalogPreparation.NORMAL,
            )
            BenchmarkCatalogStatePreparation.awaitPublishedSnapshots(activation, binding)
            val selected = BenchmarkCatalogStatePreparation.firstMangaCard(activation)
            BenchmarkCatalogStatePreparation.prepareStoryDetail(activation, selected.ref)
            source.armPrune()
            coroutineScope {
                val refresh = async { activation.acquireDiscover(CatalogMediaType.MANGA) }
                source.pruneEntered.await()
                val storySession = activation.storyDetailSession(selected.ref)
                try {
                    storySession.activate().first { state -> state.projection?.detail != null }
                } finally {
                    source.releasePrune()
                }
                check(refresh.await() == CatalogAcquisitionResult.Success)
                check(storySession.activate().first { state -> state.projection?.detail != null }.projection != null)
                storySession.release()
            }
        } finally {
            source.releasePrune()
            session.close()
        }
        return BenchmarkAgedCatalogFixture.orphanRetentionCount(context.applicationContext).also { count ->
            check(count == PIN_PRUNE_RETENTION_ROWS) {
                "Pin/prune preparation retained $count rows instead of $PIN_PRUNE_RETENTION_ROWS."
            }
        }
    }

    suspend fun prepareAgedRefresh(context: Context, runtimeFactory: CatalogRuntimeFactory) {
        BenchmarkAgedCatalogFixture.seedUnrelatedRows(context.applicationContext)
        val session = runtimeFactory.createSession()
        try {
            val activation = session.activate() as? CatalogCapabilityActivation.Available
                ?: error("Aged benchmark Catalog source is unavailable.")
            CatalogMediaType.entries.forEach { mediaType ->
                check(activation.acquireDiscover(mediaType) == CatalogAcquisitionResult.Success) {
                    "Aged benchmark refresh failed for $mediaType."
                }
            }
        } finally {
            session.close()
        }
    }

    private const val ORPHAN_GENERATION_COUNT = 9
    private const val ORPHAN_RETENTION_LIMIT = 64
    private const val PIN_PRUNE_RETENTION_ROWS = 1
}
