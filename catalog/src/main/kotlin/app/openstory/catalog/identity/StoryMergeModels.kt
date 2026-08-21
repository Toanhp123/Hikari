package app.openstory.catalog.identity

import app.openstory.common.id.PluginId
import app.openstory.common.id.StoryId

data class UserStateFootprint(
    val hasLibraryMembership: Boolean,
    val readingProgressCount: Int,
    val protectedContentMappingCount: Int,
    val hasPinnedPrimary: Boolean,
    val manualChapterOverrideCount: Int,
) {
    init {
        require(readingProgressCount >= 0)
        require(protectedContentMappingCount >= 0)
        require(manualChapterOverrideCount >= 0)
    }

    val meaningfulDomainCount: Int
        get() = listOf(
            hasLibraryMembership,
            readingProgressCount > 0,
            protectedContentMappingCount > 0,
            hasPinnedPrimary,
            manualChapterOverrideCount > 0,
        ).count { it }

    val meaningfulStateTotal: Int
        get() = (if (hasLibraryMembership) 1 else 0) +
            readingProgressCount +
            protectedContentMappingCount +
            (if (hasPinnedPrimary) 1 else 0) +
            manualChapterOverrideCount
}

enum class StoryMergeOrigin {
    AUTO_RECONCILIATION,
    USER_REVIEW_APPROVAL,
    MANUAL_MAINTENANCE,
}

data class ProtectedContentMappingConflict(
    val pluginId: PluginId,
    val candidateSourceStoryIds: Set<String>,
) {
    init {
        require(candidateSourceStoryIds.size >= 2)
        require(candidateSourceStoryIds.none(String::isBlank))
    }
}

sealed interface StoryMergeResolution {
    data class ContentMappingTarget(
        val pluginId: PluginId,
        val sourceStoryId: String,
    ) : StoryMergeResolution {
        init {
            require(sourceStoryId.isNotBlank())
        }
    }
}

data class StoryMergeRequest(
    val requestId: String,
    val leftStoryId: StoryId,
    val rightStoryId: StoryId,
    val origin: StoryMergeOrigin,
    val reconciliationCaseId: String?,
    val evidenceFingerprint: String,
    val reconciliationPolicyVersion: Int,
    val resolutions: List<StoryMergeResolution> = emptyList(),
) {
    init {
        require(requestId.isNotBlank())
        require(leftStoryId != rightStoryId)
        require(reconciliationCaseId == null || reconciliationCaseId.isNotBlank())
        require(evidenceFingerprint.isNotBlank())
        require(reconciliationPolicyVersion > 0)
    }
}

data class StoryMergeCandidate(
    val storyId: StoryId,
    val identityRevision: Long,
    val createdAtEpochMillis: Long?,
    val footprint: UserStateFootprint,
) {
    init {
        require(identityRevision >= 0L)
        require(createdAtEpochMillis == null || createdAtEpochMillis >= 0L)
    }
}

data class StoryMergeSelection(
    val survivor: StoryMergeCandidate,
    val retired: StoryMergeCandidate,
) {
    init {
        require(survivor.storyId != retired.storyId)
    }
}

sealed interface StoryMergeResult {
    data class Merged(
        val survivorStoryId: StoryId,
        val mergeEventId: String,
    ) : StoryMergeResult {
        init {
            require(mergeEventId.isNotBlank())
        }
    }

    data class AlreadyMerged(val survivorStoryId: StoryId) : StoryMergeResult

    data class ReviewRequired(
        val reasons: Set<String>,
        val protectedContentMappingConflicts: List<ProtectedContentMappingConflict> = emptyList(),
    ) : StoryMergeResult {
        init {
            require(reasons.isNotEmpty())
            require(reasons.none(String::isBlank))
        }
    }

    data class StalePlan(val currentStoryIds: Set<StoryId>) : StoryMergeResult {
        init {
            require(currentStoryIds.isNotEmpty())
        }
    }
}
