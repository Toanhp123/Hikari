package app.openstory.catalog.feature.discover.section

import app.openstory.catalog.feature.discover.DiscoverCardUi
import app.openstory.catalog.feature.discover.DiscoverTestTags
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.Dp
import app.openstory.catalog.domain.model.CatalogSectionKind
import app.openstory.designsystem.content.HikariPosterCard
import app.openstory.designsystem.content.HikariPosterGeometry

@Composable
internal fun DiscoverPosterStory(
    card: DiscoverCardUi,
    sectionKind: CatalogSectionKind,
    width: Dp,
    geometry: HikariPosterGeometry,
    onSelected: () -> Unit,
    onCoverReady: () -> Unit,
    modifier: Modifier = Modifier,
) {
    HikariPosterCard(
        title = card.title,
        supportingText = card.supportingLabel ?: card.ratingLabel,
        onClick = onSelected,
        modifier = modifier
            .width(width)
            .testTag(DiscoverTestTags.card(sectionKind, card.ref)),
        geometry = geometry,
    ) {
        DiscoverCoverImage(
            card = card,
            onCoverReady = onCoverReady,
            modifier = Modifier.matchParentSize(),
        )
    }
}
