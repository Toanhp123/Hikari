package app.openstory.catalog.feature.discover

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
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
import androidx.compose.ui.text.style.TextOverflow
import app.openstory.catalog.feature.assets.CoverArtwork
import app.openstory.designsystem.theme.hikariSpacing

@Composable
internal fun DiscoverFeaturedStory(
    card: DiscoverCardUi,
    onSelected: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier
            .width(DiscoverVisualMetrics.PopularCardWidth)
            .height(DiscoverVisualMetrics.PopularCardHeight)
            .testTag(DiscoverTestTags.card(app.openstory.catalog.domain.model.CatalogSectionKind.POPULAR, card.ref))
            .semantics { contentDescription = card.title }
            .clickable(onClick = onSelected),
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.surfaceContainer,
    ) {
        Row(
            modifier = Modifier.padding(horizontal = MaterialTheme.hikariSpacing.space16),
            horizontalArrangement = Arrangement.spacedBy(MaterialTheme.hikariSpacing.space16),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            FeaturedCover(card)
            FeaturedCopy(card, Modifier.weight(1f))
        }
    }
}

@Composable
private fun FeaturedCover(card: DiscoverCardUi) {
    CoverArtwork(
        title = card.title,
        locator = card.coverLocator,
        assetKey = card.coverAssetKey,
        modifier = Modifier
            .width(DiscoverVisualMetrics.PopularCoverWidth)
            .height(DiscoverVisualMetrics.PopularCoverHeight)
            .clip(MaterialTheme.shapes.small),
    )
}

@Composable
private fun FeaturedCopy(card: DiscoverCardUi, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(MaterialTheme.hikariSpacing.space8),
    ) {
        Text(
            text = card.title,
            style = MaterialTheme.typography.headlineSmall,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
        card.ratingLabel?.let { rating ->
            Text(rating, style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary)
        }
        card.supportingLabel?.let { label ->
            Text(
                text = label,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}
