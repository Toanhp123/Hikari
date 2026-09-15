package app.openstory.story.feature

import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import app.openstory.catalog.domain.asset.CoverAssetKey
import app.openstory.catalog.domain.asset.CoverLocator
import app.openstory.catalog.domain.read.StoryRouteArgs
import app.openstory.common.navigation.RouteEntryId

@Composable
fun StoryEntryPoint(
    args: StoryRouteArgs,
    routeEntryId: RouteEntryId,
    presentationStore: StoryPresentationStore,
    catalogFacet: StoryCatalogFacet,
    libraryFacet: StoryLibraryFacet,
    artworkContent: @Composable (String, CoverLocator?, CoverAssetKey?, Modifier) -> Unit,
    onBack: () -> Unit,
    onUiPublished: () -> Unit = {},
    onHeroMaterialized: () -> Unit = {},
    onBodyMaterialized: () -> Unit = {},
) {
    val owner = remember(presentationStore, routeEntryId, args) {
        presentationStore.ownerFor(
            entryId = routeEntryId,
            args = args,
            catalogFacet = catalogFacet,
            libraryFacet = libraryFacet,
            onUiPublished = onUiPublished,
        )
    }
    val state by owner.state.collectAsState()

    StoryDetailScreen(
        state = state,
        onBack = onBack,
        onRetry = owner::retry,
        onLibraryToggle = owner::toggleLibraryMembership,
        artworkContent = artworkContent,
        onHeroMaterialized = onHeroMaterialized,
        onBodyMaterialized = onBodyMaterialized,
    )
}
