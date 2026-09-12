package app.openstory.catalog.feature.discover

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.traversalIndex
import androidx.compose.ui.unit.Dp
import app.openstory.catalog.domain.asset.CoverAssetKey
import app.openstory.catalog.domain.identity.StorySourceRef
import app.openstory.catalog.domain.model.CatalogSectionCaps
import app.openstory.catalog.domain.model.CatalogSectionKind
import app.openstory.designsystem.content.HikariSectionHeader
import app.openstory.designsystem.state.HikariSkeleton
import app.openstory.designsystem.theme.hikariSpacing

internal fun LazyListScope.discoverSections(
    sections: List<DiscoverSectionUi>,
    horizontalInset: Dp,
    onStorySelected: (StorySourceRef, CoverAssetKey?) -> Unit,
    onCoverReady: () -> Unit,
) {
    sections.forEachIndexed { sectionIndex, section ->
        item(key = "section:${section.kind.name}") {
            Column(verticalArrangement = Arrangement.spacedBy(MaterialTheme.hikariSpacing.space12)) {
                SectionHeader(section.kind, horizontalInset)
                SectionContent(section, horizontalInset, onStorySelected, onCoverReady)
            }
        }
        if (section.kind == CatalogSectionKind.LATEST_UPDATES) {
            item(key = "editorial-quote-gap-before") {
                Spacer(Modifier.height(MaterialTheme.hikariSpacing.space24))
            }
            item(key = "editorial-quote-card") {
                EditorialQuoteCard(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = horizontalInset),
                )
            }
        }
        if (sectionIndex != sections.lastIndex) {
            item(key = "section-gap:${section.kind.name}") {
                Spacer(Modifier.height(MaterialTheme.hikariSpacing.space32))
            }
        }
    }
}

@Composable
private fun SectionHeader(kind: CatalogSectionKind, horizontalInset: Dp) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = horizontalInset)
            .testTag(DiscoverTestTags.section(kind))
            .semantics { traversalIndex = kind.traversalIndex },
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        HikariSectionHeader(title = kind.title)
        Text(
            text = "See All",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = DISABLED_LABEL_ALPHA),
        )
    }
}

@Composable
private fun SectionContent(
    section: DiscoverSectionUi,
    horizontalInset: Dp,
    onStorySelected: (StorySourceRef, CoverAssetKey?) -> Unit,
    onCoverReady: () -> Unit,
) {
    when (section.kind) {
        CatalogSectionKind.POPULAR -> PosterRail(
            cards = section.cards,
            cap = CatalogSectionCaps.cap(section.kind),
            horizontalInset = horizontalInset,
        ) { card ->
            DiscoverFeaturedStory(
                card = card,
                onSelected = { onStorySelected(card.ref, card.coverAssetKey) },
                onCoverReady = onCoverReady,
            )
        }
        CatalogSectionKind.LATEST_UPDATES -> PosterRail(
            cards = section.cards,
            cap = CatalogSectionCaps.cap(section.kind),
            horizontalInset = horizontalInset,
        ) { card ->
            DiscoverPosterTile(
                card = card,
                onSelected = { onStorySelected(card.ref, card.coverAssetKey) },
                onCoverReady = onCoverReady,
            )
        }
        CatalogSectionKind.TOP_RATED -> TopRatedList(
            section.cards,
            horizontalInset,
            onStorySelected,
            onCoverReady,
        )
    }
}

@Composable
private fun PosterRail(
    cards: List<DiscoverCardUi>,
    cap: Int,
    horizontalInset: Dp,
    content: @Composable (DiscoverCardUi) -> Unit,
) {
    LazyRow(
        contentPadding = PaddingValues(horizontal = horizontalInset),
        horizontalArrangement = Arrangement.spacedBy(MaterialTheme.hikariSpacing.space12),
    ) {
        items(
            items = cards.take(cap),
            key = { it.ref.storyId.value },
        ) { card -> content(card) }
    }
}

