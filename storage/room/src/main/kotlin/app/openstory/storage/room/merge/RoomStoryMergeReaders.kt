package app.openstory.storage.room.merge

import app.openstory.catalog.canonical.CanonicalSourcePreference
import app.openstory.catalog.canonical.CanonicalSourcePreferenceMode
import app.openstory.catalog.evidence.CatalogEvidenceFingerprints
import app.openstory.catalog.identity.SourceKey
import app.openstory.catalog.identity.StoryIdentityInvariantException
import app.openstory.catalog.model.ContentType
import app.openstory.chapters.model.CanonicalChapter
import app.openstory.chapters.model.ChapterAggregationOverride
import app.openstory.chapters.model.ChapterKind
import app.openstory.chapters.model.ChapterOverrideKind
import app.openstory.chapters.model.ChapterRelease
import app.openstory.chapters.model.ParsedChapterLabel
import app.openstory.chapters.repository.ChapterGraphSnapshot
import app.openstory.chapters.repository.ChapterSyncPhase
import app.openstory.chapters.repository.ChapterSyncState
import app.openstory.common.id.CanonicalChapterId
import app.openstory.common.id.ChapterReleaseId
import app.openstory.common.id.PluginId
import app.openstory.common.id.StoryId
import app.openstory.library.LibraryEntry
import app.openstory.library.LibraryStatus
import app.openstory.library.mapping.ContentMapping
import app.openstory.library.mapping.ContentMappingOrigin
import app.openstory.library.mapping.ContentMappingRejection
import app.openstory.reader.progress.ReadingPosition
import app.openstory.reader.progress.ReadingProgress
import app.openstory.storage.room.OpenStoryDatabase
import app.openstory.storage.room.catalog.CatalogEntryIdentifierEntity
import app.openstory.storage.room.catalog.StoryCanonicalStateEntity
import app.openstory.storage.room.catalog.toMetadataSnapshot
import app.openstory.storage.room.catalog.toModel
import app.openstory.storage.room.chapters.CanonicalChapterEntity
import app.openstory.storage.room.chapters.CanonicalChapterWithReleases
import app.openstory.storage.room.chapters.ChapterAggregationOverrideEntity
import app.openstory.storage.room.chapters.ChapterReleaseEntity
import app.openstory.storage.room.chapters.ChapterSyncStateEntity
import app.openstory.storage.room.library.ContentMappingEntity
import app.openstory.storage.room.library.ContentMappingRejectionEntity
import app.openstory.storage.room.library.LibraryEntity
import app.openstory.storage.room.reader.ReadingProgressEntity
import java.math.BigDecimal

internal class RoomStoryMergeReaders(
    private val database: OpenStoryDatabase,
) : StoryMergeSnapshotReader {
    private val catalogDao = database.catalogDao()
    private val canonicalDao = database.canonicalCatalogDao()
    private val libraryDao = database.libraryDao()
    private val chapterDao = database.chapterDao()
    private val syncDao = database.chapterSyncDao()
    private val progressDao = database.readingProgressDao()

    override suspend fun read(storyId: StoryId): StoryMergeSnapshot? {
        val story = catalogDao.findStory(storyId.value) ?: return null
        val canonicalState = canonicalDao.canonicalState(storyId.value)
            ?: throw StoryIdentityInvariantException("Missing canonical state for ${storyId.value}")
        val entries = catalogDao.entriesForStory(storyId.value)
        val sourceIdentityFingerprints = linkedMapOf<SourceKey, String>()
        entries.sortedWith(compareBy({ it.pluginId }, { it.sourceId })).forEach { entry ->
            val identifiers = catalogDao.identifiers(entry.pluginId, entry.sourceId)
                .map(CatalogEntryIdentifierEntity::toModel)
                .toSet()
            val key = SourceKey(PluginId(entry.pluginId), entry.sourceId)
            sourceIdentityFingerprints[key] = CatalogEvidenceFingerprints.identity(
                entry.toMetadataSnapshot(identifiers).entry,
            )
        }
        val groups = chapterDao.groups(storyId.value)
        return StoryMergeSnapshot(
            storyId = storyId,
            contentType = ContentType.valueOf(story.contentType),
            identityRevision = canonicalState.identityRevision,
            createdAtEpochMillis = canonicalState.createdAtEpochMillis,
            sourceKeys = sourceIdentityFingerprints.keys.toCollection(linkedSetOf()),
            sourceIdentityFingerprints = sourceIdentityFingerprints,
            sourcePreference = canonicalState.toPreference(),
            libraryEntry = libraryDao.find(storyId.value)?.toModel(),
            mappings = libraryDao.mappingsForStory(storyId.value).map(ContentMappingEntity::toModel),
            rejections = libraryDao.rejectionsForStory(storyId.value)
                .map(ContentMappingRejectionEntity::toModel),
            chapterGraph = groups.toSnapshot(chapterDao.overrides(storyId.value)),
            syncStates = syncDao.statesForStory(storyId.value).map(ChapterSyncStateEntity::toModel),
            readingProgress = progressDao.progressForStory(storyId.value).map(ReadingProgressEntity::toModel),
        )
    }
}

