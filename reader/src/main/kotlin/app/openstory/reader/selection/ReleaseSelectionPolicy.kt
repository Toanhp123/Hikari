package app.openstory.reader.selection

import app.openstory.common.id.ChapterReleaseId
import app.openstory.common.id.PluginId

data class ReleaseSelectionPolicy(
    val explicitReleaseId: ChapterReleaseId? = null,
    val previousReleaseId: ChapterReleaseId? = null,
    val previousPluginId: PluginId? = null,
    val previousSourceGroup: String? = null,
    val languageOrder: List<String> = emptyList(),
)

enum class ReleaseHealth(val rank: Int) {
    UNAVAILABLE(0),
    DEGRADED(1),
    HEALTHY(2),
}
