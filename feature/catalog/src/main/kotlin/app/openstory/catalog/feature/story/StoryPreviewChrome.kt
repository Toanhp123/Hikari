package app.openstory.catalog.feature.story

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import app.openstory.catalog.feature.presentation.BookmarkIcon
import app.openstory.catalog.feature.presentation.HeartIcon
import app.openstory.catalog.feature.presentation.MoreIcon
import app.openstory.catalog.feature.presentation.ShareIcon
import app.openstory.designsystem.content.HikariSectionHeader
import app.openstory.designsystem.theme.hikariSpacing

// These controls intentionally preview later product affordances without owning any action or state.
@Composable
internal fun StoryPreviewActions() {
    val iconTint = MaterialTheme.colorScheme.onSurfaceVariant
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(MaterialTheme.hikariSpacing.space8),
    ) {
        Button(
            onClick = {},
            enabled = false,
            modifier = Modifier
                .fillMaxWidth()
                .height(PRIMARY_ACTION_HEIGHT),
            shape = CircleShape,
            colors = ButtonDefaults.buttonColors(
                disabledContainerColor = MaterialTheme.colorScheme.primary,
                disabledContentColor = Color.White,
            ),
        ) {
            Text(
                text = "Read from Chapter 1",
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.Bold,
            )
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(MaterialTheme.hikariSpacing.space8),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            OutlinedButton(
                onClick = {},
                enabled = false,
                modifier = Modifier
                    .weight(1f)
                    .height(SECONDARY_ACTION_HEIGHT),
                shape = CircleShape,
            ) {
                BookmarkIcon(
                    size = BOOKMARK_ICON_SIZE,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.width(MaterialTheme.hikariSpacing.space8))
                Text("Add to Library", style = MaterialTheme.typography.labelMedium)
            }
            PreviewIcon { HeartIcon(size = ACTION_ICON_SIZE, tint = iconTint) }
            PreviewIcon { ShareIcon(size = ACTION_ICON_SIZE, tint = iconTint) }
            PreviewIcon { MoreIcon(size = ACTION_ICON_SIZE, tint = iconTint) }
        }
    }
}

@Composable
internal fun StoryPreviewTabs() {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = MaterialTheme.hikariSpacing.space8),
        horizontalArrangement = Arrangement.spacedBy(MaterialTheme.hikariSpacing.space24),
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = "Synopsis",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary,
            )
            Spacer(Modifier.height(MaterialTheme.hikariSpacing.space4))
            Box(
                modifier = Modifier
                    .width(ACTIVE_TAB_INDICATOR_WIDTH)
                    .height(ACTIVE_TAB_INDICATOR_HEIGHT)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.primary),
            )
        }
        PreviewTabLabel("Chapters")
        PreviewTabLabel("Similar")
    }
}

@Composable
internal fun StoryRecommendationPreview() {
    Column(verticalArrangement = Arrangement.spacedBy(MaterialTheme.hikariSpacing.space8)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            HikariSectionHeader("You May Also Like")
            Text(
                text = "See All",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = PREVIEW_LABEL_ALPHA),
            )
        }
        Text(
            text = "Stories stay longer when details feel right.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            fontStyle = FontStyle.Italic,
        )
    }
}

@Composable
private fun PreviewIcon(content: @Composable () -> Unit) {
    Surface(
        modifier = Modifier.size(ACTION_BUTTON_SIZE),
        shape = CircleShape,
        color = MaterialTheme.colorScheme.surfaceVariant,
    ) {
        Box(contentAlignment = Alignment.Center) { content() }
    }
}

@Composable
private fun PreviewTabLabel(label: String) {
    Text(
        text = label,
        style = MaterialTheme.typography.titleMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = PREVIEW_TAB_ALPHA),
    )
}

private val PRIMARY_ACTION_HEIGHT = 48.dp
private val SECONDARY_ACTION_HEIGHT = 40.dp
private val ACTION_BUTTON_SIZE = 40.dp
private val BOOKMARK_ICON_SIZE = 16.dp
private val ACTION_ICON_SIZE = 18.dp
private val ACTIVE_TAB_INDICATOR_WIDTH = 28.dp
private val ACTIVE_TAB_INDICATOR_HEIGHT = 3.dp
private const val PREVIEW_LABEL_ALPHA = 0.6f
private const val PREVIEW_TAB_ALPHA = 0.5f
