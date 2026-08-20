package app.openstory.catalog.ui.story

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import app.openstory.designsystem.content.HikariMetadataGroup
import app.openstory.designsystem.content.HikariSectionHeader
import app.openstory.designsystem.content.HikariSectionLead
import app.openstory.designsystem.refresh.HikariPullToRefresh
import app.openstory.designsystem.theme.hikariSpacing

@Composable
internal fun StoryOverview(
    story: StoryUiModel,
    compact: Boolean = false,
    modifier: Modifier = Modifier,
    refreshing: Boolean = false,
    onRefresh: (() -> Unit)? = null,
) {
    if (onRefresh == null) {
        StoryOverviewList(story, compact, modifier.fillMaxWidth())
        return
    }
    HikariPullToRefresh(
        refreshing = refreshing,
        onRefresh = onRefresh,
        modifier = modifier.fillMaxWidth().testTag("story-overview-pull-refresh"),
    ) {
        StoryOverviewList(story, compact, Modifier.fillMaxSize())
    }
}

@Composable
private fun StoryOverviewList(
    story: StoryUiModel,
    compact: Boolean,
    modifier: Modifier,
) {
    val description = story.description?.takeIf(String::isNotBlank)
    val metadataGroups = listOf(
        "Authors" to story.authors,
        "Genres" to story.genres,
        "Languages" to story.languageTags,
        "Also known as" to story.aliases,
    ).filter { (_, values) -> values.isNotEmpty() }

    LazyColumn(
        modifier = modifier,
        contentPadding = storySectionContentPadding(),
        verticalArrangement = Arrangement.spacedBy(MaterialTheme.hikariSpacing.itemGap),
    ) {
        when {
            description != null -> {
                item(key = "story-overview-header") {
                    HikariSectionLead(
                        header = { OverviewHeader() },
                        firstContent = {
                            Text(
                                description,
                                style = MaterialTheme.typography.bodyLarge,
                                maxLines = if (compact) COMPACT_DESCRIPTION_LINES else Int.MAX_VALUE,
                            )
                        },
                    )
                }
                metadataGroups.forEach { (title, values) ->
                    item(key = "story-overview-metadata-$title") { HikariMetadataGroup(title, values) }
                }
            }
            metadataGroups.isNotEmpty() -> {
                val (firstTitle, firstValues) = metadataGroups.first()
                item(key = "story-overview-header") {
                    HikariSectionLead(
                        header = { OverviewHeader() },
                        firstContent = { HikariMetadataGroup(firstTitle, firstValues) },
                    )
                }
                metadataGroups.drop(1).forEach { (title, values) ->
                    item(key = "story-overview-metadata-$title") { HikariMetadataGroup(title, values) }
                }
            }
            else -> item(key = "story-overview-header") { OverviewHeader() }
        }
    }
}

@Composable
private fun OverviewHeader() {
    HikariSectionHeader(
        title = "Details",
        subtitle = "Story information from the selected catalog source.",
    )
}

private const val COMPACT_DESCRIPTION_LINES = 7
