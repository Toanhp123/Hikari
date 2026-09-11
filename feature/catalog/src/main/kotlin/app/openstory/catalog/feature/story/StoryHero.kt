package app.openstory.catalog.feature.story

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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import app.openstory.catalog.feature.assets.CoverArtwork
import app.openstory.catalog.feature.presentation.productEyebrowLabel
import app.openstory.designsystem.state.HikariSkeleton
import app.openstory.designsystem.theme.hikariSpacing

@Composable
internal fun StoryHero(
    state: StoryDetailUiState,
    coverWidth: Dp,
    coverHeight: Dp,
    identityGap: Dp,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(identityGap),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        CoverArtwork(
            title = state.summary?.title.orEmpty(),
            locator = state.coverLocator,
            assetKey = state.coverAssetKey,
            modifier = Modifier
                .width(coverWidth)
                .height(coverHeight)
                .clip(MaterialTheme.shapes.small)
                .testTag("story-hero-cover"),
        )
        if (state.summary == null) {
            StoryIdentitySkeleton(Modifier.weight(1f))
        } else {
            StoryIdentity(summary = state.summary, modifier = Modifier.weight(1f))
        }
    }
}

@Composable
private fun StoryIdentity(summary: StorySummaryUi, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(MaterialTheme.hikariSpacing.space8),
    ) {
        Text(
            text = summary.contentType.productEyebrowLabel,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.primary,
        )
        Text(
            text = summary.title,
            style = MaterialTheme.typography.headlineMedium,
            maxLines = 3,
            overflow = TextOverflow.Ellipsis,
        )
        summary.ratingLabel?.let { rating ->
            Text(text = rating, style = MaterialTheme.typography.titleSmall)
        }
        summary.publicationStatus?.let { status ->
            Surface(
                shape = MaterialTheme.shapes.small,
                color = MaterialTheme.colorScheme.secondaryContainer,
                contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
            ) {
                Text(
                    text = status,
                    modifier = Modifier.padding(
                        horizontal = MaterialTheme.hikariSpacing.space12,
                        vertical = MaterialTheme.hikariSpacing.space4,
                    ),
                    style = MaterialTheme.typography.labelMedium,
                )
            }
        }
        summary.latestUpdateLabel?.let { update ->
            Text(
                text = update,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun StoryIdentitySkeleton(modifier: Modifier = Modifier) {
    Column(
        modifier = modifier.testTag("story-hero-identity-skeleton"),
        verticalArrangement = Arrangement.spacedBy(MaterialTheme.hikariSpacing.space12),
    ) {
        HikariSkeleton(
            modifier = Modifier.width(72.dp).height(14.dp),
            shape = MaterialTheme.shapes.small,
        )
        HikariSkeleton(
            modifier = Modifier.width(156.dp).height(72.dp),
            shape = MaterialTheme.shapes.small,
        )
        HikariSkeleton(
            modifier = Modifier.width(96.dp).height(18.dp),
            shape = MaterialTheme.shapes.small,
        )
    }
}
