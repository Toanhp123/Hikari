package app.openstory.storage.room.merge

import app.openstory.catalog.canonical.CanonicalSourcePreference
import app.openstory.catalog.identity.ProtectedContentMappingConflict
import app.openstory.catalog.identity.SourceKey
import app.openstory.catalog.identity.StoryMergeRequest
import app.openstory.catalog.identity.UserStateFootprint
import app.openstory.catalog.model.ContentType
import app.openstory.chapters.merge.ChapterStoryMergePlan
import app.openstory.chapters.repository.ChapterGraphSnapshot
import app.openstory.chapters.repository.ChapterSyncState
import app.openstory.common.id.StoryId
import app.openstory.library.LibraryEntry
import app.openstory.library.mapping.ContentMapping
import app.openstory.library.mapping.ContentMappingRejection
import app.openstory.library.merge.ContentMappingMergePlan
import app.openstory.library.merge.LibraryMergePlan
import app.openstory.reader.progress.ReadingProgress
import app.openstory.reader.progress.ReadingProgressMergePlan
import java.nio.ByteBuffer
import java.security.MessageDigest

data class StoryGraphVersion(
    val survivorIdentityRevision: Long,
    val retiredIdentityRevision: Long,
    val survivorAuthoritativeFingerprint: String,
    val retiredAuthoritativeFingerprint: String,
)

data class PreparedStoryGraphMerge(
    val request: StoryMergeRequest,
    val survivorStoryId: StoryId,
    val retiredStoryId: StoryId,
    val expectedVersion: StoryGraphVersion,
    val sourceKeysToMove: Set<SourceKey>,
    val sourcePreference: CanonicalSourcePreference,
    val libraryPlan: LibraryMergePlan,
    val mappingPlan: ContentMappingMergePlan,
    val chapterPlan: ChapterStoryMergePlan,
    val progressPlan: ReadingProgressMergePlan,
    val footprintBeforeMerge: Map<StoryId, UserStateFootprint>,
)

sealed interface StoryGraphMergePreparation {
    data class Ready(val plan: PreparedStoryGraphMerge) : StoryGraphMergePreparation

    data class ReviewRequired(
        val reasons: Set<String>,
        val protectedContentMappingConflicts: List<ProtectedContentMappingConflict> = emptyList(),
    ) : StoryGraphMergePreparation {
        init {
            require(reasons.isNotEmpty())
            require(reasons.none(String::isBlank))
        }
    }

    data class AlreadyCanonical(val survivorStoryId: StoryId) : StoryGraphMergePreparation
}

internal interface StoryMergeSnapshotReader {
    suspend fun read(storyId: StoryId): StoryMergeSnapshot?
}

