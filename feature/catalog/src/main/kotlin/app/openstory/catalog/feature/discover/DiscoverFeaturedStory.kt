package app.openstory.catalog.feature.discover

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import app.openstory.catalog.domain.model.CatalogSectionKind
import app.openstory.catalog.feature.assets.CoverArtwork
import app.openstory.designsystem.theme.hikariSpacing

@Composable
internal fun DiscoverFeaturedStory(
    card: DiscoverCardUi,
    onSelected: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .width(DiscoverVisualMetrics.TrendingCoverWidth)
            .testTag(DiscoverTestTags.card(CatalogSectionKind.POPULAR, card.ref))
            .semantics { contentDescription = card.title }
            .clickable(onClick = onSelected),
        verticalArrangement = Arrangement.spacedBy(MaterialTheme.hikariSpacing.space8),
    ) {
        CoverArtwork(
            title = card.title,
            locator = card.coverLocator,
            assetKey = card.coverAssetKey,
            modifier = Modifier
                .width(DiscoverVisualMetrics.TrendingCoverWidth)
                .height(DiscoverVisualMetrics.TrendingCoverHeight)
                .clip(MaterialTheme.shapes.medium),
        )
        Text(
            text = card.title,
            style = MaterialTheme.typography.titleSmall,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
        (card.supportingLabel ?: card.ratingLabel)?.let { label ->
            Text(
                text = label,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}
