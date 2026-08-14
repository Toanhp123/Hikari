package app.openstory.catalog.ui.dashboard

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.testTag
import app.openstory.catalog.ui.components.ReaderTarget
import app.openstory.catalog.ui.components.StoryPosterCard
import app.openstory.catalog.ui.components.StoryUpdateCard
import app.openstory.catalog.ui.components.StoryUpdateCardContent
import app.openstory.catalog.ui.components.StoryUpdateCardVariant
import app.openstory.common.id.StoryId
import app.openstory.designsystem.content.HikariSectionHeader
import app.openstory.designsystem.state.HikariEmptyState
import app.openstory.designsystem.state.HikariLoadingState
import app.openstory.designsystem.theme.hikariSpacing
import app.openstory.designsystem.layout.HikariDestinationScaffold
import app.openstory.designsystem.layout.HikariTopLevelHeader
import app.openstory.designsystem.layout.plus
import app.openstory.designsystem.theme.hikariDimensions
import app.openstory.designsystem.feedback.HikariInlineFeedback
import app.openstory.designsystem.theme.hikariAtmosphereBrush

@Composable
fun HomeDashboardScreen(
    state: HomeDashboardUiState,
    onDiscover: () -> Unit,
    onStorySelected: (StoryId) -> Unit,
    onResume: (ReaderTarget) -> Unit,
    firstContentFocusRequester: FocusRequester? = null,
    onUtilityRequested: () -> Unit = {},
    utilityFocusRequester: FocusRequester? = null,
    utilityNextFocusRequester: FocusRequester? = null,
    modifier: Modifier = Modifier,
    contentPadding: PaddingValues = PaddingValues.Zero,
) {
    val continueFocus = remember { FocusRequester() }
    val readingFocus = remember { FocusRequester() }
    val background = MaterialTheme.hikariAtmosphereBrush
    CompositionLocalProvider(LocalContentColor provides MaterialTheme.colorScheme.onBackground) {
        HikariDestinationScaffold(modifier) {
            Box(
                Modifier
                    .fillMaxSize()
                    .background(background)
                    .testTag("home-atmosphere"),
            ) {
                when {
                    state.loading -> Column(Modifier.fillMaxSize().padding(contentPadding)) {
                        HikariTopLevelHeader(
                            title = "Home",
                            onAction = onUtilityRequested,
                            focusRequester = utilityFocusRequester,
                            nextFocusRequester = utilityNextFocusRequester,
                        )
                        HikariLoadingState("Loading your reading home", Modifier.weight(1f))
                    }
                    state.isEmpty -> Column(Modifier.fillMaxSize().padding(contentPadding)) {
                        HikariTopLevelHeader(
                            title = "Home",
                            onAction = onUtilityRequested,
                            focusRequester = utilityFocusRequester,
                            nextFocusRequester = utilityNextFocusRequester,
                        )
                        Box(Modifier.weight(1f)) {
                            EmptyHome(state.failure, onDiscover, firstContentFocusRequester)
                        }
                    }
                    else -> HomeContent(
                        state, onStorySelected, onResume, continueFocus, readingFocus,
                        firstContentFocusRequester, contentPadding, onUtilityRequested,
                        utilityFocusRequester, utilityNextFocusRequester,
                    )
                }
            }
        }
    }
}

@Composable
private fun EmptyHome(
    failure: HomeDashboardFailure?,
    onDiscover: () -> Unit,
    firstContentFocusRequester: FocusRequester?,
) {
    Box(Modifier.fillMaxSize()) {
        HikariEmptyState(
            title = "Your reading home is ready to grow.",
            message = "Find a story and add it to your Library to begin.",
            actionLabel = "Discover stories",
            onAction = onDiscover,
            actionFocusRequester = firstContentFocusRequester,
        )
        failure?.let { ObservationFailure(it, Modifier.align(Alignment.TopCenter)) }
    }
}

