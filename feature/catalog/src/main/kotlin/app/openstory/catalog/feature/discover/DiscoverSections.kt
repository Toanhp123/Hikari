package app.openstory.catalog.feature.discover

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.traversalIndex
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.openstory.catalog.domain.asset.CoverAssetKey
import app.openstory.catalog.domain.identity.StorySourceRef
import app.openstory.catalog.domain.model.CatalogSectionKind
import app.openstory.catalog.feature.assets.CoverArtwork
import app.openstory.designsystem.content.HikariSectionHeader
import app.openstory.designsystem.state.HikariSkeleton
import app.openstory.designsystem.theme.hikariSpacing

internal fun LazyListScope.discoverSections(
    sections: List<DiscoverSectionUi>,
    onStorySelected: (StorySourceRef, CoverAssetKey?) -> Unit,
) {
    sections.forEach { section ->
        item(key = "section-header:${section.kind.name}") { SectionHeader(section.kind) }
        when (section.kind) {
            CatalogSectionKind.POPULAR -> item(key = "section-carousel:${section.kind.name}") {
                PopularCards(section.cards, onStorySelected)
            }
            CatalogSectionKind.LATEST_UPDATES -> items(
                items = section.cards,
                key = { card -> "section-card:${section.kind.name}:${card.ref.storyId.value}" },
            ) { card -> LatestCard(card, onStorySelected) }
            CatalogSectionKind.TOP_RATED -> itemsIndexed(
                items = section.cards,
                key = { _, card -> "section-card:${section.kind.name}:${card.ref.storyId.value}" },
            ) { index, card -> TopRatedCard(index, card, onStorySelected) }
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
            .semantics {
                traversalIndex = kind.traversalIndex
            },
    )
}

@Composable
private fun PopularCards(
    cards: List<DiscoverCardUi>,
    onStorySelected: (StorySourceRef, CoverAssetKey?) -> Unit,
) {
    LazyRow(
        contentPadding = androidx.compose.foundation.layout.PaddingValues(
            horizontal = MaterialTheme.hikariSpacing.space20,
        ),
        horizontalArrangement = Arrangement.spacedBy(MaterialTheme.hikariSpacing.space12),
    ) {
        items(cards, key = { card -> card.ref.storyId.value }) { card ->
            Surface(
                modifier = Modifier
                    .width(248.dp)
                    .testTag(DiscoverTestTags.card(CatalogSectionKind.POPULAR, card.ref))
                    .semantics { contentDescription = card.title }
                    .clickable { onStorySelected(card.ref, card.coverAssetKey) },
                shape = MaterialTheme.shapes.large,
                color = MaterialTheme.colorScheme.secondaryContainer,
            ) {
                Column {
                    CoverArtwork(
                        title = card.title,
                        locator = card.coverLocator,
                        assetKey = card.coverAssetKey,
                        modifier = Modifier
                            .fillMaxWidth()
                            .aspectRatio(POPULAR_COVER_ASPECT_RATIO),
                    )
                    Text(
                        text = card.title,
                        style = MaterialTheme.typography.titleLarge,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.padding(MaterialTheme.hikariSpacing.space16),
                    )
                }
            }
        }
    }
}

@Composable
private fun LatestCard(
    card: DiscoverCardUi,
    onStorySelected: (StorySourceRef, CoverAssetKey?) -> Unit,
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = MaterialTheme.hikariSpacing.space20)
            .testTag(DiscoverTestTags.card(CatalogSectionKind.LATEST_UPDATES, card.ref))
            .semantics { contentDescription = card.title }
            .clickable { onStorySelected(card.ref, card.coverAssetKey) },
        shape = MaterialTheme.shapes.medium,
        tonalElevation = 1.dp,
    ) {
        Row(
            modifier = Modifier.padding(MaterialTheme.hikariSpacing.space12),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(MaterialTheme.hikariSpacing.space12),
        ) {
            CoverArtwork(
                title = card.title,
                locator = card.coverLocator,
                assetKey = card.coverAssetKey,
                modifier = Modifier.size(width = 64.dp, height = 82.dp),
            )
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = card.title,
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                card.supportingLabel?.let { label ->
                    Text(
                        text = label,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

@Composable
private fun TopRatedCard(
    index: Int,
    card: DiscoverCardUi,
    onStorySelected: (StorySourceRef, CoverAssetKey?) -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = MaterialTheme.hikariSpacing.space20)
            .testTag(DiscoverTestTags.card(CatalogSectionKind.TOP_RATED, card.ref))
            .semantics { contentDescription = card.title }
            .clip(MaterialTheme.shapes.medium)
            .clickable { onStorySelected(card.ref, card.coverAssetKey) }
            .padding(
                vertical = MaterialTheme.hikariSpacing.space12,
                horizontal = MaterialTheme.hikariSpacing.space16,
            ),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(MaterialTheme.hikariSpacing.space12),
    ) {
        Text(
            text = (index + 1).toString().padStart(2, '0'),
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Black,
            color = MaterialTheme.colorScheme.tertiary,
        )
        CoverArtwork(
            title = card.title,
            locator = card.coverLocator,
            assetKey = card.coverAssetKey,
            modifier = Modifier.size(width = 48.dp, height = 64.dp),
        )
        Column(modifier = Modifier.weight(1f)) {
            Text(card.title, style = MaterialTheme.typography.titleMedium)
            card.ratingLabel?.let { rating ->
                Text(
                    text = rating,
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

internal fun androidx.compose.foundation.lazy.LazyListScope.loadingSections() {
    item(key = "popular-skeleton") {
        SkeletonSection("Popular", DiscoverTestTags.POPULAR_SKELETON, 248.dp, 148.dp)
    }
    item(key = "latest-skeleton") {
        SkeletonSection("Latest Updates", DiscoverTestTags.LATEST_SKELETON, 156.dp, 92.dp)
    }
    item(key = "top-rated-skeleton") {
        SkeletonSection("Top Rated", DiscoverTestTags.TOP_RATED_SKELETON, 248.dp, 64.dp)
    }
}

@Composable
private fun SkeletonSection(
    title: String,
    tag: String,
    width: androidx.compose.ui.unit.Dp,
    height: androidx.compose.ui.unit.Dp,
) {
    Column(
        modifier = Modifier.padding(horizontal = 20.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        HikariSectionHeader(title)
        HikariSkeleton(
            modifier = Modifier
                .width(width)
                .height(height)
                .testTag(tag),
            shape = MaterialTheme.shapes.large,
        )
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

private const val POPULAR_COVER_ASPECT_RATIO = 1.65f
