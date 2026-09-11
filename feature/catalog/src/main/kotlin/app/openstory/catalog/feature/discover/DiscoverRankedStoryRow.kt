package app.openstory.catalog.feature.discover

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
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
internal fun DiscoverRankedStoryRow(
    index: Int,
    card: DiscoverCardUi,
    isFinal: Boolean,
    onSelected: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
            .defaultMinSize(minHeight = DiscoverVisualMetrics.TopRatedRowMinHeight)
            .then(if (isFinal) Modifier.testTag(DiscoverTestTags.FINAL_TOP_RATED_ROW) else Modifier),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .defaultMinSize(minHeight = DiscoverVisualMetrics.TopRatedRowMinHeight)
                .testTag(DiscoverTestTags.card(CatalogSectionKind.TOP_RATED, card.ref))
                .semantics { contentDescription = card.title }
                .clip(MaterialTheme.shapes.medium)
                .clickable(onClick = onSelected),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(MaterialTheme.hikariSpacing.space12),
        ) {
            RankedNumber(index)
            RankedCover(card)
            RankedCopy(card, Modifier.weight(1f))
        }
    }
}

@Composable
private fun RankedNumber(index: Int) {
    Text(
        text = (index + 1).toString().padStart(2, '0'),
        modifier = Modifier.width(DiscoverVisualMetrics.TopRatedRankWidth),
        style = MaterialTheme.typography.headlineMedium,
        color = MaterialTheme.colorScheme.primary,
    )
}

@Composable
private fun RankedCover(card: DiscoverCardUi) {
    CoverArtwork(
        title = card.title,
        locator = card.coverLocator,
        assetKey = card.coverAssetKey,
        modifier = Modifier
            .width(DiscoverVisualMetrics.TopRatedCoverWidth)
            .height(DiscoverVisualMetrics.TopRatedCoverHeight)
            .clip(MaterialTheme.shapes.small),
    )
}

@Composable
private fun RankedCopy(card: DiscoverCardUi, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(MaterialTheme.hikariSpacing.space4),
    ) {
        Text(
            text = card.title,
            style = MaterialTheme.typography.titleMedium,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
        card.ratingLabel?.let { rating ->
            Text(rating, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
        }
        card.supportingLabel?.let { label ->
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
