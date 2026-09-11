package app.openstory.catalog.feature.discover

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
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
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import app.openstory.catalog.domain.model.CatalogMediaType
import app.openstory.catalog.feature.presentation.SearchIcon
import app.openstory.catalog.feature.presentation.productLabel
import app.openstory.designsystem.theme.hikariSpacing

@Composable
internal fun DiscoverHeader(
    selectedMediaType: CatalogMediaType,
    horizontalInset: Dp,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(
                start = horizontalInset,
                top = MaterialTheme.hikariSpacing.space16,
                end = horizontalInset,
            ),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        DiscoverIdentity(selectedMediaType)
        DiscoverSearchPreview()
    }
}

@Composable
private fun DiscoverIdentity(selectedMediaType: CatalogMediaType) {
    Column(verticalArrangement = Arrangement.spacedBy(MaterialTheme.hikariSpacing.space4)) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(MaterialTheme.hikariSpacing.space8),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = "Discover",
                style = MaterialTheme.typography.headlineLarge,
                modifier = Modifier
                    .testTag(DiscoverTestTags.PAGE_IDENTITY)
                    .semantics { heading() },
            )
            Text(
                text = "\u2022 ${selectedMediaType.productLabel}",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.primary,
            )
        }
        Text(
            text = "Amazing stories await you.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun DiscoverSearchPreview() {
    Surface(
        shape = CircleShape,
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        modifier = Modifier
            .size(SEARCH_CONTAINER_SIZE)
            .semantics { contentDescription = "Search" },
    ) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            SearchIcon(
                size = SEARCH_ICON_SIZE,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
internal fun EditorialHeroBanner(modifier: Modifier = Modifier) {
    val pagerState = rememberPagerState { EDITORIAL_SLIDES.size }
    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(MaterialTheme.hikariSpacing.space12),
    ) {
        HorizontalPager(
            state = pagerState,
            pageSpacing = BANNER_PAGE_SPACING,
            modifier = Modifier.fillMaxWidth(),
        ) { page ->
            EditorialBannerCard(EDITORIAL_SLIDES[page])
        }
        BannerPaginationDots(
            pageCount = EDITORIAL_SLIDES.size,
            currentPage = pagerState.currentPage,
        )
    }
}

@Composable
private fun EditorialBannerCard(slide: EditorialSlide) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .height(DiscoverVisualMetrics.HeroBannerHeight),
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.primaryContainer,
        tonalElevation = BANNER_CARD_ELEVATION,
        shadowElevation = BANNER_CARD_ELEVATION,
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(Brush.verticalGradient(slide.gradientColors))
                .padding(MaterialTheme.hikariSpacing.space20),
            verticalArrangement = Arrangement.Bottom,
        ) {
            Surface(
                shape = CircleShape,
                color = Color.Black.copy(alpha = BANNER_TAG_ALPHA),
            ) {
                Text(
                    text = slide.tag,
                    modifier = Modifier.padding(
                        horizontal = BANNER_TAG_PADDING_HORIZONTAL,
                        vertical = BANNER_TAG_PADDING_VERTICAL,
                    ),
                    style = MaterialTheme.typography.labelSmall,
                    color = Color.White,
                    fontWeight = FontWeight.Bold,
                )
            }
            Spacer(Modifier.height(MaterialTheme.hikariSpacing.space8))
            Text(
                text = slide.title,
                style = MaterialTheme.typography.headlineSmall,
                color = Color.White,
                fontWeight = FontWeight.Bold,
            )
            Text(
                text = slide.subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = Color.White.copy(alpha = BANNER_SUBTITLE_ALPHA),
            )
        }
    }
}

@Composable
private fun BannerPaginationDots(pageCount: Int, currentPage: Int) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        repeat(pageCount) { index ->
            val selected = index == currentPage
            Box(
                modifier = Modifier
                    .width(if (selected) BANNER_ACTIVE_DOT_WIDTH else BANNER_INACTIVE_DOT_WIDTH)
                    .height(BANNER_DOT_HEIGHT)
                    .clip(CircleShape)
                    .background(
                        if (selected) {
                            MaterialTheme.colorScheme.primary
                        } else {
                            MaterialTheme.colorScheme.outlineVariant
                        },
                    ),
            )
            if (index < pageCount - 1) Spacer(Modifier.width(BANNER_DOT_SPACING))
        }
    }
}

private data class EditorialSlide(
    val title: String,
    val subtitle: String,
    val tag: String,
    val gradientColors: List<Color>,
)

private val EDITORIAL_SLIDES = listOf(
    EditorialSlide(
        title = "Stories for a Brighter You",
        subtitle = "Discover new worlds, new feelings, new perspectives.",
        tag = "FEATURED",
        gradientColors = listOf(Color(0xFFE55D68), Color(0xFFC74351)),
    ),
    EditorialSlide(
        title = "Beyond the Horizon",
        subtitle = "Epic journeys, fantasy realms, and grand adventures.",
        tag = "TRENDING",
        gradientColors = listOf(Color(0xFF536DFE), Color(0xFF3949AB)),
    ),
    EditorialSlide(
        title = "Whispers in the Rain",
        subtitle = "Heartfelt drama and slice-of-life tales to inspire you.",
        tag = "CURATED",
        gradientColors = listOf(Color(0xFF00897B), Color(0xFF00695C)),
    ),
)

private val SEARCH_CONTAINER_SIZE = 40.dp
private val SEARCH_ICON_SIZE = 18.dp
private val BANNER_ACTIVE_DOT_WIDTH = 20.dp
private val BANNER_INACTIVE_DOT_WIDTH = 6.dp
private val BANNER_DOT_HEIGHT = 6.dp
private val BANNER_DOT_SPACING = 6.dp
private val BANNER_PAGE_SPACING = 12.dp
private val BANNER_TAG_PADDING_HORIZONTAL = 8.dp
private val BANNER_TAG_PADDING_VERTICAL = 4.dp
private val BANNER_CARD_ELEVATION = 2.dp
private const val BANNER_TAG_ALPHA = 0.25f
private const val BANNER_SUBTITLE_ALPHA = 0.9f
