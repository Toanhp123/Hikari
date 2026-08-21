package app.openstory.chapters.merge

import app.openstory.chapters.model.ChapterAggregationOverride
import app.openstory.chapters.repository.ChapterGraphSnapshot
import app.openstory.chapters.repository.ChapterSyncState
import app.openstory.common.id.CanonicalChapterId
import app.openstory.common.id.ChapterReleaseId
import app.openstory.common.id.PluginId
import app.openstory.common.id.StoryId
import app.openstory.common.merge.DomainMergeDecision

const val CHAPTER_GRAPH_ID_COLLISION = "chapter.graph_id_collision"
const val CHAPTER_MANUAL_OVERRIDE_CONFLICT = "chapter.manual_override_conflict"
const val CHAPTER_MANUAL_OVERRIDE_INVALID = "chapter.manual_override_invalid"
const val CHAPTER_SYNC_STATE_INVALID = "chapter.sync_state_invalid"

data class ChapterStoryMergeInput(
    val survivorStoryId: StoryId,
    val retiredStoryId: StoryId,
    val survivorGraph: ChapterGraphSnapshot,
    val retiredGraph: ChapterGraphSnapshot,
    val syncStates: List<ChapterSyncState>,
) {
    init {
        require(survivorStoryId != retiredStoryId)
    }
}

data class ChapterStoryMergePlan(
    val movedCanonicalChapterIds: Set<CanonicalChapterId>,
    val movedReleaseIds: Set<ChapterReleaseId>,
    val preservedOverrides: List<ChapterAggregationOverride>,
    val syncStatesToMove: List<ChapterSyncState>,
    val syncKeysToInvalidate: Set<ChapterSyncKey>,
    val requiresDerivedReaggregation: Boolean,
)

data class ChapterSyncKey(
    val pluginId: PluginId,
    val sourceStoryId: String,
) {
    init {
        require(sourceStoryId.isNotBlank())
    }
}

class ChapterStoryMergePolicy {
    fun plan(input: ChapterStoryMergeInput): DomainMergeDecision<ChapterStoryMergePlan> {
        val overrideDecision = mergeOverrides(input)
        val blocker = graphCollisionReason(input)
            ?: (overrideDecision as? OverrideDecision.Blocked)?.reason
            ?: CHAPTER_SYNC_STATE_INVALID.takeIf {
                input.syncStates.any { state ->
                    state.storyId != input.survivorStoryId && state.storyId != input.retiredStoryId
                }
            }
        return if (blocker != null) {
            DomainMergeDecision.RequiresReview(setOf(blocker))
        } else {
            readyPlan(input, (overrideDecision as OverrideDecision.Ready).overrides)
        }
    }

    private fun readyPlan(
        input: ChapterStoryMergeInput,
        overrides: List<ChapterAggregationOverride>,
    ): DomainMergeDecision.Ready<ChapterStoryMergePlan> {
        val syncGroups = input.syncStates.groupBy { ChapterSyncKey(it.pluginId, it.sourceStoryId) }
        val invalidated = syncGroups
            .filterValues { states -> states.map(ChapterSyncState::storyId).distinct().size > 1 }
            .keys
            .sortedWith(compareBy<ChapterSyncKey> { it.pluginId.value }.thenBy { it.sourceStoryId })
            .toCollection(linkedSetOf())
        val moves = syncGroups.entries
            .asSequence()
            .filter { (key, _) -> key !in invalidated }
            .mapNotNull { (_, states) ->
                states.singleOrNull { it.storyId == input.retiredStoryId }
            }
            .map { it.copy(storyId = input.survivorStoryId) }
            .sortedWith(compareBy<ChapterSyncState> { it.pluginId.value }.thenBy { it.sourceStoryId })
            .toList()

        val movedChapters = input.retiredGraph.chapters
            .mapTo(sortedSetOf(compareBy(CanonicalChapterId::value))) { it.id }
        val movedReleases = input.retiredGraph.releases
            .mapTo(sortedSetOf(compareBy(ChapterReleaseId::value))) { it.id }
        val requiresDerived = movedChapters.isNotEmpty() || movedReleases.isNotEmpty() || invalidated.isNotEmpty()
        return DomainMergeDecision.Ready(
            ChapterStoryMergePlan(
                movedCanonicalChapterIds = movedChapters,
                movedReleaseIds = movedReleases,
                preservedOverrides = overrides,
                syncStatesToMove = moves,
                syncKeysToInvalidate = invalidated,
                requiresDerivedReaggregation = requiresDerived,
            ),
        )
    }

    private fun graphCollisionReason(input: ChapterStoryMergeInput): String? {
        val survivorChapterIds = input.survivorGraph.chapters.mapTo(hashSetOf()) { it.id }
        val chapterCollision = input.retiredGraph.chapters.any { it.id in survivorChapterIds }
        val survivorReleaseIds = input.survivorGraph.releases.mapTo(hashSetOf()) { it.id }
        val releaseCollision = input.retiredGraph.releases.any { it.id in survivorReleaseIds }
        return CHAPTER_GRAPH_ID_COLLISION.takeIf { chapterCollision || releaseCollision }
    }

    private fun mergeOverrides(input: ChapterStoryMergeInput): OverrideDecision {
        val combined = input.survivorGraph.overrides + input.retiredGraph.overrides
        val byRelease = combined.groupBy(ChapterAggregationOverride::releaseId)
        byRelease.values.forEach { overrides ->
            if (overrides.distinct().size > 1) {
                return OverrideDecision.Blocked(CHAPTER_MANUAL_OVERRIDE_CONFLICT)
            }
        }
        val releaseIds = (input.survivorGraph.releases + input.retiredGraph.releases)
            .mapTo(hashSetOf()) { it.id }
        val chapterIds = (input.survivorGraph.chapters + input.retiredGraph.chapters)
            .mapTo(hashSetOf()) { it.id }
        val normalized = byRelease.values
            .map { it.first() }
            .sortedBy { it.releaseId.value }
        val invalid = normalized.any { override ->
            override.releaseId !in releaseIds ||
                (override.canonicalChapterId != null && override.canonicalChapterId !in chapterIds)
        }
        return if (invalid) {
            OverrideDecision.Blocked(CHAPTER_MANUAL_OVERRIDE_INVALID)
        } else {
            OverrideDecision.Ready(normalized)
        }
    }

    private sealed interface OverrideDecision {
        data class Ready(val overrides: List<ChapterAggregationOverride>) : OverrideDecision
        data class Blocked(val reason: String) : OverrideDecision
    }
}
