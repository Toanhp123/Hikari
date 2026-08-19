package app.openstory.catalog.ui.chapters

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.calculateEndPadding
import androidx.compose.foundation.layout.calculateStartPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import app.openstory.catalog.ui.feedback.catalogFailureMessage
import app.openstory.common.id.CanonicalChapterId
import app.openstory.common.id.StoryId
import app.openstory.designsystem.content.HikariDisclosureRow
import app.openstory.designsystem.content.HikariMetadataBadge
import app.openstory.designsystem.content.hikariSectionHeader
import app.openstory.designsystem.content.HikariSectionTitle
import app.openstory.designsystem.control.HikariCompactIconAction
import app.openstory.designsystem.feedback.HikariInlineFeedback
import app.openstory.designsystem.icon.HikariFilterGlyph
import app.openstory.designsystem.navigation.HikariPagination
import app.openstory.designsystem.refresh.HikariPullToRefresh
import app.openstory.designsystem.state.HikariEmptyState
import app.openstory.designsystem.theme.hikariDimensions
import app.openstory.designsystem.theme.hikariSpacing
import kotlinx.coroutines.launch

@Composable
fun ChapterList(
    state: ChapterListUiState,
    actions: ChapterListActions,
    modifier: Modifier = Modifier,
    contentPadding: PaddingValues? = null,
) {
    val pageCount = chapterPageCount(state.chapters.size)
    var requestedPage by rememberSaveable(
        state.storyId?.value,
        state.selectedFilter.name,
        state.showTombstones,
    ) { mutableIntStateOf(1) }
    val currentPage = requestedPage.coerceIn(1, pageCount)
    val visibleChapters = state.chapters.chapterPage(currentPage)
    val expandedChapterIds = remember(
        state.storyId,
        state.selectedFilter,
        state.showTombstones,
    ) { mutableStateListOf<CanonicalChapterId>() }
    val listState = rememberLazyListState()
    val headerScrolled by remember(listState) {
        derivedStateOf { listState.canScrollBackward }
    }
    val layoutDirection = LocalLayoutDirection.current
    val resolvedContentPadding = contentPadding ?: PaddingValues(MaterialTheme.hikariSpacing.screenGutter)
    val headerTopPadding = resolvedContentPadding.calculateTopPadding()
    val listContentPadding = PaddingValues(
        start = resolvedContentPadding.calculateStartPadding(layoutDirection),
        top = MaterialTheme.hikariDimensions.zero,
        end = resolvedContentPadding.calculateEndPadding(layoutDirection),
        bottom = resolvedContentPadding.calculateBottomPadding(),
    )
    val scope = rememberCoroutineScope()
    var optionsVisible by rememberSaveable(state.storyId?.value) { mutableStateOf(false) }
    val visibleReleaseIds = visibleChapters.flatMap { chapter ->
        chapter.releases.filter(ChapterReleaseUiModel::downloadCapable).map(ChapterReleaseUiModel::id)
    }

    LaunchedEffect(pageCount) {
        if (requestedPage != currentPage) {
            requestedPage = currentPage
            expandedChapterIds.clear()
            listState.scrollToItem(0)
        }
    }

    val onPageSelected: (Int) -> Unit = { page ->
        val selectedPage = page.coerceIn(1, pageCount)
        if (selectedPage != currentPage) {
            requestedPage = selectedPage
            expandedChapterIds.clear()
            scope.launch { listState.scrollToItem(0) }
        }
    }

    HikariPullToRefresh(
        refreshing = state.refreshing,
        onRefresh = actions.onRefresh,
        modifier = modifier.testTag("chapter-pull-refresh"),
    ) {
        LazyColumn(
            state = listState,
            modifier = Modifier.fillMaxWidth().testTag("chapter-list"),
            contentPadding = listContentPadding,
            verticalArrangement = Arrangement.spacedBy(MaterialTheme.hikariDimensions.zero),
        ) {
            chapterListItems(
                state = state,
                chapters = visibleChapters,
                actions = actions,
                currentPage = currentPage,
                pageCount = pageCount,
                expandedChapterIds = expandedChapterIds,
                onPageSelected = onPageSelected,
                onOpenOptions = { optionsVisible = true },
                headerTopPadding = headerTopPadding,
                headerScrolled = headerScrolled,
                onToggleChapter = { chapterId ->
                    if (chapterId in expandedChapterIds) {
                        expandedChapterIds.remove(chapterId)
                    } else {
                        expandedChapterIds.add(chapterId)
                    }
                },
            )
        }
    }

    if (optionsVisible) {
        ChapterFiltersSheet(
            state = state,
            actions = actions,
            visibleReleaseIds = visibleReleaseIds,
            onDismiss = { optionsVisible = false },
        )
    }
}

