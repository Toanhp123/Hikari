package app.openstory.catalog.ui.dashboard

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Card
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.traversalIndex
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.openstory.catalog.ui.components.ReaderTarget
import app.openstory.designsystem.artwork.HikariArtwork
import app.openstory.designsystem.artwork.HikariArtworkModel
import app.openstory.designsystem.artwork.rememberHikariArtwork

@Composable
fun ContinueReadingCard(
    item: HomeDashboardItem,
    onResume: (ReaderTarget) -> Unit,
    modifier: Modifier = Modifier,
    focusRequester: FocusRequester? = null,
    downFocusRequester: FocusRequester? = null,
) {
    val target = requireNotNull(item.readerTarget)
    Card(
        onClick = { onResume(target) },
        modifier = modifier.width(204.dp)
            .then(focusRequester?.let { Modifier.focusRequester(it) } ?: Modifier)
            .then(
                downFocusRequester?.let { target ->
                    Modifier.focusProperties { down = target }
                } ?: Modifier,
            )
            .semantics(mergeDescendants = true) {
            val chapter = item.chapterLabel ?: "saved chapter"
            val percent = ((item.progressFraction ?: 0f) * 100).toInt()
            contentDescription = "Resume ${item.title}, $chapter, $percent percent read"
            traversalIndex = 1f
        },
    ) {
        Column(
            modifier = Modifier.padding(10.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            val artwork = rememberHikariArtwork(HikariArtworkModel(item.coverUrl, item.storyId.value, item.title))
            HikariArtwork(artwork, "${item.title} cover", Modifier.fillMaxWidth().height(224.dp))
            Text(item.title, style = MaterialTheme.typography.titleMedium, maxLines = 2, overflow = TextOverflow.Ellipsis)
            Text(item.chapterLabel ?: "Continue reading", style = MaterialTheme.typography.bodySmall)
            LinearProgressIndicator(
                progress = { item.progressFraction ?: 0f },
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}
