package app.openstory.reader.selection

class ReleaseSelector {
    fun select(
        candidates: Collection<ReleaseCandidate>,
        policy: ReleaseSelectionPolicy = ReleaseSelectionPolicy(),
    ): ReleaseSelectionResult {
        if (candidates.isEmpty()) return ReleaseSelectionResult.NoneAvailable
        val ordered = candidates.sortedWith(candidateComparator(policy))
        return ReleaseSelectionResult.Selected(
            candidate = ordered.first(),
            alternates = ordered.drop(1),
            reason = explain(ordered, policy),
        )
    }

    private fun explain(ordered: List<ReleaseCandidate>, policy: ReleaseSelectionPolicy): SelectionReason {
        val selected = ordered.first()
        val runnerUp = ordered.getOrNull(1) ?: return SelectionReason.STABLE_ID
        return when {
            matchesExplicit(selected, policy) != matchesExplicit(runnerUp, policy) -> SelectionReason.EXPLICIT_RELEASE
            matchesPreviousRelease(selected, policy) != matchesPreviousRelease(runnerUp, policy) ->
                SelectionReason.PREVIOUS_RELEASE
            matchesPreviousGroup(selected, policy) != matchesPreviousGroup(runnerUp, policy) ->
                SelectionReason.PREVIOUS_SOURCE_GROUP
            matchesPreviousSource(selected, policy) != matchesPreviousSource(runnerUp, policy) ->
                SelectionReason.PREVIOUS_SOURCE
            languageRank(selected.release.languageTag, policy.languageOrder) !=
                languageRank(runnerUp.release.languageTag, policy.languageOrder) -> SelectionReason.LANGUAGE_ORDER
            selected.health != runnerUp.health || selected.completeness != runnerUp.completeness ->
                SelectionReason.HEALTH_AND_COMPLETENESS
            selected.release.publishedAtEpochMillis != runnerUp.release.publishedAtEpochMillis ->
                SelectionReason.RECENCY
            else -> SelectionReason.STABLE_ID
        }
    }

    private fun matchesExplicit(candidate: ReleaseCandidate, policy: ReleaseSelectionPolicy) =
        candidate.release.id == policy.explicitReleaseId

    private fun matchesPreviousRelease(candidate: ReleaseCandidate, policy: ReleaseSelectionPolicy) =
        candidate.release.id == policy.previousReleaseId

    private fun matchesPreviousGroup(candidate: ReleaseCandidate, policy: ReleaseSelectionPolicy) =
        policy.previousSourceGroup != null && candidate.sourceGroup == policy.previousSourceGroup

    private fun matchesPreviousSource(candidate: ReleaseCandidate, policy: ReleaseSelectionPolicy) =
        candidate.release.pluginId == policy.previousPluginId

    private fun candidateComparator(policy: ReleaseSelectionPolicy): Comparator<ReleaseCandidate> =
        compareByDescending<ReleaseCandidate> { it.release.id == policy.explicitReleaseId }
            .thenByDescending { it.release.id == policy.previousReleaseId }
            .thenByDescending {
                policy.previousSourceGroup != null && it.sourceGroup == policy.previousSourceGroup
            }
            .thenByDescending { it.release.pluginId == policy.previousPluginId }
            .thenBy { languageRank(it.release.languageTag, policy.languageOrder) }
            .thenByDescending { it.health.rank }
            .thenByDescending { it.completeness }
            .thenByDescending { it.release.publishedAtEpochMillis ?: Long.MIN_VALUE }
            .thenBy { it.release.pluginId.value }
            .thenBy { it.release.id.value }

    private fun languageRank(languageTag: String, order: List<String>): Int =
        order.indexOf(languageTag).takeIf { it >= 0 } ?: Int.MAX_VALUE

}
