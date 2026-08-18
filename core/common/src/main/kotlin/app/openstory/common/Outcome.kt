package app.openstory.common

sealed interface Outcome<out T, out E> {
    data class Success<T>(val value: T) : Outcome<T, Nothing>
    data class Failure<E>(val error: E) : Outcome<Nothing, E>

    fun <R> fold(
        onSuccess: (T) -> R,
        onFailure: (E) -> R,
    ): R = when (this) {
        is Success -> onSuccess(value)
        is Failure -> onFailure(error)
    }

    fun <R> map(transform: (T) -> R): Outcome<R, E> = when (this) {
        is Success -> Success(transform(value))
        is Failure -> this
    }

    fun <R> flatMap(
        transform: (T) -> Outcome<R, @UnsafeVariance E>,
    ): Outcome<R, E> = when (this) {
        is Success -> transform(value)
        is Failure -> this
    }

    fun <F> mapError(transform: (E) -> F): Outcome<T, F> = when (this) {
        is Success -> this
        is Failure -> Failure(transform(error))
    }

    fun getOrNull(): T? = when (this) {
        is Success -> value
        is Failure -> null
    }
}
