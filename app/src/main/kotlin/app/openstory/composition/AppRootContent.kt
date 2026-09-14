package app.openstory.composition

import androidx.compose.runtime.Composable
import app.openstory.artwork.ArtworkLoader
import app.openstory.catalog.domain.model.CatalogMediaType
import app.openstory.catalog.feature.CatalogRootEntryPoint
import app.openstory.catalog.feature.CatalogRuntimeAccess
import app.openstory.composition.navigation.StoryRouteCodec
import app.openstory.navigation.AppMediaRoute
import app.openstory.navigation.AppRoute
import app.openstory.navigation.newStoryRouteEntryId

@Composable
internal fun DiscoverDestination(
    route: AppRoute.Discover,
    runtimeAccess: CatalogRuntimeAccess,
    artworkLoader: ArtworkLoader,
    onStoryRoute: (AppRoute.Story) -> Boolean,
) {
    CatalogRootEntryPoint(
        runtimeAccess = runtimeAccess,
        artworkLoader = artworkLoader,
        mediaType = route.media.toCatalogMediaType(),
        onStorySelected = { storyArgs ->
            val wire = StoryRouteCodec.encode(storyArgs, newStoryRouteEntryId())
            onStoryRoute(AppRoute.Story(wire))
        },
    )
}

private fun AppMediaRoute.toCatalogMediaType(): CatalogMediaType = when (this) {
    AppMediaRoute.MANGA -> CatalogMediaType.MANGA
    AppMediaRoute.LIGHT_NOVEL -> CatalogMediaType.LIGHT_NOVEL
}
