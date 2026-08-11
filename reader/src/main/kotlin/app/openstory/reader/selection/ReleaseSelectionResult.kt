package app.openstory.reader.selection

import app.openstory.chapters.model.ChapterRelease

data class ReleaseCandidate(
    val release: ChapterRelease,
    val sourceGroup: String? = null,
    val health: ReleaseHealth = ReleaseHealth.HEALTHY,
    val completeness: Int = 100,
) {
    init {
        require(completeness in MIN_COMPLETENESS..MAX_COMPLETENESS) {
            "Completeness must be between $MIN_COMPLETENESS and $MAX_COMPLETENESS"
        }
    }

    private companion object {
        const val MIN_COMPLETENESS = 0
        const val MAX_COMPLETENESS = 100
    }
}

enum class SelectionReason {
    EXPLICIT_RELEASE,
    PREVIOUS_RELEASE,
    PREVIOUS_SOURCE_GROUP,
    PREVIOUS_SOURCE,
    LANGUAGE_ORDER,
    HEALTH_AND_COMPLETENESS,
    RECENCY,
    STABLE_ID,
}

sealed interface ReleaseSelectionResult {
    data object NoneAvailable : ReleaseSelectionResult

    data class Selected(
        val candidate: ReleaseCandidate,
        val alternates: List<ReleaseCandidate>,
        val reason: SelectionReason,
    ) : ReleaseSelectionResult
}
