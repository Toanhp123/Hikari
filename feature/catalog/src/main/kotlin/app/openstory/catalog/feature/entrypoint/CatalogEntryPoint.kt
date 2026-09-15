package app.openstory.catalog.feature.entrypoint

import androidx.compose.runtime.Composable
import app.openstory.artwork.runtime.ArtworkLoader
import app.openstory.catalog.domain.model.CatalogMediaType
import app.openstory.catalog.domain.read.StoryRouteArgs
import app.openstory.catalog.feature.discover.composition.DiscoverComposition
import app.openstory.catalog.feature.runtime.CatalogRuntimeAccess

@Composable
fun CatalogRootEntryPoint(
    runtimeAccess: CatalogRuntimeAccess,
    artworkLoader: ArtworkLoader,
    mediaType: CatalogMediaType,
    onStorySelected: (StoryRouteArgs) -> Unit = {},
) {
    DiscoverComposition(
        runtimeAccess = runtimeAccess,
        artworkLoader = artworkLoader,
        mediaType = mediaType,
        onStorySelected = onStorySelected,
    )
}
