package app.openstory.reader.engine.internal

/** Stable pure ranking. Input order and object identity never participate in ties. */
internal class CandidateRanker {
    fun rank(candidates: List<EvaluatedCandidate>): List<EvaluatedCandidate> = candidates.sortedWith(
        compareByDescending<EvaluatedCandidate> { it.weightedScore.value }
            .thenBy { it.candidate.sourceId.value }
            .thenBy { it.candidate.releaseId.value },
    )
}
