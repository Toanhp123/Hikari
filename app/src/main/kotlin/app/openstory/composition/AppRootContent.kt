package app.openstory.composition

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
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

@Composable
internal fun HomeDestination(
    onExploreManga: () -> Unit,
    onExploreLightNovels: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .testTag(AppShellTestTags.HOME_ROOT)
            .background(
                Brush.verticalGradient(
                    listOf(
                        MaterialTheme.colorScheme.surface,
                        MaterialTheme.colorScheme.secondaryContainer.copy(alpha = HOME_GRADIENT_ALPHA),
                    ),
                ),
            )
            .padding(
                horizontal = HOME_HORIZONTAL_PADDING,
                vertical = HOME_VERTICAL_PADDING,
            ),
        verticalArrangement = Arrangement.spacedBy(HOME_CONTENT_SPACING),
    ) {
        Text(
            text = "Your library starts here",
            style = MaterialTheme.typography.headlineLarge,
            fontWeight = FontWeight.Bold,
        )
        Text(
            text = "Build a quiet shelf from stories you choose. Explore a catalog to begin.",
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Column(verticalArrangement = Arrangement.spacedBy(HOME_ACTION_SPACING)) {
            Button(
                onClick = onExploreManga,
                modifier = Modifier.fillMaxWidth().testTag(AppShellTestTags.EXPLORE_MANGA),
            ) {
                Text("Explore Manga")
            }
            Button(
                onClick = onExploreLightNovels,
                modifier = Modifier.fillMaxWidth().testTag(AppShellTestTags.EXPLORE_LIGHT_NOVELS),
            ) {
                Text("Explore Light Novels")
            }
        }
    }
}

private fun AppMediaRoute.toCatalogMediaType(): CatalogMediaType = when (this) {
    AppMediaRoute.MANGA -> CatalogMediaType.MANGA
    AppMediaRoute.LIGHT_NOVEL -> CatalogMediaType.LIGHT_NOVEL
}

private const val HOME_GRADIENT_ALPHA = 0.5f
private val HOME_HORIZONTAL_PADDING = 28.dp
private val HOME_VERTICAL_PADDING = 72.dp
private val HOME_CONTENT_SPACING = 20.dp
private val HOME_ACTION_SPACING = 12.dp
