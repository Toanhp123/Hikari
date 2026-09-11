package app.openstory.catalog.feature.story

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
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
    identityGap: Dp,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(identityGap),
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(StoryVisualMetrics.HeroBannerHeight)
                .clip(MaterialTheme.shapes.large),
        ) {
            CoverArtwork(
                title = state.summary?.title.orEmpty(),
                locator = state.coverLocator,
                assetKey = state.coverAssetKey,
                modifier = Modifier
                    .fillMaxSize()
                    .testTag("story-hero-cover"),
            )
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        Brush.verticalGradient(
                            colors = listOf(
                                Color.Transparent,
                                Color.Transparent,
                                MaterialTheme.colorScheme.surface.copy(alpha = 0.70f),
                                MaterialTheme.colorScheme.surface,
                            ),
                        ),
                    ),
            )
        }
        if (state.summary == null) {
            StoryIdentitySkeleton(Modifier.fillMaxWidth())
        } else {
            StoryIdentity(summary = state.summary, modifier = Modifier.fillMaxWidth())
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
            text = summary.title,
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold,
            maxLines = 3,
            overflow = TextOverflow.Ellipsis,
        )
        StoryIdentityTags(summary)
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
private fun StoryIdentityTags(summary: StorySummaryUi) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(MaterialTheme.hikariSpacing.space12),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        summary.ratingLabel?.let { rating ->
            Text(
                text = "★ $rating",
                style = MaterialTheme.typography.titleMedium,
                color = RATING_STAR_COLOR,
                fontWeight = FontWeight.Bold,
            )
        }
        Surface(
            shape = CircleShape,
            color = MaterialTheme.colorScheme.primaryContainer,
            contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
        ) {
            Text(
                text = summary.contentType.productEyebrowLabel,
                modifier = Modifier.padding(
                    horizontal = MaterialTheme.hikariSpacing.space12,
                    vertical = MaterialTheme.hikariSpacing.space4,
                ),
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.Bold,
            )
        }
        summary.publicationStatus?.let { status ->
            Surface(
                shape = CircleShape,
                color = MaterialTheme.colorScheme.secondaryContainer,
                contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
            ) {
                Text(
                    text = status,
                    modifier = Modifier.padding(
                        horizontal = MaterialTheme.hikariSpacing.space12,
                        vertical = MaterialTheme.hikariSpacing.space4,
                    ),
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Bold,
                )
            }
        }
    }
}

private val RATING_STAR_COLOR = Color(0xFFF5A623)

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
            modifier = Modifier.width(220.dp).height(48.dp),
            shape = MaterialTheme.shapes.small,
        )
        HikariSkeleton(
            modifier = Modifier.width(120.dp).height(20.dp),
            shape = MaterialTheme.shapes.small,
        )
    }
}
