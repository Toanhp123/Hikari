package app.openstory.library.mapping

import app.openstory.common.id.PluginId
import app.openstory.common.id.StoryId

enum class ContentMappingOrigin {
    AUTOMATED,
    USER_APPROVED,
    USER_URL,
    ;

    val isProtected: Boolean
        get() = this != AUTOMATED
}

data class ContentMapping(
    val storyId: StoryId,
    val pluginId: PluginId,
    val sourceStoryId: String,
    val origin: ContentMappingOrigin,
    val policyVersion: Int,
    val updatedAt: Long,
) {
    init {
        require(sourceStoryId.isNotBlank()) { "Source story ID must not be blank" }
        require(sourceStoryId.none(Char::isISOControl)) {
            "Source story ID must not contain control characters"
        }
        require(policyVersion > 0) { "Policy version must be positive" }
        require(updatedAt >= 0L) { "Updated time must not be negative" }
    }
}

data class ContentMappingRejection(
    val storyId: StoryId,
    val pluginId: PluginId,
    val sourceStoryId: String,
    val policyVersion: Int,
    val rejectedAt: Long,
) {
    init {
        require(sourceStoryId.isNotBlank()) { "Rejected source story ID must not be blank" }
        require(policyVersion > 0) { "Policy version must be positive" }
        require(rejectedAt >= 0L) { "Rejected time must not be negative" }
    }
}

sealed interface ContentMappingWriteResult {
    val mapping: ContentMapping

    data class Written(
        override val mapping: ContentMapping,
        val changed: Boolean,
    ) : ContentMappingWriteResult

    data class Protected(
        override val mapping: ContentMapping,
    ) : ContentMappingWriteResult
}
