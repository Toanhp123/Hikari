package app.openstory.catalog.feature

import androidx.compose.runtime.Composable
import app.openstory.catalog.domain.model.CatalogMediaType
import app.openstory.catalog.domain.read.StoryRouteArgs

@Composable
fun CatalogRootEntryPoint(
    mediaType: CatalogMediaType,
    onStorySelected: (StoryRouteArgs) -> Unit = {},
) {
    CatalogComposition(mediaType = mediaType, onStorySelected = onStorySelected)
}
