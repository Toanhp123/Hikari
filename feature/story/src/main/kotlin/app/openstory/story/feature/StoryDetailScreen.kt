package app.openstory.story.feature

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import app.openstory.catalog.domain.asset.CoverAssetKey
import app.openstory.catalog.domain.asset.CoverLocator
import app.openstory.designsystem.feedback.HikariInlineFeedback
import app.openstory.designsystem.control.HikariIconAction
import app.openstory.designsystem.control.HikariIconActionStyle
import app.openstory.designsystem.state.HikariSkeleton
import app.openstory.designsystem.theme.HikariBreakpoints
import app.openstory.designsystem.theme.hikariSpacing
import app.openstory.story.feature.presentation.BackArrowIcon

@Composable
internal fun StoryDetailScreen(
    state: StoryDetailUiState,
    onBack: () -> Unit,
    onRetry: () -> Unit,
    onLibraryToggle: () -> Unit = {},
    artworkContent: @Composable (String, CoverLocator?, CoverAssetKey?, Modifier) -> Unit =
        { title, _, _, modifier -> StoryArtworkPlaceholder(title, modifier) },
    onHeroMaterialized: () -> Unit = {},
    onBodyMaterialized: () -> Unit = {},
) {
    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        StoryDetailContent(
            state = state,
            layout = storyLayout(maxWidth),
            onBack = onBack,
            onRetry = onRetry,
            onLibraryToggle = onLibraryToggle,
            artworkContent = artworkContent,
            onHeroMaterialized = onHeroMaterialized,
            onBodyMaterialized = onBodyMaterialized,
        )
    }
}

@Composable
private fun StoryDetailContent(
    state: StoryDetailUiState,
    layout: StoryLayoutMetrics,
    onBack: () -> Unit,
    onRetry: () -> Unit,
    onLibraryToggle: () -> Unit,
    artworkContent: @Composable (String, CoverLocator?, CoverAssetKey?, Modifier) -> Unit,
    onHeroMaterialized: () -> Unit,
    onBodyMaterialized: () -> Unit,
) {
    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .statusBarsPadding()
            .testTag(StoryTestTags.ROOT),
        contentPadding = PaddingValues(
            start = layout.screenInset,
            end = layout.screenInset,
            bottom = MaterialTheme.hikariSpacing.space32,
        ),
        verticalArrangement = Arrangement.spacedBy(MaterialTheme.hikariSpacing.space24),
    ) {
        item(key = "story-back") { StoryTopBar(onBack) }
        item(key = "story-hero") {
            StoryHero(
                hero = state.toHeroUi(),
                identityGap = layout.identityGap,
                artworkContent = artworkContent,
                modifier = Modifier.fillMaxWidth(),
                onMaterialized = onHeroMaterialized,
            )
        }
        if (state.canPresentLibraryAction && state.detail == null) {
            item(key = "story-actions") {
                StoryLibraryAction(
                    libraryMembership = state.libraryMembership,
                    libraryMutationFailed = state.libraryMutationFailed,
                    onLibraryToggle = onLibraryToggle,
                )
            }
        }
        state.issue?.let { issue -> item(key = "story-issue") { StoryIssue(issue.retryable, onRetry) } }
        if (state.detailLoading && state.detail == null) {
            item(key = "story-detail-loading") { StoryMetadataSkeleton() }
        }
        state.detail?.let { detail -> item(key = "story-detail") {
            SideEffect(onBodyMaterialized)
            StoryMetadataSections(
                detail = detail,
                libraryMembership = state.libraryMembership,
                libraryMutationFailed = state.libraryMutationFailed,
                onLibraryToggle = onLibraryToggle,
                modifier = Modifier.fillMaxWidth(),
            )
        } }
    }
}

private val STORY_TOP_BAR_HEIGHT = 48.dp
private val BACK_ICON_SIZE = 18.dp

@Composable
private fun StoryTopBar(onBack: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().height(STORY_TOP_BAR_HEIGHT),
        horizontalArrangement = Arrangement.Start,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        HikariIconAction(
            onClick = onBack,
            contentDescription = "Back",
            style = HikariIconActionStyle.OVERLAY,
        ) {
            BackArrowIcon(
                size = BACK_ICON_SIZE,
                tint = MaterialTheme.colorScheme.onSurface,
            )
        }
    }
}

@Composable
private fun StoryIssue(retryable: Boolean, onRetry: () -> Unit) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.errorContainer,
        shape = MaterialTheme.shapes.large,
    ) {
        HikariInlineFeedback(
            message = "Story metadata could not be refreshed.",
            modifier = Modifier.padding(MaterialTheme.hikariSpacing.space16),
            actionLabel = "Try again".takeIf { retryable },
            onAction = onRetry.takeIf { retryable },
        )
    }
}

@Composable
private fun StoryMetadataSkeleton() {
    Column(
        modifier = Modifier.fillMaxWidth().testTag("story-detail-skeleton"),
        verticalArrangement = Arrangement.spacedBy(MaterialTheme.hikariSpacing.space12),
    ) {
        HikariSkeleton(
            modifier = Modifier.fillMaxWidth(METADATA_HEADING_SKELETON_WIDTH_FRACTION).height(26.dp),
            shape = MaterialTheme.shapes.small,
        )
        HikariSkeleton(
            modifier = Modifier.fillMaxWidth().height(72.dp),
            shape = MaterialTheme.shapes.medium,
        )
    }
}

@Composable
private fun storyLayout(width: Dp): StoryLayoutMetrics = if (HikariBreakpoints.isWide(width)) {
    StoryLayoutMetrics(
        screenInset = HikariBreakpoints.screenHorizontalInset(width),
        identityGap = MaterialTheme.hikariSpacing.space24,
    )
} else {
    StoryLayoutMetrics(
        screenInset = HikariBreakpoints.screenHorizontalInset(width),
        identityGap = MaterialTheme.hikariSpacing.space16,
    )
}

private data class StoryLayoutMetrics(
    val screenInset: Dp,
    val identityGap: Dp,
)

private const val METADATA_HEADING_SKELETON_WIDTH_FRACTION = 0.3f
