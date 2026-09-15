package app.openstory.library.feature

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import app.openstory.designsystem.content.HikariPosterCard
import app.openstory.designsystem.content.HikariPosterGeometry
import app.openstory.designsystem.content.HikariPosterGrid
import app.openstory.designsystem.content.HikariPosterSkeleton
import app.openstory.designsystem.control.HikariFilterChip
import app.openstory.designsystem.control.HikariSearchField
import app.openstory.designsystem.presentation.HikariFocusedHeader
import app.openstory.designsystem.state.HikariEmptyState
import app.openstory.designsystem.state.HikariErrorState
import app.openstory.designsystem.theme.HikariBreakpoints
import app.openstory.designsystem.theme.hikariSpacing
import app.openstory.library.domain.LibraryFilter

@Composable
fun HomeScreen(
    state: HomeUiState,
    gridState: LazyGridState = androidx.compose.foundation.lazy.grid.rememberLazyGridState(),
    onInputQueryChanged: (String) -> Unit,
    onFilterSelected: (LibraryFilter) -> Unit,
    onExploreManga: () -> Unit,
    onExploreLightNovels: () -> Unit,
    onStorySelected: (LibraryStoryPosterUi) -> Unit,
    onRetry: () -> Unit,
    artwork: @Composable (LibraryStoryPosterUi, Modifier) -> Unit,
) {
    BoxWithConstraints(
        modifier = Modifier
            .fillMaxSize()
            .testTag(HomeTestTags.ROOT)
            .background(
                Brush.verticalGradient(
                    listOf(
                        MaterialTheme.colorScheme.surface,
                        MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.3f),
                    ),
                ),
            ),
    ) {
        val horizontalInset = HikariBreakpoints.screenHorizontalInset(maxWidth)
        Column(modifier = Modifier.fillMaxSize()) {
            HomeHeader(state, onInputQueryChanged, onFilterSelected, horizontalInset)
            when (val content = state.content) {
                HomeContentState.Loading -> HomeLoading(horizontalInset)
                HomeContentState.LibraryEmpty -> HomeLibraryEmpty(
                    onExploreManga,
                    onExploreLightNovels,
                    horizontalInset,
                )
                HomeContentState.NoMatches -> HomeNoMatches(horizontalInset)
                HomeContentState.Failure -> HomeFailure(onRetry, horizontalInset)
                is HomeContentState.Content -> HikariPosterGrid(
                    state = gridState,
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(
                        start = horizontalInset,
                        end = horizontalInset,
                        bottom = HOME_BOTTOM_PADDING,
                    ),
                ) {
                    items(
                        items = content.stories,
                        key = { story -> story.ref.storyId.value },
                    ) { story ->
                        HikariPosterCard(
                            title = story.title,
                            supportingText = story.supportingText,
                            onClick = { onStorySelected(story) },
                            modifier = Modifier.testTag(HomeTestTags.story(story.ref)),
                            geometry = HikariPosterGeometry.Standard,
                        ) {
                            artwork(story, Modifier.fillMaxSize())
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun HomeHeader(
    state: HomeUiState,
    onInputQueryChanged: (String) -> Unit,
    onFilterSelected: (LibraryFilter) -> Unit,
    horizontalInset: Dp,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(
                start = horizontalInset,
                end = horizontalInset,
                top = HOME_TOP_PADDING,
                bottom = MaterialTheme.hikariSpacing.space20,
            ),
        verticalArrangement = Arrangement.spacedBy(MaterialTheme.hikariSpacing.space16),
    ) {
        HikariFocusedHeader(
            title = "Library",
            subtitle = "Stories you saved, ready from local truth.",
        )
        HikariSearchField(
            value = state.inputQuery,
            onValueChange = onInputQueryChanged,
            modifier = Modifier.fillMaxWidth().testTag(HomeTestTags.SEARCH),
            label = "Search your library",
        )
        Row(
            modifier = Modifier.horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(MaterialTheme.hikariSpacing.space8),
        ) {
            LibraryFilter.entries.forEach { filter ->
                HikariFilterChip(
                    selected = state.filter == filter,
                    onClick = { onFilterSelected(filter) },
                    label = filter.label,
                )
            }
        }
    }
}

@Composable
private fun HomeLoading(horizontalInset: Dp) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = horizontalInset),
        horizontalArrangement = Arrangement.spacedBy(MaterialTheme.hikariSpacing.space12),
    ) {
        repeat(HOME_LOADING_PLACEHOLDER_COUNT) {
            HikariPosterSkeleton(
                modifier = Modifier.weight(1f).widthIn(max = 144.dp),
                geometry = HikariPosterGeometry.Standard,
            )
        }
    }
}

@Composable
private fun HomeLibraryEmpty(
    onExploreManga: () -> Unit,
    onExploreLightNovels: () -> Unit,
    horizontalInset: Dp,
) {
    Column(
        modifier = Modifier.fillMaxSize().padding(horizontalInset),
        verticalArrangement = Arrangement.spacedBy(MaterialTheme.hikariSpacing.space16, Alignment.CenterVertically),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        HikariEmptyState(
            title = "Your library is empty",
            body = "Save stories from Manga or Light Novel to build your shelf.",
        )
        Button(onClick = onExploreManga, modifier = Modifier.fillMaxWidth().testTag(HomeTestTags.EXPLORE_MANGA)) {
            Text("Explore Manga")
        }
        Button(
            onClick = onExploreLightNovels,
            modifier = Modifier.fillMaxWidth().testTag(HomeTestTags.EXPLORE_LIGHT_NOVELS),
        ) {
            Text("Explore Light Novels")
        }
    }
}

@Composable
private fun HomeNoMatches(horizontalInset: Dp) {
    Box(
        modifier = Modifier.fillMaxSize().padding(horizontalInset),
        contentAlignment = Alignment.Center,
    ) {
        HikariEmptyState(
            title = "No saved stories match",
            body = "Try another search or media filter.",
        )
    }
}

@Composable
private fun HomeFailure(onRetry: () -> Unit, horizontalInset: Dp) {
    Box(
        modifier = Modifier.fillMaxSize().padding(horizontalInset),
        contentAlignment = Alignment.Center,
    ) {
        HikariErrorState(
            title = "Library unavailable",
            body = "Your saved stories are unchanged. Try the local query again.",
            actionLabel = "Try again",
            onAction = onRetry,
        )
    }
}

private val LibraryFilter.label: String
    get() = when (this) {
        LibraryFilter.ALL -> "All"
        LibraryFilter.MANGA -> "Manga"
        LibraryFilter.LIGHT_NOVEL -> "Light Novel"
    }

private val HOME_TOP_PADDING = 28.dp
private val HOME_BOTTOM_PADDING = 32.dp
private const val HOME_LOADING_PLACEHOLDER_COUNT = 3
