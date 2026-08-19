package app.openstory.catalog.ui.dashboard

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import app.openstory.catalog.ui.components.ReaderTarget
import app.openstory.catalog.ui.components.StoryPosterCard
import app.openstory.catalog.ui.components.StoryUpdateCard
import app.openstory.catalog.ui.components.StoryUpdateCardContent
import app.openstory.catalog.ui.components.StoryUpdateCardVariant
import app.openstory.common.id.StoryId
import app.openstory.designsystem.content.HikariSectionHeader
import app.openstory.designsystem.theme.hikariDimensions
import app.openstory.designsystem.theme.hikariSpacing

internal fun LazyListScope.continueReadingShelf(
    state: HomeDashboardUiState,
    onResume: (ReaderTarget) -> Unit,
    continueFocus: FocusRequester,
    readingFocus: FocusRequester,
) {
    if (state.continueReading.isEmpty()) return
    item("home-continue") {
        HomeSection("Continue Reading") {
            LazyRow(horizontalArrangement = Arrangement.spacedBy(MaterialTheme.hikariSpacing.itemGap)) {
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

internal fun LazyListScope.latestUpdatesShelf(
    updates: List<HomeUpdateItem>,
    onStorySelected: (StoryId) -> Unit,
    onResume: (ReaderTarget) -> Unit,
    firstFocusRequester: FocusRequester?,
) {
    if (updates.isEmpty()) return
    item("home-updates") {
        HomeSection("Latest Updates") {
            LazyRow(horizontalArrangement = Arrangement.spacedBy(MaterialTheme.hikariSpacing.itemGap)) {
                items(updates, key = { it.releaseId.value }) { update ->
                    StoryUpdateCard(
                        content = StoryUpdateCardContent(
                            storyId = update.storyId,
                            title = update.title,
                            coverUrl = update.coverUrl,
                            chapterLabel = update.chapterLabel,
                            contentDescription = if (update.readerTarget != null) {
                                "Read ${update.title}, ${update.chapterLabel}. Section Latest Updates"
                            } else {
                                "Open ${update.title}, ${update.chapterLabel}. Section Latest Updates"
                            },
                        ),
                        onClick = {
                            update.readerTarget?.let(onResume) ?: onStorySelected(update.storyId)
                        },
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

internal fun LazyListScope.libraryShelves(
    state: HomeDashboardUiState,
    onStorySelected: (StoryId) -> Unit,
    firstContentFocusRequester: FocusRequester?,
    readingFocusRequester: FocusRequester,
) {
    itemShelf(
        "Reading",
        state.reading,
        onStorySelected,
        firstContentFocusRequester.takeIf { state.continueReading.isEmpty() } ?: readingFocusRequester,
    )
    itemShelf(
        "Planned",
        state.planned,
        onStorySelected,
        firstContentFocusRequester.takeIf { state.continueReading.isEmpty() && state.reading.isEmpty() },
    )
    itemShelf(
        "Paused",
        state.paused,
        onStorySelected,
        firstContentFocusRequester.takeIf {
            state.continueReading.isEmpty() && state.reading.isEmpty() && state.planned.isEmpty()
        },
    )
    itemShelf(
        "Completed",
        state.completed,
        onStorySelected,
        firstContentFocusRequester.takeIf {
            state.continueReading.isEmpty() && state.reading.isEmpty() && state.planned.isEmpty() &&
                state.paused.isEmpty()
        },
    )
}

internal fun LazyListScope.itemShelf(
    title: String,
    entries: List<HomeDashboardItem>,
    onStorySelected: (StoryId) -> Unit,
    firstFocusRequester: FocusRequester? = null,
) {
    if (entries.isEmpty()) return
    item("home-shelf-$title") {
        HomeSection(title) {
            LazyRow(horizontalArrangement = Arrangement.spacedBy(MaterialTheme.hikariSpacing.itemGap)) {
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
private fun HomeSection(title: String, content: @Composable () -> Unit) {
    Column(
        verticalArrangement = Arrangement.spacedBy(MaterialTheme.hikariSpacing.sectionContentGap),
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

private const val UPDATE_CARD_TRAVERSAL_INDEX = 3f
