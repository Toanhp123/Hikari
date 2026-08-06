package app.openstory.database.mapping

import app.openstory.database.entity.ChapterReleaseEntity
import app.openstory.model.ChapterRelease

internal fun ChapterRelease.toEntity():
    ChapterReleaseEntity =
    ChapterReleaseEntity(
        releaseId = id.value,
        contentMappingId =
            contentMappingId.value,
        pluginId = pluginId.value,
        sourceReleaseId =
            externalReleaseId,
        sourceUrl = sourceUrl,
        language = language.value,
        title = title,
        volumeNumber = volumeNumber,
        chapterNumber = chapterNumber,
        partNumber = partNumber,
        translatorOrUploader =
            translatorOrUploader,
        publishedAtEpochMillis =
            publishedAtEpochMillis,
        updatedAtEpochMillis =
            updatedAtEpochMillis,
        contentFingerprint =
            contentFingerprint,
        availability =
            availability.name,
        fetchedAtEpochMillis =
            fetchedAtEpochMillis,
    )
