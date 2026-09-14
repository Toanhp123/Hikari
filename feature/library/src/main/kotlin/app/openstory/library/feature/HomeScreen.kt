package app.openstory.library.feature

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.Box
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
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import app.openstory.designsystem.content.HikariPosterCard
import app.openstory.designsystem.content.HikariPosterGrid
import app.openstory.designsystem.content.HikariPosterSkeleton
import app.openstory.designsystem.state.HikariEmptyState
import app.openstory.designsystem.state.HikariErrorState
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
    Column(
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
        HomeHeader(state, onInputQueryChanged, onFilterSelected)
        when (val content = state.content) {
            HomeContentState.Loading -> HomeLoading()
            HomeContentState.LibraryEmpty -> HomeLibraryEmpty(onExploreManga, onExploreLightNovels)
            HomeContentState.NoMatches -> HomeNoMatches()
            HomeContentState.Failure -> HomeFailure(onRetry)
            is HomeContentState.Content -> HikariPosterGrid(
                state = gridState,
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(
                    start = HOME_HORIZONTAL_PADDING,
                    end = HOME_HORIZONTAL_PADDING,
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
                        artworkModifier = Modifier.aspectRatio(HOME_POSTER_ASPECT_RATIO),
                    ) {
                        artwork(story, Modifier.fillMaxSize())
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
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(
                start = HOME_HORIZONTAL_PADDING,
                end = HOME_HORIZONTAL_PADDING,
                top = HOME_TOP_PADDING,
                bottom = MaterialTheme.hikariSpacing.space20,
            ),
        verticalArrangement = Arrangement.spacedBy(MaterialTheme.hikariSpacing.space16),
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(MaterialTheme.hikariSpacing.space4)) {
            Text("Library", style = MaterialTheme.typography.headlineLarge, fontWeight = FontWeight.Bold)
            Text(
                "Stories you saved, ready from local truth.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        OutlinedTextField(
            value = state.inputQuery,
            onValueChange = onInputQueryChanged,
            modifier = Modifier.fillMaxWidth().testTag(HomeTestTags.SEARCH),
            label = { Text("Search your library") },
            singleLine = true,
        )
        Row(
            modifier = Modifier.horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(MaterialTheme.hikariSpacing.space8),
        ) {
            LibraryFilter.entries.forEach { filter ->
                FilterChip(
                    selected = state.filter == filter,
                    onClick = { onFilterSelected(filter) },
                    label = { Text(filter.label) },
                )
            }
        }
    }
}

@Composable
private fun HomeLoading() {
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = HOME_HORIZONTAL_PADDING),
        horizontalArrangement = Arrangement.spacedBy(MaterialTheme.hikariSpacing.space12),
    ) {
        repeat(HOME_LOADING_PLACEHOLDER_COUNT) {
            HikariPosterSkeleton(
                modifier = Modifier.weight(1f).widthIn(max = 144.dp),
                artworkModifier = Modifier.aspectRatio(HOME_POSTER_ASPECT_RATIO),
            )
        }
    }
}

@Composable
private fun HomeLibraryEmpty(
    onExploreManga: () -> Unit,
    onExploreLightNovels: () -> Unit,
) {
    Column(
        modifier = Modifier.fillMaxSize().padding(HOME_HORIZONTAL_PADDING),
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
private fun HomeNoMatches() {
    Box(
        modifier = Modifier.fillMaxSize().padding(HOME_HORIZONTAL_PADDING),
        contentAlignment = Alignment.Center,
    ) {
        HikariEmptyState(
            title = "No saved stories match",
            body = "Try another search or media filter.",
        )
    }
}

@Composable
private fun HomeFailure(onRetry: () -> Unit) {
    Box(
        modifier = Modifier.fillMaxSize().padding(HOME_HORIZONTAL_PADDING),
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

private val HOME_HORIZONTAL_PADDING = 20.dp
private val HOME_TOP_PADDING = 28.dp
private val HOME_BOTTOM_PADDING = 32.dp
private const val HOME_POSTER_ASPECT_RATIO = 104f / 150f
private const val HOME_LOADING_PLACEHOLDER_COUNT = 3
