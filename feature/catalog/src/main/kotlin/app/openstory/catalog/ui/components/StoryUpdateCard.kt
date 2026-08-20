package app.openstory.catalog.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.traversalIndex
import androidx.compose.ui.text.style.TextOverflow
import app.openstory.common.id.StoryId
import app.openstory.designsystem.artwork.HikariArtwork
import app.openstory.designsystem.artwork.HikariArtworkModel
import app.openstory.designsystem.artwork.HikariListArtworkFrame
import app.openstory.designsystem.artwork.rememberHikariArtwork
import app.openstory.designsystem.control.HikariUtilityAction
import app.openstory.designsystem.surface.HikariContentCard
import app.openstory.designsystem.theme.hikariDimensions
import app.openstory.designsystem.theme.hikariSpacing

data class StoryUpdateCardContent(
    val storyId: StoryId,
    val title: String,
    val coverUrl: String?,
    val chapterLabel: String,
    val contentDescription: String,
    val sourceLabel: String? = null,
    val languageTag: String? = null,
)

data class StoryUpdateCardAction(
    val label: String,
    val onClick: () -> Unit,
)

enum class StoryUpdateCardVariant {
    SHELF,
    ROW,
}

@Composable
fun StoryUpdateCard(
    content: StoryUpdateCardContent,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    variant: StoryUpdateCardVariant = StoryUpdateCardVariant.ROW,
    action: StoryUpdateCardAction? = null,
    traversalIndex: Float? = null,
) {
    val cardOwnsClick = action == null
    HikariContentCard(
        onClick = onClick.takeIf { cardOwnsClick },
        modifier = storyUpdateCardModifier(
            modifier = modifier,
            content = content,
            variant = variant,
            traversalIndex = traversalIndex,
            mergeContent = cardOwnsClick,
        ),
    ) {
        val contentPadding = MaterialTheme.hikariSpacing.space12
        Row(
            modifier = Modifier.padding(contentPadding),
            horizontalArrangement = Arrangement.spacedBy(contentPadding),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (cardOwnsClick) {
                StoryUpdateArtwork(content, variant)
                StoryUpdateText(content, variant)
            } else {
                StoryUpdateClickableContent(
                    content = content,
                    variant = variant,
                    onClick = onClick,
                    modifier = Modifier.weight(1f),
                )
                StoryUpdateAction(action)
            }
        }
    }
}

@Composable
private fun storyUpdateCardModifier(
    modifier: Modifier,
    content: StoryUpdateCardContent,
    variant: StoryUpdateCardVariant,
    traversalIndex: Float?,
    mergeContent: Boolean,
): Modifier {
    val dimensions = MaterialTheme.hikariDimensions
    val sizedModifier = when (variant) {
        StoryUpdateCardVariant.SHELF -> modifier
            .width(dimensions.dashboardCardWidth)
            .heightIn(min = dimensions.summaryCardMinHeight)
        StoryUpdateCardVariant.ROW -> modifier
            .fillMaxWidth()
            .heightIn(min = dimensions.updateRowMinHeight)
    }
    return sizedModifier.semantics(mergeDescendants = mergeContent) {
        if (mergeContent) contentDescription = content.contentDescription
        traversalIndex?.let { value -> this.traversalIndex = value }
    }
}

@Composable
private fun StoryUpdateClickableContent(
    content: StoryUpdateCardContent,
    variant: StoryUpdateCardVariant,
    onClick: () -> Unit,
    modifier: Modifier,
) {
    Row(
        modifier = modifier
            .heightIn(min = MaterialTheme.hikariDimensions.minimumTouchTarget)
            .clickable(role = Role.Button, onClick = onClick)
            .semantics(mergeDescendants = true) { contentDescription = content.contentDescription },
        horizontalArrangement = Arrangement.spacedBy(MaterialTheme.hikariSpacing.space12),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        StoryUpdateArtwork(content, variant)
        StoryUpdateText(content, variant)
    }
}

@Composable
private fun StoryUpdateArtwork(
    content: StoryUpdateCardContent,
    variant: StoryUpdateCardVariant,
) {
    val dimensions = MaterialTheme.hikariDimensions
    val artworkSize = when (variant) {
        StoryUpdateCardVariant.SHELF -> dimensions.posterActivity
        StoryUpdateCardVariant.ROW -> dimensions.posterUpdate
    }
    HikariListArtworkFrame(Modifier.width(artworkSize.width).height(artworkSize.height)) {
        HikariArtwork(
            state = rememberHikariArtwork(
                HikariArtworkModel(content.coverUrl, content.storyId.value, content.title),
            ),
            contentDescription = "${content.title} cover",
            modifier = Modifier.matchParentSize(),
        )
    }
}

@Composable
private fun RowScope.StoryUpdateText(
    content: StoryUpdateCardContent,
    variant: StoryUpdateCardVariant,
) {
    Column(
        modifier = if (variant == StoryUpdateCardVariant.ROW) Modifier.weight(1f) else Modifier,
        verticalArrangement = Arrangement.spacedBy(
            if (variant == StoryUpdateCardVariant.ROW) {
                MaterialTheme.hikariSpacing.space8
            } else {
                MaterialTheme.hikariSpacing.space4
            },
        ),
    ) {
        Text(
            content.title,
            style = if (variant == StoryUpdateCardVariant.ROW) {
                MaterialTheme.typography.titleMedium
            } else {
                MaterialTheme.typography.titleSmall
            },
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
        Text(
            content.chapterLabel,
            style = if (variant == StoryUpdateCardVariant.ROW) {
                MaterialTheme.typography.bodyMedium
            } else {
                MaterialTheme.typography.bodySmall
            },
        )
        if (variant == StoryUpdateCardVariant.ROW && content.sourceLabel != null && content.languageTag != null) {
            Text(
                "${content.sourceLabel}  •  ${content.languageTag}",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
private fun StoryUpdateAction(action: StoryUpdateCardAction) {
    HikariUtilityAction(
        onClick = action.onClick,
        modifier = Modifier.heightIn(min = MaterialTheme.hikariDimensions.minimumTouchTarget),
    ) { Text(action.label) }
}
