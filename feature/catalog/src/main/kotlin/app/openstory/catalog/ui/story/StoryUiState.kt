package app.openstory.catalog.ui.story

import app.openstory.catalog.model.CatalogEntry
import app.openstory.catalog.model.ContentType
import app.openstory.common.id.PluginId
import app.openstory.common.id.StoryId
import app.openstory.catalog.model.Score
import app.openstory.catalog.ui.components.ReaderTarget
import app.openstory.library.LibraryStatus

data class StoryUiState(
    val storyId: StoryId,
    val story: StoryUiModel? = null,
    val selectedSource: StorySourceIdentity? = null,
    val refreshing: Boolean = false,
    val failure: StoryRefreshFailure? = null,
    val libraryStatus: LibraryStatus? = null,
    val resumeTarget: ReaderTarget? = null,
    val selectedSection: StorySection = StorySection.OVERVIEW,
)

data class StoryUiModel(
    val storyId: StoryId,
    val preferredTitle: String,
    val contentType: ContentType,
    val aliases: Set<String>,
    val description: String? = null,
    val coverUrl: String? = null,
    val score: Score? = null,
    val authors: Set<String> = emptySet(),
    val genres: Set<String> = emptySet(),
    val languageTags: Set<String> = emptySet(),
    val sources: List<CatalogEntry>,
)

enum class StorySection { OVERVIEW, CHAPTERS, SOURCES }

data class StorySourceIdentity(
    val pluginId: PluginId,
    val sourceId: String,
)

data class StoryRefreshFailure(
    val code: String,
    val retryable: Boolean,
)
