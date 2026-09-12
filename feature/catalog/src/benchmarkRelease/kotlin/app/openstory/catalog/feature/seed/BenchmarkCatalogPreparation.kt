package app.openstory.catalog.feature.seed

import app.openstory.catalog.domain.identity.StorySourceRef
import app.openstory.catalog.domain.model.CatalogMediaType
import app.openstory.catalog.domain.read.DiscoverCard
import app.openstory.catalog.domain.read.DiscoverPersistenceState
import app.openstory.catalog.runtime.CatalogCapabilityActivation
import app.openstory.catalog.runtime.acquisition.CatalogAcquisitionResult
import app.openstory.catalog.runtime.source.CatalogSourceBinding
import kotlinx.coroutines.flow.first

public enum class BenchmarkCatalogPreparation(public val wireValue: String) {
    NORMAL("normal"),
    PERSISTED_EMPTY("persisted-empty"),
    REPEATED_REFRESH("repeated-refresh"),
    DISK_HIT("disk-hit"),
    AGED_STORAGE("aged-storage"),
    OVERSIZED_DETAIL("oversized-detail"),
    ORPHAN_OVERFLOW("orphan-overflow"),
    PIN_PRUNE_RACE("pin-prune-race"),
    PATHOLOGICAL_IMAGE_BOUNDS("pathological-image-bounds");

    public companion object {
        public fun fromWireValue(value: String?): BenchmarkCatalogPreparation = entries
            .singleOrNull { it.wireValue == value }
            ?: throw IllegalArgumentException("Unknown benchmark Catalog preparation: $value")
    }
}

internal object BenchmarkCatalogStatePreparation {
    suspend fun publishDiscoverGenerations(
        activation: CatalogCapabilityActivation.Available,
        preparation: BenchmarkCatalogPreparation,
    ) {
        val generations = if (preparation == BenchmarkCatalogPreparation.REPEATED_REFRESH) {
            REPEATED_REFRESH_GENERATIONS
        } else {
            1
        }
        repeat(generations) {
            CatalogMediaType.entries.forEach { mediaType ->
                check(activation.acquireDiscover(mediaType) == CatalogAcquisitionResult.Success) {
                    "Benchmark Catalog fixture import failed for $mediaType."
                }
            }
        }
    }

    suspend fun awaitPublishedSnapshots(
        activation: CatalogCapabilityActivation.Available,
        binding: CatalogSourceBinding,
    ) {
        CatalogMediaType.entries.forEach { mediaType ->
            activation.discoverSession(mediaType).states.first { state ->
                val published = state.persistence as? DiscoverPersistenceState.Published
                published?.provenance?.catalogSourceKey == binding.catalogSourceKey &&
                    published.provenance.sourceVersion == binding.sourceVersion
            }
        }
    }

    suspend fun prepareFirstStoryDetail(activation: CatalogCapabilityActivation.Available) {
        prepareStoryDetail(activation, firstMangaCard(activation).ref)
    }

    suspend fun prepareStoryDetail(
        activation: CatalogCapabilityActivation.Available,
        ref: StorySourceRef,
    ) {
        val storySession = activation.storyDetailSession(ref)
        storySession.activate().first { state -> state.projection?.detail != null }
        storySession.release()
    }

    suspend fun firstMangaCard(activation: CatalogCapabilityActivation.Available): DiscoverCard =
        activation.discoverSession(CatalogMediaType.MANGA).states
            .first { it.persistence is DiscoverPersistenceState.Published }
            .let { state ->
                (state.persistence as DiscoverPersistenceState.Published).cards.first()
            }

    private const val REPEATED_REFRESH_GENERATIONS = 6
}

internal val BenchmarkCatalogPreparation.sourceScenario: BenchmarkCatalogScenario
    get() = when (this) {
        BenchmarkCatalogPreparation.PERSISTED_EMPTY -> BenchmarkCatalogScenario.PERSISTED_EMPTY
        BenchmarkCatalogPreparation.OVERSIZED_DETAIL -> BenchmarkCatalogScenario.OVERSIZED_DETAIL
        BenchmarkCatalogPreparation.ORPHAN_OVERFLOW -> BenchmarkCatalogScenario.ROTATING_GENERATIONS
        BenchmarkCatalogPreparation.NORMAL,
        BenchmarkCatalogPreparation.REPEATED_REFRESH,
        BenchmarkCatalogPreparation.DISK_HIT,
        BenchmarkCatalogPreparation.AGED_STORAGE,
        BenchmarkCatalogPreparation.PIN_PRUNE_RACE,
        BenchmarkCatalogPreparation.PATHOLOGICAL_IMAGE_BOUNDS,
        -> BenchmarkCatalogScenario.NORMAL
    }
