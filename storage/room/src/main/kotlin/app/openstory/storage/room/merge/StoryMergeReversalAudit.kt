package app.openstory.storage.room.merge

import app.openstory.catalog.canonical.CanonicalSourcePreference
import app.openstory.catalog.canonical.CanonicalSourcePreferenceMode
import app.openstory.catalog.identity.SourceKey
import app.openstory.chapters.model.ChapterAggregationOverride
import app.openstory.chapters.model.ChapterOverrideKind
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
import app.openstory.storage.room.catalog.StoryMergeEventEntity
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.longOrNull

internal data class StoryMergeReversalAudit(
    val mergeEventId: String,
    val survivorStoryId: StoryId,
    val retiredStoryId: StoryId,
    val postMergeAuthoritativeFingerprint: String,
    val survivorBefore: StoryMergeReversalAuditSnapshot,
    val retiredBefore: StoryMergeReversalAuditSnapshot,
)

internal data class StoryMergeReversalAuditSnapshot(
    val storyId: StoryId,
    val contentType: String,
    val createdAtEpochMillis: Long?,
    val authoritativeFingerprint: String,
    val identityRevision: Long,
    val sourcePreference: CanonicalSourcePreference,
    val sourceKeys: Set<SourceKey>,
    val libraryEntry: LibraryEntry?,
    val mappings: List<ContentMapping>,
    val rejections: List<ContentMappingRejection>,
    val syncStates: List<ChapterSyncState>,
    val chapterIds: Set<CanonicalChapterId>,
    val releaseIds: Set<ChapterReleaseId>,
    val manualOverrides: List<ChapterAggregationOverride>,
    val readingProgress: List<ReadingProgress>,
) {
    init {
        require(identityRevision >= 0L)
        require(createdAtEpochMillis == null || createdAtEpochMillis >= 0L)
        require(authoritativeFingerprint.isNotBlank())
        require(sourcePreference.storyId == storyId)
        require(libraryEntry == null || libraryEntry.storyId == storyId)
        require(mappings.all { it.storyId == storyId })
        require(rejections.all { it.storyId == storyId })
        require(syncStates.all { it.storyId == storyId })
        require(readingProgress.all { it.storyId == storyId })
        require(sourcePreference.pinnedSource == null || sourcePreference.pinnedSource in sourceKeys)
    }
}

internal object StoryMergeReversalAuditParser {
    fun parse(event: StoryMergeEventEntity): StoryMergeReversalAudit {
        require(event.reversalPayloadVersion == STORY_MERGE_REVERSAL_PAYLOAD_VERSION) {
            "Unsupported merge reversal payload version ${event.reversalPayloadVersion}"
        }
        val root = Json.parseToJsonElement(event.reversalPayload).jsonObject
        require(root.requiredInt("version") == STORY_MERGE_REVERSAL_PAYLOAD_VERSION)
        val survivorId = StoryId(root.requiredString("survivorStoryId"))
        val retiredId = StoryId(root.requiredString("retiredStoryId"))
        require(survivorId.value == event.survivorStoryId)
        require(retiredId.value == event.retiredStoryId)
        return StoryMergeReversalAudit(
            mergeEventId = event.mergeEventId,
            survivorStoryId = survivorId,
            retiredStoryId = retiredId,
            postMergeAuthoritativeFingerprint = root.requiredString("postMergeAuthoritativeFingerprint"),
            survivorBefore = root.requiredObject("survivorBefore").snapshot(survivorId),
            retiredBefore = root.requiredObject("retiredBefore").snapshot(retiredId),
        )
    }

    private fun JsonObject.snapshot(expectedStoryId: StoryId): StoryMergeReversalAuditSnapshot {
        val storyId = StoryId(requiredString("storyId"))
        require(storyId == expectedStoryId)
        val sourceKeys = requiredArray("sourceKeys").mapTo(linkedSetOf()) { element ->
            val value = element.jsonObject
            SourceKey(PluginId(value.requiredString("pluginId")), value.requiredString("sourceId"))
        }
        return StoryMergeReversalAuditSnapshot(
            storyId = storyId,
            contentType = requiredString("contentType"),
            createdAtEpochMillis = optionalLong("createdAtEpochMillis"),
            authoritativeFingerprint = requiredString("authoritativeFingerprint"),
            identityRevision = requiredLong("identityRevision"),
            sourcePreference = requiredObject("sourcePreference").sourcePreference(storyId),
            sourceKeys = sourceKeys,
            libraryEntry = get("library")?.jsonObject?.library(storyId),
            mappings = requiredArray("mappings").map { it.jsonObject.mapping(storyId) },
            rejections = requiredArray("mappingRejections").map { it.jsonObject.rejection(storyId) },
            syncStates = requiredArray("syncStates").map { it.jsonObject.syncState(storyId) },
            chapterIds = requiredArray("chapterIds").mapTo(linkedSetOf()) {
                CanonicalChapterId(it.jsonPrimitive.requiredContent())
            },
            releaseIds = requiredArray("releaseIds").mapTo(linkedSetOf()) {
                ChapterReleaseId(it.jsonPrimitive.requiredContent())
            },
            manualOverrides = requiredArray("manualOverrides").map { it.jsonObject.override() },
            readingProgress = requiredArray("progress").map { it.jsonObject.progress(storyId) },
        )
    }

