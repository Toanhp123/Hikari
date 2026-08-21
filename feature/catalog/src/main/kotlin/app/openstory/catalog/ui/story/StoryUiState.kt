package app.openstory.catalog.ui.story

import app.openstory.catalog.canonical.CanonicalSourcePreferenceMode
import app.openstory.catalog.identity.SourceKey
import app.openstory.catalog.model.CatalogEntry
import app.openstory.catalog.model.ContentType
import app.openstory.catalog.model.PublicationStatus
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
    val reconciliationPrompt: StoryReconciliationPromptUiModel? = null,
    val reconciliationResolving: Boolean = false,
    val reconciliationFailureMessage: String? = null,
)

data class StoryReconciliationPromptUiModel(
    val caseId: String,
    val caseRevision: Long,
    val otherStoryId: StoryId,
    val otherStoryTitle: String,
    val confidence: Double,
    val mergeAllowed: Boolean,
    val reasonLabels: List<String>,
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
    val effectivePrimary: SourceKey? = null,
    val preferenceMode: CanonicalSourcePreferenceMode = CanonicalSourcePreferenceMode.AUTO,
    val pinnedSource: SourceKey? = null,
    val publicationStatus: PublicationStatus? = null,
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