private fun StoryCanonicalStateEntity.toPreference() = CanonicalSourcePreference(
    storyId = StoryId(storyId),
    mode = CanonicalSourcePreferenceMode.valueOf(preferenceMode),
    pinnedSource = when (preferenceMode) {
        CanonicalSourcePreferenceMode.AUTO.name -> null
        CanonicalSourcePreferenceMode.PINNED.name -> SourceKey(
            PluginId(requireNotNull(pinnedPluginId)),
            requireNotNull(pinnedSourceId),
        )
        else -> error("Unknown canonical source preference mode: $preferenceMode")
    },
    revision = preferenceRevision,
)

private fun LibraryEntity.toModel() = LibraryEntry(
    storyId = StoryId(storyId),
    status = LibraryStatus.valueOf(status),
    addedAt = addedAtEpochMillis,
    updatedAt = updatedAtEpochMillis,
)

private fun ContentMappingEntity.toModel() = ContentMapping(
    storyId = StoryId(storyId),
    pluginId = PluginId(pluginId),
    sourceStoryId = sourceStoryId,
    origin = ContentMappingOrigin.valueOf(origin),
    policyVersion = policyVersion,
    updatedAt = updatedAtEpochMillis,
)

private fun ContentMappingRejectionEntity.toModel() = ContentMappingRejection(
    storyId = StoryId(storyId),
    pluginId = PluginId(pluginId),
    sourceStoryId = sourceStoryId,
    policyVersion = policyVersion,
    rejectedAt = rejectedAtEpochMillis,
)

private fun List<CanonicalChapterWithReleases>.toSnapshot(
    overrideRows: List<ChapterAggregationOverrideEntity>,
): ChapterGraphSnapshot {
    val chapters = map { group ->
        val releaseIds = group.releases.mapTo(linkedSetOf()) { ChapterReleaseId(it.chapterReleaseId) }
        group.chapter.toModel(releaseIds)
    }.sortedBy { it.id.value }
    val releases = flatMap(CanonicalChapterWithReleases::releases)
        .distinctBy(ChapterReleaseEntity::chapterReleaseId)
        .map(ChapterReleaseEntity::toModel)
        .sortedBy { it.id.value }
    val overrides = overrideRows.map(ChapterAggregationOverrideEntity::toModel)
        .sortedBy { it.releaseId.value }
    return ChapterGraphSnapshot(chapters, releases, overrides)
}

private fun CanonicalChapterEntity.toModel(releaseIds: Set<ChapterReleaseId>) = CanonicalChapter(
    id = CanonicalChapterId(canonicalChapterId),
    storyId = StoryId(storyId),
    parsedLabel = parsedLabel(kind, volume, chapter, part, normalizedTitle),
    displayLabel = displayLabel,
    tombstoned = tombstoned,
    releaseIds = releaseIds,
)

private fun ChapterReleaseEntity.toModel() = ChapterRelease(
    id = ChapterReleaseId(chapterReleaseId),
    storyId = StoryId(storyId),
    pluginId = PluginId(pluginId),
    sourceStoryId = sourceStoryId,
    sourceReleaseId = sourceReleaseId,
    displayLabel = displayLabel,
    parsedLabel = parsedLabel(kind, volume, chapter, part, normalizedTitle),
    languageTag = languageTag,
    publishedAtEpochMillis = publishedAtEpochMillis,
    canonicalChapterId = canonicalChapterId?.let(::CanonicalChapterId),
)

private fun ChapterAggregationOverrideEntity.toModel() = ChapterAggregationOverride(
    releaseId = ChapterReleaseId(chapterReleaseId),
    canonicalChapterId = canonicalChapterId?.let(::CanonicalChapterId),
    kind = ChapterOverrideKind.valueOf(kind),
)

private fun ChapterSyncStateEntity.toModel() = ChapterSyncState(
    storyId = StoryId(storyId),
    pluginId = PluginId(pluginId),
    sourceStoryId = sourceStoryId,
    phase = ChapterSyncPhase.valueOf(phase),
    cursor = cursor,
    checkpoint = checkpoint,
    fingerprint = fingerprint,
    updatedAtEpochMillis = updatedAtEpochMillis,
)

private fun ReadingProgressEntity.toModel() = ReadingProgress(
    storyId = StoryId(storyId),
    canonicalChapterId = CanonicalChapterId(canonicalChapterId),
    releaseId = ChapterReleaseId(chapterReleaseId),
    contentFingerprint = contentFingerprint,
    position = ReadingPosition(blockId, characterOffset, fraction),
    completedAtEpochMillis = completedAtEpochMillis,
    updatedAtEpochMillis = updatedAtEpochMillis,
)

private fun parsedLabel(
    kind: String,
    volume: String?,
    chapter: String?,
    part: Int?,
    normalizedTitle: String?,
) = ParsedChapterLabel(
    kind = ChapterKind.valueOf(kind),
    volume = volume?.let(::BigDecimal),
    chapter = chapter?.let(::BigDecimal),
    part = part,
    normalizedTitle = normalizedTitle,
)