@Composable
private fun TopRatedList(
    cards: List<DiscoverCardUi>,
    horizontalInset: Dp,
    onStorySelected: (StorySourceRef, CoverAssetKey?) -> Unit,
    onCoverReady: () -> Unit,
) {
    val visibleCards = cards.take(CatalogSectionCaps.cap(CatalogSectionKind.TOP_RATED))
    Column(
        modifier = Modifier.padding(horizontal = horizontalInset),
        verticalArrangement = Arrangement.spacedBy(MaterialTheme.hikariSpacing.space4),
    ) {
        visibleCards.forEachIndexed { index, card ->
            DiscoverRankedStoryRow(
                index = index,
                card = card,
                isFinal = index == visibleCards.lastIndex,
                onSelected = { onStorySelected(card.ref, card.coverAssetKey) },
                onCoverReady = onCoverReady,
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

internal fun LazyListScope.loadingSections(horizontalInset: Dp) {
    popularLoadingSection(horizontalInset)
    loadingSectionGap("loading-major-gap-1")
    latestLoadingSection(horizontalInset)
    loadingSectionGap("loading-major-gap-2")
    topRatedLoadingSection(horizontalInset)
}

private fun LazyListScope.popularLoadingSection(horizontalInset: Dp) {
    item(key = "popular-skeleton") {
        SkeletonSection("Trending Now", horizontalInset) {
            LazyRow(horizontalArrangement = Arrangement.spacedBy(MaterialTheme.hikariSpacing.space12)) {
                items(CatalogSectionCaps.cap(CatalogSectionKind.POPULAR)) { index ->
                    HikariSkeleton(
                        modifier = Modifier
                            .width(DiscoverVisualMetrics.TrendingCoverWidth)
                            .height(DiscoverVisualMetrics.TrendingCoverHeight)
                            .then(
                                if (index == 0) {
                                    Modifier.testTag(DiscoverTestTags.POPULAR_SKELETON)
                                } else {
                                    Modifier
                                },
                            ),
                        shape = MaterialTheme.shapes.medium,
                    )
                }
            }
        }
    }
}

private fun LazyListScope.latestLoadingSection(horizontalInset: Dp) {
    item(key = "latest-skeleton") {
        SkeletonSection("Recommended for You", horizontalInset) {
            LazyRow(horizontalArrangement = Arrangement.spacedBy(MaterialTheme.hikariSpacing.space12)) {
                items(LATEST_SKELETON_COUNT) { index ->
                    HikariSkeleton(
                        modifier = Modifier
                            .width(DiscoverVisualMetrics.RecommendedCoverWidth)
                            .height(DiscoverVisualMetrics.RecommendedCoverHeight)
                            .then(
                                if (index == 0) {
                                    Modifier.testTag(DiscoverTestTags.LATEST_SKELETON)
                                } else {
                                    Modifier
                                },
                            ),
                        shape = MaterialTheme.shapes.medium,
                    )
                }
            }
        }
    }
}

private fun LazyListScope.topRatedLoadingSection(horizontalInset: Dp) {
    item(key = "top-rated-skeleton") {
        SkeletonSection("Top Rated", horizontalInset) {
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

private fun LazyListScope.loadingSectionGap(key: String) {
    item(key = key) {
        Spacer(Modifier.height(MaterialTheme.hikariSpacing.space32))
    }
}

@Composable
private fun SkeletonSection(
    title: String,
    horizontalInset: Dp,
    content: @Composable () -> Unit,
) {
    Column(
        modifier = Modifier.padding(horizontal = horizontalInset),
        verticalArrangement = Arrangement.spacedBy(MaterialTheme.hikariSpacing.space12),
    ) {
        HikariSectionHeader(title)
        content()
    }
}

private val CatalogSectionKind.title: String
    get() = when (this) {
        CatalogSectionKind.POPULAR -> "Trending Now"
        CatalogSectionKind.LATEST_UPDATES -> "Recommended for You"
        CatalogSectionKind.TOP_RATED -> "Top Rated"
    }

private val CatalogSectionKind.traversalIndex: Float
    get() = when (this) {
        CatalogSectionKind.POPULAR -> 0f
        CatalogSectionKind.LATEST_UPDATES -> 1f
        CatalogSectionKind.TOP_RATED -> 2f
    }

private const val LATEST_SKELETON_COUNT = 3
private const val DISABLED_LABEL_ALPHA = 0.65f
