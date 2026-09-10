package app.openstory.catalog.feature.assets

import app.openstory.catalog.domain.failure.CatalogFailure

internal sealed interface CoverArtworkState {
    data object Loading : CoverArtworkState
    data object Ready : CoverArtworkState
    data class Failed(val failure: CatalogFailure) : CoverArtworkState
}
