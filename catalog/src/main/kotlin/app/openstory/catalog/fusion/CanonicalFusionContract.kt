package app.openstory.catalog.fusion

import app.openstory.catalog.canonical.CanonicalGeneration
import app.openstory.common.id.StoryId

enum class CanonicalFusionReason {
    BOOTSTRAP,
    SOURCE_EVIDENCE_CHANGED,
    SOURCE_AVAILABILITY_CHANGED,
    SOURCE_PREFERENCE_CHANGED,
    POLICY_REEVALUATION,
    POST_MERGE,
}

sealed interface CanonicalFusionResult {
    data class Promoted(val generation: CanonicalGeneration) : CanonicalFusionResult
    data class Unchanged(val active: CanonicalGeneration) : CanonicalFusionResult
    data class Preparing(val storyId: StoryId) : CanonicalFusionResult
    data class Failed(val storyId: StoryId, val code: String, val retryable: Boolean) : CanonicalFusionResult {
        init {
            require(code.isNotBlank())
        }
    }
}

fun interface CanonicalGenerationRebuilder {
    suspend fun rebuild(storyId: StoryId, reason: CanonicalFusionReason): CanonicalFusionResult
}
