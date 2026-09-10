package app.openstory.catalog.feature.discover

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.traversalIndex
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.openstory.catalog.domain.asset.CoverAssetKey
import app.openstory.catalog.domain.identity.StorySourceRef
import app.openstory.catalog.domain.model.CatalogSectionKind
import app.openstory.catalog.feature.assets.CoverArtwork

internal fun DiscoverSectionUi.viewportRows(): List<DiscoverViewportRow> = buildList {
    add(DiscoverViewportRow.Header(kind))
    if (kind == CatalogSectionKind.POPULAR) {
        add(DiscoverViewportRow.Carousel(kind, cards))
    } else {
        cards.forEachIndexed { index, card ->
            add(DiscoverViewportRow.VerticalCard(kind, index, card))
        }
    }
}

@Composable
internal fun DiscoverViewportRowContent(
    row: DiscoverViewportRow,
    onStorySelected: (StorySourceRef, CoverAssetKey?) -> Unit,
) {
    when (row) {
        is DiscoverViewportRow.Header -> SectionHeader(row.kind)
        is DiscoverViewportRow.Carousel -> PopularCards(row.cards, onStorySelected)
        is DiscoverViewportRow.VerticalCard -> when (row.kind) {
            CatalogSectionKind.LATEST_UPDATES -> LatestCard(row.card, onStorySelected)
            CatalogSectionKind.TOP_RATED -> TopRatedCard(row.index, row.card, onStorySelected)
            CatalogSectionKind.POPULAR -> error("Popular cards belong to the carousel row")
        }
    }
}

@Composable
private fun SectionHeader(kind: CatalogSectionKind) {
    Text(
        text = kind.title,
        style = MaterialTheme.typography.headlineSmall,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp)
            .testTag(DiscoverTestTags.section(kind))
            .semantics {
                heading()
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
        contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 20.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        items(cards, key = { card -> card.ref.storyId.value }) { card ->
            Surface(
                modifier = Modifier
                    .width(248.dp)
                    .testTag(DiscoverTestTags.card(CatalogSectionKind.POPULAR, card.ref))
                    .semantics { contentDescription = card.title }
                    .clickable { onStorySelected(card.ref, card.coverAssetKey) },
                shape = RoundedCornerShape(28.dp),
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
                        modifier = Modifier.padding(16.dp),
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
            .padding(horizontal = 20.dp)
            .testTag(DiscoverTestTags.card(CatalogSectionKind.LATEST_UPDATES, card.ref))
            .semantics { contentDescription = card.title }
            .clickable { onStorySelected(card.ref, card.coverAssetKey) },
        shape = RoundedCornerShape(18.dp),
        tonalElevation = 1.dp,
    ) {
        Row(
            modifier = Modifier.padding(10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp),
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
            .padding(horizontal = 20.dp)
            .testTag(DiscoverTestTags.card(CatalogSectionKind.TOP_RATED, card.ref))
            .semantics { contentDescription = card.title }
            .clip(RoundedCornerShape(18.dp))
            .clickable { onStorySelected(card.ref, card.coverAssetKey) }
            .padding(vertical = 12.dp, horizontal = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp),
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
        Text(title, style = MaterialTheme.typography.headlineSmall)
        Box(
            modifier = Modifier
                .width(width)
                .height(height)
                .clip(RoundedCornerShape(24.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant)
                .testTag(tag),
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
