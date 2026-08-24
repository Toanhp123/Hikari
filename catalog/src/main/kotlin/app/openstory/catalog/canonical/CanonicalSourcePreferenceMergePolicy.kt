package app.openstory.catalog.canonical

import app.openstory.common.id.StoryId
import app.openstory.common.merge.DomainMergeDecision

const val CANONICAL_SOURCE_PREFERENCE_PIN_CONFLICT = "canonical_source_preference.pinned_conflict"

class CanonicalSourcePreferenceMergePolicy {
    fun plan(
        survivorId: StoryId,
        left: CanonicalSourcePreference,
        right: CanonicalSourcePreference,
    ): DomainMergeDecision<CanonicalSourcePreference> {
        val leftPin = left.pinnedSource
        val rightPin = right.pinnedSource
        if (leftPin != null && rightPin != null && leftPin != rightPin) {
            return DomainMergeDecision.RequiresReview(setOf(CANONICAL_SOURCE_PREFERENCE_PIN_CONFLICT))
        }
        val nextRevision = maxOf(left.revision, right.revision).let { current ->
            require(current < Long.MAX_VALUE) { "Canonical source preference revision exhausted" }
            current + 1L
        }
        val pin = leftPin ?: rightPin
        return DomainMergeDecision.Ready(
            CanonicalSourcePreference(
                storyId = survivorId,
                mode = if (pin == null) CanonicalSourcePreferenceMode.AUTO else CanonicalSourcePreferenceMode.PINNED,
                pinnedSource = pin,
                revision = nextRevision,
            ),
        )
    }
}
