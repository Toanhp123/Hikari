package app.openstory.catalog.ui.discover

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.text.style.TextOverflow
import app.openstory.common.id.StoryId
import app.openstory.designsystem.artwork.HikariArtwork
import app.openstory.designsystem.artwork.HikariArtworkModel
import app.openstory.designsystem.artwork.rememberHikariArtwork
import app.openstory.designsystem.theme.hikariDimensions
import app.openstory.designsystem.theme.hikariLayoutRatios
import app.openstory.designsystem.theme.hikariShapes
import app.openstory.designsystem.theme.hikariSpacing

@Composable
fun DiscoverLatestCard(
    item: DiscoverStoryItem,
    onSelected: (StoryId) -> Unit,
    modifier: Modifier = Modifier,
) {
    val updateLabel = item.latestUpdate?.releaseLabel
    Column(
        modifier = modifier
            .heightIn(min = MaterialTheme.hikariDimensions.minimumTouchTarget)
            .clickable(role = Role.Button) { onSelected(item.storyId) }
            .semantics(mergeDescendants = true) {
                contentDescription = buildString {
                    append(item.title)
                    updateLabel?.let { append(", "); append(it) }
                }
            }
            .testTag("discover-latest-item-${item.storyId.value}"),
        verticalArrangement = Arrangement.spacedBy(MaterialTheme.hikariSpacing.space4),
    ) {
        HikariArtwork(
            state = rememberHikariArtwork(
                HikariArtworkModel(item.coverUrl, item.storyId.value, item.title),
            ),
            contentDescription = null,
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(MaterialTheme.hikariLayoutRatios.posterCardAspectRatio)
                .clip(MaterialTheme.hikariShapes.contentCard),
        )
        Text(
            text = item.title,
            style = MaterialTheme.typography.titleSmall,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
        updateLabel?.let { label ->
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
