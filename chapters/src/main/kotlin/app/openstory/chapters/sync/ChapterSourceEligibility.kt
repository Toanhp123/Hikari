package app.openstory.chapters.sync

import app.openstory.common.id.PluginId
import app.openstory.library.mapping.ContentMapping

data class ChapterSourceEligibility(
    val pluginId: PluginId,
    val sourceStoryId: String,
    val allowed: Boolean,
    val denialCode: String?,
) {
    init {
        require(allowed || !denialCode.isNullOrBlank())
        require(!allowed || denialCode == null)
    }
}

fun interface ChapterSourceEligibilityResolver {
    suspend fun evaluate(mapping: ContentMapping): ChapterSourceEligibility

    companion object {
        val ALLOW_ALL = ChapterSourceEligibilityResolver { mapping ->
            ChapterSourceEligibility(
                pluginId = mapping.pluginId,
                sourceStoryId = mapping.sourceStoryId,
                allowed = true,
                denialCode = null,
            )
        }
    }
}
