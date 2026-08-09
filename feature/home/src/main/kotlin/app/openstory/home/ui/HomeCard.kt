package app.openstory.home.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.openstory.model.ContentType
import app.openstory.model.PluginId
import app.openstory.model.StoryId

interface HomeCoverRenderer {
    @Composable
    fun render(
        reference: String?,
        title: String,
        modifier: Modifier,
    )
}

object PlaceholderHomeCoverRenderer : HomeCoverRenderer {
    @Composable
    override fun render(
        reference: String?,
        title: String,
        modifier: Modifier,
    ) {
        Box(
            modifier = modifier.background(MaterialTheme.colorScheme.surfaceVariant),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = title.trim().take(1).ifEmpty { "?" },
                style = MaterialTheme.typography.headlineMedium,
            )
        }
    }
}

data class HomeCardPresentation(
    val storyId: StoryId,
    val sourceId: String,
    val title: String,
    val contentType: ContentType,
    val authors: Set<String>,
    val coverReference: String?,
    val score: Double?,
    val scoreScale: Double?,
    val scoreSource: PluginId,
    val sectionTitle: String,
)

@Composable
fun HomeCard(
    card: HomeCardPresentation,
    onClick: (HomeStorySelection) -> Unit,
    modifier: Modifier = Modifier,
    coverRenderer: HomeCoverRenderer = PlaceholderHomeCoverRenderer,
) {
    Card(
        modifier = modifier
            .width(CARD_WIDTH)
            .semantics(mergeDescendants = true) {
                contentDescription = card.accessibilityDescription()
            },
        onClick = {
            onClick(
                HomeStorySelection(
                    storyId = card.storyId,
                    pluginId = card.scoreSource,
                    sourceId = card.sourceId,
                ),
            )
        },
    ) {
        Column(
            modifier = Modifier.padding(CARD_PADDING),
            verticalArrangement = Arrangement.spacedBy(CARD_GAP),
        ) {
            coverRenderer.render(
                reference = card.coverReference,
                title = card.title,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(COVER_HEIGHT),
            )
            Text(
                text = card.title,
                style = MaterialTheme.typography.titleMedium,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = card.contentType.displayName(),
                style = MaterialTheme.typography.labelMedium,
            )
            if (card.authors.isNotEmpty()) {
                Text(
                    text = card.authors.sorted().joinToString(),
                    style = MaterialTheme.typography.bodySmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            card.scoreLabel()?.let { score ->
                Text(
                    text = score,
                    style = MaterialTheme.typography.bodySmall,
                )
            }
            Spacer(modifier = Modifier.height(1.dp))
        }
    }
}

private fun HomeCardPresentation.scoreLabel(): String? {
    val value = score
    val scale = scoreScale
    return if (value != null && scale != null) {
        "${formatScore(value)} / ${formatScore(scale)} · ${scoreSource.value}"
    } else {
        null
    }
}

private fun HomeCardPresentation.accessibilityDescription(): String {
    val scoreText = if (score != null && scoreScale != null) {
        " Score ${formatScore(score)} out of ${formatScore(scoreScale)} from ${scoreSource.value}."
    } else {
        " Score unavailable from ${scoreSource.value}."
    }
    return "$title. ${contentType.displayName()}. Section $sectionTitle.$scoreText"
}

private fun ContentType.displayName(): String = when (this) {
    ContentType.LIGHT_NOVEL -> "Light novel"
    ContentType.WEB_NOVEL -> "Web novel"
    ContentType.MANGA -> "Manga"
    ContentType.ANIME -> "Anime"
}

private fun formatScore(value: Double): String {
    val whole = value.toLong()
    return if (value == whole.toDouble()) whole.toString() else value.toString()
}

private val CARD_WIDTH = 184.dp
private val COVER_HEIGHT = 220.dp
private val CARD_PADDING = 12.dp
private val CARD_GAP = 6.dp
