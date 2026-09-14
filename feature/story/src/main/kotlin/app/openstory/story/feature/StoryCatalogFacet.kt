package app.openstory.story.feature

import app.openstory.catalog.domain.failure.CatalogFailure
import app.openstory.catalog.domain.identity.CatalogSourceKey
import app.openstory.catalog.domain.identity.StorySourceRef
import app.openstory.catalog.runtime.CatalogCapabilityActivation
import app.openstory.catalog.runtime.acquisition.CatalogAcquisitionResult
import app.openstory.catalog.runtime.story.StoryDetailSessionState
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.onCompletion
import kotlinx.coroutines.flow.onStart

interface StoryCatalogFacet {
    suspend fun activate(ref: StorySourceRef): StoryCatalogFacetActivation
}

sealed interface StoryCatalogFacetActivation {
    data class Unavailable(val failure: CatalogFailure) : StoryCatalogFacetActivation

    data class Available(
        val states: Flow<StoryDetailSessionState>,
        val retry: suspend () -> CatalogAcquisitionResult,
        val quiesce: suspend () -> Unit = {},
        val release: suspend () -> Unit,
    ) : StoryCatalogFacetActivation
}

class CatalogStoryFacet(
    private val activateCatalog: suspend (CatalogSourceKey) -> CatalogCapabilityActivation,
    private val onCollectorStarted: () -> Unit = {},
    private val onCollectorStopped: () -> Unit = {},
) : StoryCatalogFacet {
    override suspend fun activate(ref: StorySourceRef): StoryCatalogFacetActivation =
        when (val activation = activateCatalog(ref.catalogSourceKey)) {
            is CatalogCapabilityActivation.Unavailable ->
                StoryCatalogFacetActivation.Unavailable(activation.failure)
            is CatalogCapabilityActivation.Available -> {
                val session = activation.storyDetailSession(ref)
                StoryCatalogFacetActivation.Available(
                    states = session.activate()
                        .onStart { onCollectorStarted() }
                        .onCompletion { onCollectorStopped() },
                    retry = session::retry,
                    quiesce = session::quiesce,
                    release = session::release,
                )
            }
        }
}
