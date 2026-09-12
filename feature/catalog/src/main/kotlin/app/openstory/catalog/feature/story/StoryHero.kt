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
import androidx.compose.runtime.SideEffect
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
    hero: StoryHeroUi,
    identityGap: Dp,
    modifier: Modifier = Modifier,
    onMaterialized: () -> Unit = {},
) {
    SideEffect(onMaterialized)
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
                title = hero.title.orEmpty(),
                locator = hero.artwork.locator,
                assetKey = hero.artwork.assetKey,
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
        if (hero.title == null || hero.contentType == null) {
            StoryIdentitySkeleton(Modifier.fillMaxWidth())
        } else {
            StoryIdentity(hero = hero, modifier = Modifier.fillMaxWidth())
        }
    }
}

@Composable
private fun StoryIdentity(hero: StoryHeroUi, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(MaterialTheme.hikariSpacing.space8),
    ) {
        Text(
            text = requireNotNull(hero.title),
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold,
            maxLines = 3,
            overflow = TextOverflow.Ellipsis,
        )
        StoryIdentityTags(hero)
        hero.latestUpdateLabel?.let { update ->
            Text(
                text = update,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun StoryIdentityTags(hero: StoryHeroUi) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(MaterialTheme.hikariSpacing.space12),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        hero.ratingLabel?.let { rating ->
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
                text = requireNotNull(hero.contentType).productEyebrowLabel,
                modifier = Modifier.padding(
                    horizontal = MaterialTheme.hikariSpacing.space12,
                    vertical = MaterialTheme.hikariSpacing.space4,
                ),
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.Bold,
            )
        }
        hero.publicationStatus?.let { status ->
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
