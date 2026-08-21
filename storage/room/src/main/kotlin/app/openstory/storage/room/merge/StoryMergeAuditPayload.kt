package app.openstory.storage.room.merge

import kotlinx.serialization.json.add
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import kotlinx.serialization.json.putJsonObject

internal const val STORY_MERGE_REVERSAL_PAYLOAD_VERSION = 1

internal fun storyMergeReversalPayload(
    plan: PreparedStoryGraphMerge,
    survivorBefore: StoryMergeSnapshot,
    retiredBefore: StoryMergeSnapshot,
    postMergeFingerprint: String,
): String = buildJsonObject {
    put("version", STORY_MERGE_REVERSAL_PAYLOAD_VERSION)
    put("survivorStoryId", plan.survivorStoryId.value)
    put("retiredStoryId", plan.retiredStoryId.value)
    put("postMergeAuthoritativeFingerprint", postMergeFingerprint)
    put("survivorBefore", snapshotAudit(survivorBefore))
    put("retiredBefore", snapshotAudit(retiredBefore))
}.toString()

private fun snapshotAudit(snapshot: StoryMergeSnapshot) = buildJsonObject {
    put("storyId", snapshot.storyId.value)
    put("contentType", snapshot.contentType.name)
    snapshot.createdAtEpochMillis?.let { put("createdAtEpochMillis", it) }
    put("authoritativeFingerprint", snapshot.authoritativeFingerprint())
    put("identityRevision", snapshot.identityRevision)
    putJsonObject("sourcePreference") {
        put("mode", snapshot.sourcePreference.mode.name)
        put("revision", snapshot.sourcePreference.revision)
        snapshot.sourcePreference.pinnedSource?.let { pinned ->
            put("pinnedPluginId", pinned.pluginId.value)
            put("pinnedSourceId", pinned.sourceId)
        }
    }
    putJsonArray("sourceKeys") {
        snapshot.sourceKeys
            .sortedWith(compareBy({ it.pluginId.value }, { it.sourceId }))
            .forEach { key ->
                add(buildJsonObject {
                    put("pluginId", key.pluginId.value)
                    put("sourceId", key.sourceId)
                })
            }
    }
    snapshot.libraryEntry?.let { library ->
        putJsonObject("library") {
            put("status", library.status.name)
            put("addedAt", library.addedAt)
            put("updatedAt", library.updatedAt)
        }
    }
    putJsonArray("mappings") {
        snapshot.mappings
            .sortedWith(compareBy({ it.pluginId.value }, { it.sourceStoryId }))
            .forEach { mapping ->
                add(buildJsonObject {
                    put("pluginId", mapping.pluginId.value)
                    put("sourceStoryId", mapping.sourceStoryId)
                    put("origin", mapping.origin.name)
                    put("policyVersion", mapping.policyVersion)
                    put("updatedAt", mapping.updatedAt)
                })
            }
    }
    putJsonArray("mappingRejections") {
        snapshot.rejections
            .sortedWith(
                compareBy({ it.pluginId.value }, { it.sourceStoryId }, { it.policyVersion }),
            )
            .forEach { rejection ->
                add(buildJsonObject {
                    put("pluginId", rejection.pluginId.value)
                    put("sourceStoryId", rejection.sourceStoryId)
                    put("policyVersion", rejection.policyVersion)
                    put("rejectedAt", rejection.rejectedAt)
                })
            }
    }
    putJsonArray("syncStates") {
        snapshot.syncStates
            .sortedWith(compareBy({ it.pluginId.value }, { it.sourceStoryId }))
            .forEach { state ->
                add(buildJsonObject {
                    put("pluginId", state.pluginId.value)
                    put("sourceStoryId", state.sourceStoryId)
                    put("phase", state.phase.name)
                    state.cursor?.let { put("cursor", it) }
                    state.checkpoint?.let { put("checkpoint", it) }
                    state.fingerprint?.let { put("fingerprint", it) }
                    put("updatedAtEpochMillis", state.updatedAtEpochMillis)
                })
            }
    }
    putJsonArray("chapterIds") {
        snapshot.chapterGraph.chapters.sortedBy { it.id.value }.forEach { add(it.id.value) }
    }
    putJsonArray("releaseIds") {
        snapshot.chapterGraph.releases.sortedBy { it.id.value }.forEach { add(it.id.value) }
    }
    putJsonArray("manualOverrides") {
        snapshot.chapterGraph.overrides.sortedBy { it.releaseId.value }.forEach { override ->
            add(buildJsonObject {
                put("releaseId", override.releaseId.value)
                override.canonicalChapterId?.let { put("canonicalChapterId", it.value) }
                put("kind", override.kind.name)
            })
        }
    }
    putJsonArray("progress") {
        snapshot.readingProgress.sortedBy { it.canonicalChapterId.value }.forEach { progress ->
            add(buildJsonObject {
                put("canonicalChapterId", progress.canonicalChapterId.value)
                put("releaseId", progress.releaseId.value)
                put("contentFingerprint", progress.contentFingerprint)
                put("blockId", progress.position.blockId)
                put("characterOffset", progress.position.characterOffset)
                put("fractionBits", progress.position.fraction.toRawBits())
                progress.completedAtEpochMillis?.let { put("completedAtEpochMillis", it) }
                put("updatedAtEpochMillis", progress.updatedAtEpochMillis)
            })
        }
    }
}
