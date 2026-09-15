package app.openstory.catalog.feature

import androidx.compose.runtime.Composable
import app.openstory.artwork.runtime.ArtworkLoader
import app.openstory.catalog.domain.model.CatalogMediaType
import app.openstory.catalog.domain.read.StoryRouteArgs

@Composable
fun CatalogRootEntryPoint(
    runtimeAccess: CatalogRuntimeAccess,
    artworkLoader: ArtworkLoader,
    mediaType: CatalogMediaType,
    onStorySelected: (StoryRouteArgs) -> Unit = {},
) {
    CatalogComposition(
        runtimeAccess = runtimeAccess,
        artworkLoader = artworkLoader,
        mediaType = mediaType,
        onStorySelected = onStorySelected,
    )
}
