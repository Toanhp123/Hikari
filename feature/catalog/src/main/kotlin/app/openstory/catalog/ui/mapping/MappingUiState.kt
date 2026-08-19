package app.openstory.catalog.ui.mapping

import app.openstory.common.id.PluginId
import app.openstory.library.mapping.ContentMappingOrigin
import app.openstory.library.matching.ContentMatchDecision

data class MappingUiState(
    val loading: Boolean = true,
    val mappings: List<MappingItemUiModel> = emptyList(),
    val candidates: List<MappingCandidateUiModel> = emptyList(),
    val urlInput: String = "",
    val busy: Boolean = false,
    val failures: List<String> = emptyList(),
)

data class MappingItemUiModel(
    val pluginId: PluginId,
    val sourceStoryId: String,
    val origin: ContentMappingOrigin,
)

data class MappingCandidateUiModel(
    val pluginId: PluginId,
    val sourceStoryId: String,
    val title: String,
    val sourceUrl: String?,
    val decision: ContentMatchDecision,
    val score: Double,
    val evidenceLabels: List<String>,
    val fromUrl: Boolean,
    val replacesSourceStoryId: String? = null,
)

data class MappingActions(
    val onSearch: () -> Unit = {},
    val onUrlChange: (String) -> Unit = {},
    val onResolveUrl: () -> Unit = {},
    val onApprove: (PluginId, String) -> Unit = { _, _ -> },
    val onReject: (PluginId, String) -> Unit = { _, _ -> },
)