fun LazyListScope.chapterListItems(
    state: ChapterListUiState,
    chapters: List<ChapterItemUiModel>,
    actions: ChapterListActions,
    currentPage: Int,
    pageCount: Int,
    expandedChapterIds: Collection<CanonicalChapterId>,
    onPageSelected: (Int) -> Unit,
    onOpenOptions: () -> Unit,
    headerTopPadding: Dp,
    headerScrolled: Boolean,
    onToggleChapter: (CanonicalChapterId) -> Unit,
) {
    hikariSectionHeader(
        key = "chapter-summary",
        title = "Chapters",
        subtitle = state.chapterCount.chapterCountLabel(),
        sticky = true,
        contentType = "chapter-header",
        topPadding = headerTopPadding,
        stickyBottomSeparation = true,
        stickyBottomSeparationEnabled = headerScrolled,
        action = {
            if (pageCount > 1) {
                HikariPagination(
                    currentPage = currentPage,
                    pageCount = pageCount,
                    onPageSelected = onPageSelected,
                    modifier = Modifier.testTag("chapter-pagination"),
                )
            }
            HikariCompactIconAction(
                onClick = onOpenOptions,
                contentDescription = "Chapter options",
            ) {
                HikariFilterGlyph()
            }
        },
    )
    state.failure?.let { failure ->
        item(key = "chapter-failure", contentType = "chapter-feedback") {
            HikariInlineFeedback(message = catalogFailureMessage(failure, "Couldn't refresh chapters."))
        }
    }
    if (state.loading && state.chapters.isEmpty()) {
        item(key = "chapter-loading", contentType = "chapter-progress") {
            LinearProgressIndicator(
                modifier = Modifier.fillMaxWidth().testTag("chapter-loading"),
            )
        }
        return
    }
    if (state.chapters.isEmpty()) {
        item(key = "chapter-empty", contentType = "chapter-empty") {
            HikariEmptyState(title = "No chapters available")
        }
        return
    }

    val storyId = requireNotNull(state.storyId)
    var previousVolume: String? = null
    chapters.forEachIndexed { index, chapter ->
        val volume = chapter.volumeLabel
        if (volume != null && volume != previousVolume) {
            item(
                key = "chapter-volume:$volume",
                contentType = "chapter-volume",
            ) {
                HikariSectionTitle(
                    title = volume,
                    modifier = Modifier.padding(top = MaterialTheme.hikariSpacing.space8),
                )
            }
        }
        previousVolume = volume
        chapterItem(
            chapter = chapter,
            storyId = storyId,
            actions = actions,
            isFirst = index == 0,
            expanded = chapter.id in expandedChapterIds,
            onToggle = { onToggleChapter(chapter.id) },
        )
    }

}

private fun LazyListScope.chapterItem(
    chapter: ChapterItemUiModel,
    storyId: StoryId,
    actions: ChapterListActions,
    isFirst: Boolean,
    expanded: Boolean,
    onToggle: () -> Unit,
) {
    item(
        key = "chapter:${chapter.id.value}",
        contentType = "chapter-group-header",
    ) {
        val benchmarkModifier = if (isFirst) Modifier.testTag("chapter-summary-first") else Modifier
        ChapterGroupHeader(
            chapter = chapter,
            expanded = expanded,
            onToggle = onToggle,
            modifier = benchmarkModifier
                .padding(top = if (isFirst) MaterialTheme.hikariDimensions.zero else MaterialTheme.hikariSpacing.space8)
                .testTag("chapter-toggle:${chapter.id.value}")
                .semantics(mergeDescendants = false) {
                    contentDescription = chapter.accessibilityDescription()
                },
        )
    }

    if (expanded) {
        items(
            items = chapter.releases,
            key = { release -> "chapter:${chapter.id.value}:release:${release.id.value}" },
            contentType = { "chapter-release" },
        ) { release ->
            Column(modifier = Modifier.fillMaxWidth()) {
                ChapterReleaseRow(
                    release = release,
                    chapterId = chapter.id,
                    storyId = storyId,
                    onKeepGrouped = actions.onKeepGrouped,
                    onSeparate = actions.onSeparate,
                    onRead = actions.onRead,
                    downloadState = actions.downloadState(release.id),
                    pendingRemoval = actions.pendingRemoval == release.id,
                    downloadActions = actions.downloadActions,
                )
                ChapterDivider()
            }
        }
    }
}

@Composable
private fun ChapterGroupHeader(
    chapter: ChapterItemUiModel,
    expanded: Boolean,
    onToggle: () -> Unit,
    modifier: Modifier = Modifier,
) {
    HikariDisclosureRow(
        title = chapter.label,
        subtitle = chapter.title,
        expanded = expanded,
        onToggle = onToggle,
        modifier = modifier,
        trailing = {
            when {
                chapter.tombstoned -> HikariMetadataBadge("Unavailable")
                chapter.releases.size > 1 -> HikariMetadataBadge(chapter.releaseCountLabel())
            }
        },
    )
}

@Composable
private fun ChapterDivider() {
    HorizontalDivider(
        thickness = MaterialTheme.hikariDimensions.borderThin,
        color = MaterialTheme.colorScheme.outlineVariant,
    )
}

private fun Int.chapterCountLabel(): String = "$this ${if (this == 1) "chapter" else "chapters"}"

private fun ChapterItemUiModel.releaseCountLabel(): String =
    "${releases.size} ${if (releases.size == 1) "source" else "sources"}"

private fun ChapterItemUiModel.accessibilityDescription(): String = buildString {
    append(label)
    title?.let { append(", ").append(it) }
    append(", ${releases.size}")
    append(if (releases.size == 1) " release" else " releases")
    if (tombstoned) append(", unavailable")
}
