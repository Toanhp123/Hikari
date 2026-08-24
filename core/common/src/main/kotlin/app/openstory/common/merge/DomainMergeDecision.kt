package app.openstory.common.merge

sealed interface DomainMergeDecision<out T> {
    data class Ready<T>(val value: T) : DomainMergeDecision<T>

    data class RequiresReview(val reasons: Set<String>) : DomainMergeDecision<Nothing> {
        init {
            require(reasons.isNotEmpty())
            require(reasons.none(String::isBlank))
        }
    }
}