@Composable
private fun HomeContent(
    state: HomeDashboardUiState,
    onStorySelected: (StoryId) -> Unit,
    onResume: (ReaderTarget) -> Unit,
    continueFocus: FocusRequester,
    readingFocus: FocusRequester,
    firstContentFocusRequester: FocusRequester?,
    contentPadding: PaddingValues,
    onUtilityRequested: () -> Unit,
    utilityFocusRequester: FocusRequester?,
    utilityNextFocusRequester: FocusRequester?,
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = contentPadding.plus(bottom = MaterialTheme.hikariSpacing.space24),
        verticalArrangement = Arrangement.spacedBy(MaterialTheme.hikariSpacing.space16),
    ) {
        item("home-header") {
            HikariTopLevelHeader(
                title = "Home",
                onAction = onUtilityRequested,
                focusRequester = utilityFocusRequester,
                nextFocusRequester = utilityNextFocusRequester,
            )
        }
        item("home-summary") { HomeSummary(state.summary) }
        state.failure?.let { failure ->
            item("home-failure") {
                ObservationFailure(failure, Modifier.padding(horizontal = MaterialTheme.hikariSpacing.space20))
            }
        }
        continueReadingShelf(
            state,
            onResume,
            firstContentFocusRequester ?: continueFocus,
            readingFocus,
        )
        itemShelf(
            "Reading",
            state.reading,
            onStorySelected,
            firstContentFocusRequester.takeIf { state.continueReading.isEmpty() } ?: readingFocus,
        )
        itemShelf(
            "Planned", state.planned, onStorySelected,
            firstContentFocusRequester.takeIf {
                state.continueReading.isEmpty() && state.reading.isEmpty()
            },
        )
        itemShelf(
            "Paused", state.paused, onStorySelected,
            firstContentFocusRequester.takeIf {
                state.continueReading.isEmpty() && state.reading.isEmpty() && state.planned.isEmpty()
            },
        )
        itemShelf(
            "Completed", state.completed, onStorySelected,
            firstContentFocusRequester.takeIf {
                state.continueReading.isEmpty() && state.reading.isEmpty() && state.planned.isEmpty() &&
                    state.paused.isEmpty()
            },
        )
        latestUpdatesShelf(
            state.latestUpdates,
            onResume,
            firstContentFocusRequester.takeIf {
                state.continueReading.isEmpty() && state.reading.isEmpty() && state.planned.isEmpty() &&
                    state.paused.isEmpty() && state.completed.isEmpty()
            },
        )
    }
}

private fun androidx.compose.foundation.lazy.LazyListScope.continueReadingShelf(
    state: HomeDashboardUiState,
    onResume: (ReaderTarget) -> Unit,
    continueFocus: FocusRequester,
    readingFocus: FocusRequester,
) {
    if (state.continueReading.isEmpty()) return
    item("home-continue") {
        HomeSection("Continue Reading") {
            LazyRow(horizontalArrangement = Arrangement.spacedBy(MaterialTheme.hikariSpacing.space12)) {
                items(state.continueReading, key = { it.storyId.value }) { item ->
                    ContinueReadingCard(
                        item = item,
                        onResume = onResume,
                        focusRequester = continueFocus.takeIf { item == state.continueReading.first() },
                        downFocusRequester = readingFocus.takeIf { state.reading.isNotEmpty() },
                    )
                }
            }
        }
    }
}

private fun androidx.compose.foundation.lazy.LazyListScope.latestUpdatesShelf(
    updates: List<HomeUpdateItem>,
    onResume: (ReaderTarget) -> Unit,
    firstFocusRequester: FocusRequester?,
) {
    if (updates.isEmpty()) return
    item("home-updates") {
        HomeSection("Latest Updates") {
            LazyRow(horizontalArrangement = Arrangement.spacedBy(MaterialTheme.hikariSpacing.space12)) {
                items(updates, key = { it.releaseId.value }) { update ->
                    StoryUpdateCard(
                        content = StoryUpdateCardContent(
                            storyId = update.storyId,
                            title = update.title,
                            coverUrl = update.coverUrl,
                            chapterLabel = update.chapterLabel,
                            contentDescription =
                                "Read ${update.title}, ${update.chapterLabel}. Section Latest Updates",
                        ),
                        onClick = { onResume(update.readerTarget) },
                        variant = StoryUpdateCardVariant.SHELF,
                        traversalIndex = UPDATE_CARD_TRAVERSAL_INDEX,
                        modifier = Modifier.then(
                            if (update == updates.first() && firstFocusRequester != null) {
                                Modifier.focusRequester(firstFocusRequester)
                            } else {
                                Modifier
                            },
                        ),
                    )
                }
            }
        }
    }
}

