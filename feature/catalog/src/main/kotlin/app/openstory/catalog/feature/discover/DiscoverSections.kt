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
import androidx.compose.runtime.key
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
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

@Composable
internal fun DiscoverSection(
    section: DiscoverSectionUi,
    onStorySelected: (StorySourceRef, CoverAssetKey?) -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .testTag(DiscoverTestTags.section(section.kind))
            .semantics { traversalIndex = section.kind.traversalIndex },
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(
            text = section.kind.title,
            style = MaterialTheme.typography.headlineSmall,
            modifier = Modifier
                .padding(horizontal = 20.dp)
                .semantics { heading() },
        )
        when (section.kind) {
            CatalogSectionKind.POPULAR -> PopularCards(section.cards, onStorySelected)
            CatalogSectionKind.LATEST_UPDATES -> LatestCards(section.cards, onStorySelected)
            CatalogSectionKind.TOP_RATED -> TopRatedCards(section.cards, onStorySelected)
        }
    }
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
                    CoverPlaceholder(
                        title = card.title,
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
private fun LatestCards(
    cards: List<DiscoverCardUi>,
    onStorySelected: (StorySourceRef, CoverAssetKey?) -> Unit,
) {
    Column(
        modifier = Modifier.padding(horizontal = 20.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        cards.forEach { card ->
            key(DiscoverTestTags.card(CatalogSectionKind.LATEST_UPDATES, card.ref)) {
                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
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
                        CoverPlaceholder(card.title, Modifier.size(width = 64.dp, height = 82.dp))
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
        }
    }
}

@Composable
private fun TopRatedCards(
    cards: List<DiscoverCardUi>,
    onStorySelected: (StorySourceRef, CoverAssetKey?) -> Unit,
) {
    Column(
        modifier = Modifier.padding(horizontal = 20.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        cards.forEachIndexed { index, card ->
            key(DiscoverTestTags.card(CatalogSectionKind.TOP_RATED, card.ref)) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
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
        }
    }
}

@Composable
private fun CoverPlaceholder(title: String, modifier: Modifier) {
    Box(
        modifier = modifier.background(
            Brush.linearGradient(
                colors = listOf(
                    MaterialTheme.colorScheme.tertiaryContainer,
                    MaterialTheme.colorScheme.primaryContainer,
                    MaterialTheme.colorScheme.surfaceVariant,
                ),
            ),
        ),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = title.firstOrNull()?.uppercase() ?: "H",
            style = MaterialTheme.typography.displayMedium,
            fontWeight = FontWeight.Black,
            color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.68f),
        )
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
