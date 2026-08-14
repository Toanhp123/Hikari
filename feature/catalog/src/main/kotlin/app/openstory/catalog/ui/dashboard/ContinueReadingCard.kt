package app.openstory.catalog.ui.dashboard

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.width
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.unit.dp
import app.openstory.catalog.ui.components.ReaderTarget
import app.openstory.catalog.ui.components.StoryPosterCard

@Composable
fun ContinueReadingCard(
    item: HomeDashboardItem,
    onResume: (ReaderTarget) -> Unit,
    modifier: Modifier = Modifier,
    focusRequester: FocusRequester? = null,
    downFocusRequester: FocusRequester? = null,
) {
    val target = requireNotNull(item.readerTarget)
    val chapter = item.chapterLabel ?: "saved chapter"
    val percent = ((item.progressFraction ?: 0f) * PERCENT_MULTIPLIER).toInt()
    StoryPosterCard(
        storyId = item.storyId,
        title = item.title,
        coverUrl = item.coverUrl,
        contentDescription = "Resume ${item.title}, $chapter, $percent percent read",
        onSelected = { onResume(target) },
        traversalIndex = 1f,
        modifier = modifier.width(104.dp)
            .then(focusRequester?.let { Modifier.focusRequester(it) } ?: Modifier)
            .then(
                downFocusRequester?.let { target ->
                    Modifier.focusProperties { down = target }
                } ?: Modifier,
            ),
    ) {
        Text(item.chapterLabel ?: "Continue reading", style = MaterialTheme.typography.labelSmall)
        LinearProgressIndicator(
            progress = { item.progressFraction ?: 0f },
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

private const val PERCENT_MULTIPLIER = 100
