package app.openstory.model

enum class MappingOrigin {
    AUTOMATIC,
    PLUGIN,
    USER,
}

data class ContentMapping(
    val id: ContentMappingId,
    val storyId: StoryId,
    val pluginId: PluginId,
    val externalStoryId: String,
    val sourceUrl: String,
    val language: LanguageTag,
    val origin: MappingOrigin,
    val confidence: Double,
    val userLocked: Boolean,
    val enabled: Boolean,
    val lastSuccessfulSyncAtEpochMillis: Long?,
    val nextEligibleSyncAtEpochMillis: Long?,
    val failureState: String?,
) {
    init {
        require(externalStoryId.isNotBlank()) {
            "External story ID must not be blank"
        }
        require(sourceUrl.isNotBlank()) {
            "Source URL must not be blank"
        }
        require(confidence in 0.0..1.0) {
            "Mapping confidence must be between 0 and 1"
        }
    }
}