internal data class StoryMergeSnapshot(
    val storyId: StoryId,
    val contentType: ContentType,
    val identityRevision: Long,
    val createdAtEpochMillis: Long?,
    val sourceKeys: Set<SourceKey>,
    val sourceIdentityFingerprints: Map<SourceKey, String> = emptyMap(),
    val sourcePreference: CanonicalSourcePreference,
    val libraryEntry: LibraryEntry?,
    val mappings: List<ContentMapping>,
    val rejections: List<ContentMappingRejection>,
    val chapterGraph: ChapterGraphSnapshot,
    val syncStates: List<ChapterSyncState>,
    val readingProgress: List<ReadingProgress>,
) {
    init {
        require(identityRevision >= 0L)
        require(createdAtEpochMillis == null || createdAtEpochMillis >= 0L)
        require(sourceIdentityFingerprints.keys.all { it in sourceKeys })
        require(sourceIdentityFingerprints.values.none(String::isBlank))
        require(sourcePreference.storyId == storyId)
        require(libraryEntry == null || libraryEntry.storyId == storyId)
        require(mappings.all { it.storyId == storyId })
        require(rejections.all { it.storyId == storyId })
        require(chapterGraph.chapters.all { it.storyId == storyId })
        require(chapterGraph.releases.all { it.storyId == storyId })
        require(syncStates.all { it.storyId == storyId })
        require(readingProgress.all { it.storyId == storyId })
    }

    fun footprint(): UserStateFootprint = UserStateFootprint(
        hasLibraryMembership = libraryEntry != null,
        readingProgressCount = readingProgress.size,
        protectedContentMappingCount = mappings.count { it.origin.isProtected },
        hasPinnedPrimary = sourcePreference.pinnedSource != null,
        manualChapterOverrideCount = chapterGraph.overrides.size,
    )

    fun authoritativeFingerprint(): String = AuthoritativeFingerprintBuilder()
        .value("storyId", storyId.value)
        .value("contentType", contentType.name)
        .value("identityRevision", identityRevision)
        .nullableValue("createdAtEpochMillis", createdAtEpochMillis)
        .values(
            "sourceKeys",
            sourceKeys.sortedWith(compareBy<SourceKey> { it.pluginId.value }.thenBy { it.sourceId })
                .map { "${it.pluginId.value}\u0000${it.sourceId}" },
        )
        .values(
            "sourceIdentityFingerprints",
            sourceIdentityFingerprints.entries
                .sortedWith(
                    compareBy<Map.Entry<SourceKey, String>> { it.key.pluginId.value }
                        .thenBy { it.key.sourceId },
                )
                .map { "${it.key.pluginId.value}\u0000${it.key.sourceId}\u0000${it.value}" },
        )
        .value("preferenceMode", sourcePreference.mode.name)
        .nullableValue("pinnedPluginId", sourcePreference.pinnedSource?.pluginId?.value)
        .nullableValue("pinnedSourceId", sourcePreference.pinnedSource?.sourceId)
        .value("preferenceRevision", sourcePreference.revision)
        .nullableValue(
            "library",
            libraryEntry?.let { "${it.status.name}\u0000${it.addedAt}\u0000${it.updatedAt}" },
        )
        .values(
            "mappings",
            mappings.sortedWith(compareBy<ContentMapping> { it.pluginId.value }.thenBy { it.sourceStoryId })
                .map {
                    listOf(
                        it.pluginId.value,
                        it.sourceStoryId,
                        it.origin.name,
                        it.policyVersion.toString(),
                        it.updatedAt.toString(),
                    ).joinToString("\u0000")
                },
        )
        .values(
            "rejections",
            rejections.sortedWith(
                compareBy<ContentMappingRejection> { it.pluginId.value }
                    .thenBy { it.sourceStoryId }
                    .thenBy { it.policyVersion },
            ).map {
                listOf(
                    it.pluginId.value,
                    it.sourceStoryId,
                    it.policyVersion.toString(),
                    it.rejectedAt.toString(),
                ).joinToString("\u0000")
            },
        )
        .values(
            "chapters",
            chapterGraph.chapters.sortedBy { it.id.value }.map {
                listOf(
                    it.id.value,
                    it.parsedLabel.kind.name,
                    it.parsedLabel.volume?.toPlainString().orEmpty(),
                    it.parsedLabel.chapter?.toPlainString().orEmpty(),
                    it.parsedLabel.part?.toString().orEmpty(),
                    it.parsedLabel.normalizedTitle.orEmpty(),
                    it.displayLabel,
                    it.tombstoned.toString(),
                    it.releaseIds.map { id -> id.value }.sorted().joinToString("\u0001"),
                ).joinToString("\u0000")
            },
        )
        .values(
            "releases",
            chapterGraph.releases.sortedBy { it.id.value }.map {
                listOf(
                    it.id.value,
                    it.pluginId.value,
                    it.sourceStoryId,
                    it.sourceReleaseId,
                    it.displayLabel,
                    it.parsedLabel.kind.name,
                    it.parsedLabel.volume?.toPlainString().orEmpty(),
                    it.parsedLabel.chapter?.toPlainString().orEmpty(),
                    it.parsedLabel.part?.toString().orEmpty(),
                    it.parsedLabel.normalizedTitle.orEmpty(),
                    it.languageTag,
                    it.publishedAtEpochMillis?.toString().orEmpty(),
                    it.canonicalChapterId?.value.orEmpty(),
                ).joinToString("\u0000")
            },
        )
        .values(
            "overrides",
            chapterGraph.overrides.sortedBy { it.releaseId.value }.map {
                "${it.releaseId.value}\u0000${it.canonicalChapterId?.value.orEmpty()}\u0000${it.kind.name}"
            },
        )
        .values(
            "sync",
            syncStates.sortedWith(compareBy<ChapterSyncState> { it.pluginId.value }.thenBy { it.sourceStoryId })
                .map {
                    listOf(
                        it.pluginId.value,
                        it.sourceStoryId,
                        it.phase.name,
                        it.cursor.orEmpty(),
                        it.checkpoint.orEmpty(),
                        it.fingerprint.orEmpty(),
                        it.updatedAtEpochMillis.toString(),
                    ).joinToString("\u0000")
                },
        )
        .values(
            "progress",
            readingProgress.sortedBy { it.canonicalChapterId.value }.map {
                listOf(
                    it.canonicalChapterId.value,
                    it.releaseId.value,
                    it.contentFingerprint,
                    it.position.blockId,
                    it.position.characterOffset.toString(),
                    it.position.fraction.toRawBits().toString(),
                    it.completedAtEpochMillis?.toString().orEmpty(),
                    it.updatedAtEpochMillis.toString(),
                ).joinToString("\u0000")
            },
        )
        .digest()
}

private class AuthoritativeFingerprintBuilder {
    private val digest = MessageDigest.getInstance("SHA-256")

    fun value(label: String, value: String): AuthoritativeFingerprintBuilder = apply {
        token(label)
        token(value)
    }

    fun value(label: String, value: Long): AuthoritativeFingerprintBuilder = value(label, value.toString())

    fun nullableValue(label: String, value: String?): AuthoritativeFingerprintBuilder = apply {
        token(label)
        token(if (value == null) "0" else "1")
        if (value != null) token(value)
    }

    fun nullableValue(label: String, value: Long?): AuthoritativeFingerprintBuilder =
        nullableValue(label, value?.toString())

    fun values(label: String, values: List<String>): AuthoritativeFingerprintBuilder = apply {
        token(label)
        token(values.size.toString())
        values.forEach(::token)
    }

    fun digest(): String = digest.digest().joinToString("") { byte -> "%02x".format(byte) }

    private fun token(value: String) {
        val bytes = value.toByteArray(Charsets.UTF_8)
        digest.update(ByteBuffer.allocate(Int.SIZE_BYTES).putInt(bytes.size).array())
        digest.update(bytes)
    }
}