private fun androidx.compose.foundation.lazy.LazyListScope.itemShelf(
    title: String,
    entries: List<HomeDashboardItem>,
    onStorySelected: (StoryId) -> Unit,
    firstFocusRequester: FocusRequester? = null,
) {
    if (entries.isEmpty()) return
    item("home-shelf-$title") {
        HomeSection(title) {
            LazyRow(horizontalArrangement = Arrangement.spacedBy(MaterialTheme.hikariSpacing.space12)) {
                items(entries, key = { it.storyId.value }) { item ->
                    DashboardStoryCard(
                        item,
                        title,
                        onStorySelected,
                        Modifier.then(
                            if (item == entries.first() && firstFocusRequester != null) {
                                Modifier.focusRequester(firstFocusRequester)
                            } else {
                                Modifier
                            },
                        ),
                    )
                }
            }
        }
    }
}

@Composable
private fun HomeSummary(summary: HomeReadingSummary) {
    Box(
        Modifier
            .fillMaxWidth()
            .height(MaterialTheme.hikariDimensions.dashboardFeatureHeight)
            .padding(MaterialTheme.hikariSpacing.space20),
    ) {
        Column(
            modifier = Modifier.align(Alignment.BottomStart),
            verticalArrangement = Arrangement.spacedBy(MaterialTheme.hikariSpacing.space8),
        ) {
            Text("Welcome back", style = MaterialTheme.typography.headlineMedium)
            Text("Your library, progress and newest chapters in one place.", style = MaterialTheme.typography.bodyLarge)
            Row(horizontalArrangement = Arrangement.spacedBy(MaterialTheme.hikariSpacing.space16)) {
                SummaryMetric(summary.libraryCount, "Library")
                SummaryMetric(summary.readingCount, "Reading")
                SummaryMetric(summary.completedCount, "Completed")
                SummaryMetric(summary.downloadedCount, "Offline")
            }
        }
    }
}

@Composable
private fun SummaryMetric(value: Int, label: String) {
    Column {
        Text(value.toString(), style = MaterialTheme.typography.titleLarge)
        Text(label, style = MaterialTheme.typography.labelMedium)
    }
}

@Composable
private fun HomeSection(title: String, content: @Composable () -> Unit) {
    Column(
        modifier = Modifier.padding(horizontal = MaterialTheme.hikariSpacing.space20),
        verticalArrangement = Arrangement.spacedBy(MaterialTheme.hikariSpacing.space8),
    ) {
        HikariSectionHeader(title)
        content()
    }
}

@Composable
private fun DashboardStoryCard(
    item: HomeDashboardItem,
    section: String,
    onSelected: (StoryId) -> Unit,
    modifier: Modifier = Modifier,
) {
    StoryPosterCard(
        storyId = item.storyId,
        title = item.title,
        coverUrl = item.coverUrl,
        contentDescription = "${item.title}. Section $section",
        onSelected = { onSelected(item.storyId) },
        traversalIndex = 2f,
        modifier = modifier.width(MaterialTheme.hikariDimensions.posterShelfWideWidth),
    )
}

@Composable
private fun ObservationFailure(failure: HomeDashboardFailure, modifier: Modifier = Modifier) {
    HikariInlineFeedback(
        message = "Some reading data could not be refreshed (${failure.code}).",
        modifier = modifier,
    )
}

private const val UPDATE_CARD_TRAVERSAL_INDEX = 3f
