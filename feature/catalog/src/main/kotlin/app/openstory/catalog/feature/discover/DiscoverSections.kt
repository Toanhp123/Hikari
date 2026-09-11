package app.openstory.catalog.feature.discover

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.traversalIndex
import app.openstory.catalog.domain.asset.CoverAssetKey
import app.openstory.catalog.domain.identity.StorySourceRef
import app.openstory.catalog.domain.model.CatalogSectionKind
import app.openstory.designsystem.content.HikariSectionHeader
import app.openstory.designsystem.state.HikariSkeleton
import app.openstory.designsystem.theme.hikariSpacing

internal fun LazyListScope.discoverSections(
    sections: List<DiscoverSectionUi>,
    onStorySelected: (StorySourceRef, CoverAssetKey?) -> Unit,
) {
    sections.forEachIndexed { sectionIndex, section ->
        item(key = "section:${section.kind.name}") {
            Column(verticalArrangement = Arrangement.spacedBy(MaterialTheme.hikariSpacing.space12)) {
                SectionHeader(section.kind)
                when (section.kind) {
                    CatalogSectionKind.POPULAR -> PopularRail(section.cards, onStorySelected)
                    CatalogSectionKind.LATEST_UPDATES -> LatestRail(section.cards, onStorySelected)
                    CatalogSectionKind.TOP_RATED -> TopRatedList(section.cards, onStorySelected)
                }
            }
        }
        if (sectionIndex != sections.lastIndex) {
            item(key = "section-gap:${section.kind.name}") {
                androidx.compose.foundation.layout.Spacer(
                    Modifier.height(MaterialTheme.hikariSpacing.space32),
                )
            }
        }
    }
}

@Composable
private fun SectionHeader(kind: CatalogSectionKind) {
    HikariSectionHeader(
        title = kind.title,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = MaterialTheme.hikariSpacing.space20)
            .testTag(DiscoverTestTags.section(kind))
            .semantics { traversalIndex = kind.traversalIndex },
    )
}

@Composable
private fun PopularRail(
    cards: List<DiscoverCardUi>,
    onStorySelected: (StorySourceRef, CoverAssetKey?) -> Unit,
) {
    LazyRow(
        contentPadding = PaddingValues(horizontal = MaterialTheme.hikariSpacing.space20),
        horizontalArrangement = Arrangement.spacedBy(MaterialTheme.hikariSpacing.space12),
    ) {
        items(cards.take(POPULAR_CAP), key = { it.ref.storyId.value }) { card ->
            DiscoverFeaturedStory(card = card, onSelected = { onStorySelected(card.ref, card.coverAssetKey) })
        }
    }
}

@Composable
private fun LatestRail(
    cards: List<DiscoverCardUi>,
    onStorySelected: (StorySourceRef, CoverAssetKey?) -> Unit,
) {
    LazyRow(
        contentPadding = PaddingValues(horizontal = MaterialTheme.hikariSpacing.space20),
        horizontalArrangement = Arrangement.spacedBy(MaterialTheme.hikariSpacing.space12),
    ) {
        items(cards.take(LATEST_CAP), key = { it.ref.storyId.value }) { card ->
            DiscoverPosterTile(card = card, onSelected = { onStorySelected(card.ref, card.coverAssetKey) })
        }
    }
}

@Composable
private fun TopRatedList(
    cards: List<DiscoverCardUi>,
    onStorySelected: (StorySourceRef, CoverAssetKey?) -> Unit,
) {
    Column(
        modifier = Modifier.padding(horizontal = MaterialTheme.hikariSpacing.space20),
        verticalArrangement = Arrangement.spacedBy(MaterialTheme.hikariSpacing.space4),
    ) {
        cards.take(TOP_RATED_CAP).forEachIndexed { index, card ->
            DiscoverRankedStoryRow(
                index = index,
                card = card,
                isFinal = index == minOf(cards.size, TOP_RATED_CAP) - 1,
                onSelected = { onStorySelected(card.ref, card.coverAssetKey) },
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

internal fun LazyListScope.loadingSections() {
    item(key = "popular-skeleton") {
        SkeletonSection("Popular") {
            HikariSkeleton(
                modifier = Modifier
                    .width(DiscoverVisualMetrics.PopularCardWidth)
                    .height(DiscoverVisualMetrics.PopularCardHeight)
                    .testTag(DiscoverTestTags.POPULAR_SKELETON),
                shape = MaterialTheme.shapes.medium,
            )
        }
    }
    item(key = "loading-major-gap-1") {
        androidx.compose.foundation.layout.Spacer(Modifier.height(MaterialTheme.hikariSpacing.space32))
    }
    item(key = "latest-skeleton") {
        SkeletonSection("Latest Updates") {
            LazyRow(horizontalArrangement = Arrangement.spacedBy(MaterialTheme.hikariSpacing.space12)) {
                items(LATEST_SKELETON_COUNT) { index ->
                    HikariSkeleton(
                        modifier = Modifier
                            .width(DiscoverVisualMetrics.LatestCoverWidth)
                            .height(DiscoverVisualMetrics.LatestCoverHeight)
                            .then(if (index == 0) Modifier.testTag(DiscoverTestTags.LATEST_SKELETON) else Modifier),
                        shape = MaterialTheme.shapes.small,
                    )
                }
            }
        }
    }
    item(key = "loading-major-gap-2") {
        androidx.compose.foundation.layout.Spacer(Modifier.height(MaterialTheme.hikariSpacing.space32))
    }
    item(key = "top-rated-skeleton") {
        SkeletonSection("Top Rated") {
            HikariSkeleton(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(DiscoverVisualMetrics.TopRatedRowMinHeight)
                    .testTag(DiscoverTestTags.TOP_RATED_SKELETON),
                shape = MaterialTheme.shapes.medium,
            )
        }
    }
}

@Composable
private fun SkeletonSection(title: String, content: @Composable () -> Unit) {
    Column(
        modifier = Modifier.padding(horizontal = MaterialTheme.hikariSpacing.space20),
        verticalArrangement = Arrangement.spacedBy(MaterialTheme.hikariSpacing.space12),
    ) {
        HikariSectionHeader(title)
        content()
    }
}

private val CatalogSectionKind.title: String
    get() = when (this) {
        CatalogSectionKind.POPULAR -> "Popular"
        CatalogSectionKind.LATEST_UPDATES -> "Latest Updates"
        CatalogSectionKind.TOP_RATED -> "Top Rated"
    }

private val CatalogSectionKind.traversalIndex: Float
    get() = when (this) {
        CatalogSectionKind.POPULAR -> 0f
        CatalogSectionKind.LATEST_UPDATES -> 1f
        CatalogSectionKind.TOP_RATED -> 2f
    }

private const val POPULAR_CAP = 5
private const val LATEST_CAP = 9
private const val TOP_RATED_CAP = 5
private const val LATEST_SKELETON_COUNT = 3
