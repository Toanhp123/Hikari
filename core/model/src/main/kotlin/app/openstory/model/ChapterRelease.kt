package app.openstory.model

import java.math.BigDecimal

enum class ReleaseAvailability {
    AVAILABLE,
    TEMPORARILY_MISSING,
    REMOVED,
}

data class ChapterRelease(
    val id: ReleaseId,
    val chapterId: ChapterId,
    val contentMappingId: ContentMappingId,
    val pluginId: PluginId,
    val externalReleaseId: String,
    val sourceUrl: String,
    val language: LanguageTag,
    val title: String,
    val volumeNumber: BigDecimal?,
    val chapterNumber: BigDecimal?,
    val partNumber: BigDecimal?,
    val translatorOrUploader: String?,
    val publishedAtEpochMillis: Long?,
    val updatedAtEpochMillis: Long?,
    val contentFingerprint: String?,
    val availability: ReleaseAvailability,
    val fetchedAtEpochMillis: Long,
) {
    init {
        require(externalReleaseId.isNotBlank()) {
            "External release ID must not be blank"
        }
        require(sourceUrl.isNotBlank()) {
            "Source URL must not be blank"
        }
        require(title.isNotBlank()) {
            "Release title must not be blank"
        }
    }
}
