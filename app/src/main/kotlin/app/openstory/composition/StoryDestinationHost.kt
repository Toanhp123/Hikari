package app.openstory.composition

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import app.openstory.catalog.feature.CatalogCoverArtwork
import app.openstory.catalog.feature.rememberCatalogRuntimeAccess
import app.openstory.common.navigation.RouteEntryId
import app.openstory.common.navigation.RouteLifecycleSource
import app.openstory.composition.navigation.StoryRouteCodec
import app.openstory.navigation.AppRoute
import app.openstory.story.feature.CatalogStoryFacet
import app.openstory.story.feature.StoryEntryPoint
import app.openstory.story.feature.StoryPresentationStore
import app.openstory.story.feature.rememberStoryPresentationStore

internal class StoryDestinationHost(
    private val presentationStore: StoryPresentationStore,
) {
    @Composable
    fun Content(
        route: AppRoute.Story,
        onBack: () -> Unit,
    ) {
        StoryDestination(
            route = route,
            presentationStore = presentationStore,
            onBack = onBack,
        )
    }
}

@Composable
internal fun rememberStoryDestinationHost(
    lifecycleSource: RouteLifecycleSource,
): StoryDestinationHost {
    val presentationStore = rememberStoryPresentationStore(lifecycleSource)
    return remember(presentationStore) { StoryDestinationHost(presentationStore) }
}

@Composable
private fun StoryDestination(
    route: AppRoute.Story,
    presentationStore: StoryPresentationStore,
    onBack: () -> Unit,
) {
    val storyArgs = StoryRouteCodec.decodeOrNull(route.wire)
    if (storyArgs == null) {
        LaunchedEffect(route.entryId, onBack) { onBack() }
        return
    }

    val runtimeAccess = rememberCatalogRuntimeAccess()
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
        artworkContent = { title, locator, assetKey, modifier ->
            CatalogCoverArtwork(
                runtimeAccess = runtimeAccess,
                title = title,
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
