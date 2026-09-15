package app.openstory.composition

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import app.openstory.artwork.runtime.ArtworkLoader
import app.openstory.catalog.feature.CatalogCoverArtwork
import app.openstory.catalog.feature.CatalogRuntimeAccess
import app.openstory.common.navigation.RouteEntryId
import app.openstory.common.navigation.RouteLifecycleSource
import app.openstory.composition.navigation.StoryRouteCodec
import app.openstory.library.runtime.LibraryRuntime
import app.openstory.navigation.AppRoute
import app.openstory.story.feature.CatalogStoryFacet
import app.openstory.story.feature.RuntimeStoryLibraryFacet
import app.openstory.story.feature.StoryEntryPoint
import app.openstory.story.feature.StoryLibraryFacet
import app.openstory.story.feature.StoryPresentationStore
import app.openstory.story.feature.rememberStoryPresentationStore

internal class StoryDestinationHost(
    private val presentationStore: StoryPresentationStore,
    private val runtimeAccess: CatalogRuntimeAccess,
    private val libraryFacet: StoryLibraryFacet,
    private val artworkLoader: ArtworkLoader,
) {
    @Composable
    fun Content(
        route: AppRoute.Story,
        onBack: () -> Unit,
    ) {
        StoryDestination(
            route = route,
            presentationStore = presentationStore,
            runtimeAccess = runtimeAccess,
            libraryFacet = libraryFacet,
            artworkLoader = artworkLoader,
            onBack = onBack,
        )
    }
}

@Composable
internal fun rememberStoryDestinationHost(
    lifecycleSource: RouteLifecycleSource,
    runtimeAccess: CatalogRuntimeAccess,
    libraryRuntime: LibraryRuntime,
    artworkLoader: ArtworkLoader,
): StoryDestinationHost {
    val presentationStore = rememberStoryPresentationStore(lifecycleSource)
    val libraryFacet = remember(libraryRuntime) { RuntimeStoryLibraryFacet(libraryRuntime) }
    return remember(presentationStore, runtimeAccess, libraryFacet, artworkLoader) {
        StoryDestinationHost(presentationStore, runtimeAccess, libraryFacet, artworkLoader)
    }
}

@Composable
private fun StoryDestination(
    route: AppRoute.Story,
    presentationStore: StoryPresentationStore,
    runtimeAccess: CatalogRuntimeAccess,
    libraryFacet: StoryLibraryFacet,
    artworkLoader: ArtworkLoader,
    onBack: () -> Unit,
) {
    val storyArgs = StoryRouteCodec.decodeOrNull(route.wire)
    if (storyArgs == null) {
        LaunchedEffect(route.entryId, onBack) { onBack() }
        return
    }

    val catalogFacet = remember(runtimeAccess) {
        CatalogStoryFacet(
            activateCatalog = runtimeAccess::activate,
            onCollectorStarted = runtimeAccess::storyCollectorStarted,
            onCollectorStopped = runtimeAccess::storyCollectorStopped,
        )
    }
    StoryEntryPoint(
        args = storyArgs,
        routeEntryId = RouteEntryId.from(route.entryId),
        presentationStore = presentationStore,
        catalogFacet = catalogFacet,
        libraryFacet = libraryFacet,
        artworkContent = { locator, assetKey, modifier ->
            CatalogCoverArtwork(
                artworkLoader = artworkLoader,
                locator = locator,
                assetKey = assetKey,
                modifier = modifier,
            )
        },
        onBack = onBack,
        onUiPublished = runtimeAccess::storyUiPublished,
        onHeroMaterialized = runtimeAccess::storyHeroMaterialized,
        onBodyMaterialized = runtimeAccess::storyBodyMaterialized,
    )
}
