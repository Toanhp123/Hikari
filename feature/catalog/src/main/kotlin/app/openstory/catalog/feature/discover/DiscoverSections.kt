package app.openstory.catalog.feature.discover

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.traversalIndex
import androidx.compose.ui.unit.Dp
import app.openstory.catalog.domain.model.CatalogSectionCaps
import app.openstory.catalog.domain.model.CatalogSectionKind
import app.openstory.designsystem.content.HikariSectionHeader
import app.openstory.designsystem.content.HikariPosterRail
import app.openstory.designsystem.content.HikariPosterGeometry
import app.openstory.designsystem.content.HikariPosterSkeleton
import app.openstory.designsystem.state.HikariSkeleton
import app.openstory.designsystem.theme.hikariSpacing

internal fun LazyListScope.discoverSections(
    sections: List<DiscoverSectionUi>,
    horizontalInset: Dp,
    sectionLabels: DiscoverSectionLabels,
    onStorySelected: (DiscoverCardUi) -> Unit,
    onCoverReady: () -> Unit,
) {
    sections.forEachIndexed { sectionIndex, section ->
        item(key = "section:${section.kind.name}") {
            Column(verticalArrangement = Arrangement.spacedBy(MaterialTheme.hikariSpacing.space12)) {
                SectionHeader(section.kind, horizontalInset, sectionLabels)
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
private fun SectionHeader(
    kind: CatalogSectionKind,
    horizontalInset: Dp,
    sectionLabels: DiscoverSectionLabels,
) {
    HikariSectionHeader(
        title = sectionLabels.title(kind),
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = horizontalInset)
            .testTag(DiscoverTestTags.section(kind))
            .semantics { traversalIndex = kind.traversalIndex },
    )
}

@Composable
private fun SectionContent(
    section: DiscoverSectionUi,
    horizontalInset: Dp,
    onStorySelected: (DiscoverCardUi) -> Unit,
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
                onSelected = { onStorySelected(card) },
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
                onSelected = { onStorySelected(card) },
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
    HikariPosterRail(
        contentPadding = PaddingValues(horizontal = horizontalInset),
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
    onStorySelected: (DiscoverCardUi) -> Unit,
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
                onSelected = { onStorySelected(card) },
                onCoverReady = onCoverReady,
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

internal fun LazyListScope.loadingSections(horizontalInset: Dp, sectionLabels: DiscoverSectionLabels) {
    popularLoadingSection(horizontalInset, sectionLabels.popular)
    loadingSectionGap("loading-major-gap-1")
    latestLoadingSection(horizontalInset, sectionLabels.latestUpdates)
    loadingSectionGap("loading-major-gap-2")
    topRatedLoadingSection(horizontalInset, sectionLabels.topRated)
}

private fun LazyListScope.popularLoadingSection(horizontalInset: Dp, title: String) {
    item(key = "popular-skeleton") {
        SkeletonSection(title, horizontalInset) {
            HikariPosterRail {
                items(CatalogSectionCaps.cap(CatalogSectionKind.POPULAR)) { index ->
                    HikariPosterSkeleton(
                        modifier = Modifier.width(DiscoverVisualMetrics.TrendingCoverWidth),
                        geometry = HikariPosterGeometry.Featured,
                        artworkModifier = Modifier.then(
                                if (index == 0) {
                                    Modifier.testTag(DiscoverTestTags.POPULAR_SKELETON)
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

private fun LazyListScope.latestLoadingSection(horizontalInset: Dp, title: String) {
    item(key = "latest-skeleton") {
        SkeletonSection(title, horizontalInset) {
            HikariPosterRail {
                items(LATEST_SKELETON_COUNT) { index ->
                    HikariPosterSkeleton(
                        modifier = Modifier.width(DiscoverVisualMetrics.LatestUpdatesCoverWidth),
                        geometry = HikariPosterGeometry.Standard,
                        artworkModifier = Modifier.then(
                                if (index == 0) {
                                    Modifier.testTag(DiscoverTestTags.LATEST_SKELETON)
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

private fun LazyListScope.topRatedLoadingSection(horizontalInset: Dp, title: String) {
    item(key = "top-rated-skeleton") {
        SkeletonSection(title, horizontalInset) {
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


private val CatalogSectionKind.traversalIndex: Float
    get() = when (this) {
        CatalogSectionKind.POPULAR -> 0f
        CatalogSectionKind.LATEST_UPDATES -> 1f
        CatalogSectionKind.TOP_RATED -> 2f
    }

private const val LATEST_SKELETON_COUNT = 3
