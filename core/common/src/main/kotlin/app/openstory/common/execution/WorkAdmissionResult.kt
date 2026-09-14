package app.openstory.common.execution

enum class WorkRejectionReason {
    SATURATED,
}

sealed interface WorkAdmissionResult<out T> {
    data class Completed<T>(val value: T) : WorkAdmissionResult<T>

    data class Rejected(
        val reason: WorkRejectionReason,
    ) : WorkAdmissionResult<Nothing>
}
