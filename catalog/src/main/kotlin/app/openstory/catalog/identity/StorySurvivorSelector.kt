package app.openstory.catalog.identity

class StorySurvivorSelector {
    fun select(
        left: StoryMergeCandidate,
        right: StoryMergeCandidate,
    ): StoryMergeSelection {
        require(left.storyId != right.storyId)
        val survivor = if (compare(left, right) <= 0) left else right
        val retired = if (survivor == left) right else left
        return StoryMergeSelection(survivor, retired)
    }

    private fun compare(
        left: StoryMergeCandidate,
        right: StoryMergeCandidate,
    ): Int {
        val domainOrder = compareDescending(
            left.footprint.meaningfulDomainCount,
            right.footprint.meaningfulDomainCount,
        )
        val stateOrder = compareDescending(
            left.footprint.meaningfulStateTotal,
            right.footprint.meaningfulStateTotal,
        )
        val ageOrder = if (left.createdAtEpochMillis != null && right.createdAtEpochMillis != null) {
            left.createdAtEpochMillis.compareTo(right.createdAtEpochMillis)
        } else {
            0
        }
        return when {
            domainOrder != 0 -> domainOrder
            stateOrder != 0 -> stateOrder
            ageOrder != 0 -> ageOrder
            else -> left.storyId.value.compareTo(right.storyId.value)
        }
    }

    private fun compareDescending(left: Int, right: Int): Int = right.compareTo(left)
}