    private fun JsonObject.sourcePreference(storyId: StoryId): CanonicalSourcePreference {
        val mode = CanonicalSourcePreferenceMode.valueOf(requiredString("mode"))
        val pinned = when (mode) {
            CanonicalSourcePreferenceMode.AUTO -> null
            CanonicalSourcePreferenceMode.PINNED -> SourceKey(
                PluginId(requiredString("pinnedPluginId")),
                requiredString("pinnedSourceId"),
            )
        }
        return CanonicalSourcePreference(
            storyId = storyId,
            mode = mode,
            pinnedSource = pinned,
            revision = requiredLong("revision"),
        )
    }

    private fun JsonObject.library(storyId: StoryId) = LibraryEntry(
        storyId = storyId,
        status = LibraryStatus.valueOf(requiredString("status")),
        addedAt = requiredLong("addedAt"),
        updatedAt = requiredLong("updatedAt"),
    )

    private fun JsonObject.mapping(storyId: StoryId) = ContentMapping(
        storyId = storyId,
        pluginId = PluginId(requiredString("pluginId")),
        sourceStoryId = requiredString("sourceStoryId"),
        origin = ContentMappingOrigin.valueOf(requiredString("origin")),
        policyVersion = requiredInt("policyVersion"),
        updatedAt = requiredLong("updatedAt"),
    )

    private fun JsonObject.rejection(storyId: StoryId) = ContentMappingRejection(
        storyId = storyId,
        pluginId = PluginId(requiredString("pluginId")),
        sourceStoryId = requiredString("sourceStoryId"),
        policyVersion = requiredInt("policyVersion"),
        rejectedAt = requiredLong("rejectedAt"),
    )

    private fun JsonObject.syncState(storyId: StoryId) = ChapterSyncState(
        storyId = storyId,
        pluginId = PluginId(requiredString("pluginId")),
        sourceStoryId = requiredString("sourceStoryId"),
        phase = ChapterSyncPhase.valueOf(requiredString("phase")),
        cursor = optionalString("cursor"),
        checkpoint = optionalString("checkpoint"),
        fingerprint = optionalString("fingerprint"),
        updatedAtEpochMillis = requiredLong("updatedAtEpochMillis"),
    )

    private fun JsonObject.override() = ChapterAggregationOverride(
        releaseId = ChapterReleaseId(requiredString("releaseId")),
        canonicalChapterId = optionalString("canonicalChapterId")?.let(::CanonicalChapterId),
        kind = ChapterOverrideKind.valueOf(requiredString("kind")),
    )

    private fun JsonObject.progress(storyId: StoryId) = ReadingProgress(
        storyId = storyId,
        canonicalChapterId = CanonicalChapterId(requiredString("canonicalChapterId")),
        releaseId = ChapterReleaseId(requiredString("releaseId")),
        contentFingerprint = requiredString("contentFingerprint"),
        position = ReadingPosition(
            blockId = requiredString("blockId"),
            characterOffset = requiredInt("characterOffset"),
            fraction = Float.fromBits(requiredInt("fractionBits")),
        ),
        completedAtEpochMillis = optionalLong("completedAtEpochMillis"),
        updatedAtEpochMillis = requiredLong("updatedAtEpochMillis"),
    )

    private fun JsonObject.requiredObject(name: String): JsonObject =
        requireNotNull(get(name)) { "Missing merge reversal field $name" }.jsonObject

    private fun JsonObject.requiredArray(name: String): JsonArray =
        requireNotNull(get(name)) { "Missing merge reversal field $name" }.jsonArray

    private fun JsonObject.requiredString(name: String): String =
        requireNotNull(get(name)?.jsonPrimitive?.contentOrNull) { "Missing merge reversal field $name" }
            .also { require(it.isNotBlank()) { "Blank merge reversal field $name" } }

    private fun JsonObject.optionalString(name: String): String? = get(name)?.jsonPrimitive?.contentOrNull

    private fun JsonObject.requiredLong(name: String): Long =
        requireNotNull(get(name)?.jsonPrimitive?.longOrNull) { "Invalid merge reversal long $name" }

    private fun JsonObject.optionalLong(name: String): Long? = get(name)?.jsonPrimitive?.longOrNull

    private fun JsonObject.requiredInt(name: String): Int =
        requireNotNull(get(name)?.jsonPrimitive?.intOrNull) { "Invalid merge reversal int $name" }

    private fun JsonPrimitive.requiredContent(): String =
        requireNotNull(contentOrNull).also { require(it.isNotBlank()) }
}
