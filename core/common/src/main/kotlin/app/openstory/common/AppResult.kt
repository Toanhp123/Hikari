package app.openstory.common

sealed interface AppResult<out T> {
    data class Success<T>(
        val value: T,
    ) : AppResult<T>

    data class Failure(
        val error: AppError,
    ) : AppResult<Nothing>

    fun <R> map(transform: (T) -> R): AppResult<R> = when (this) {
        is Success -> Success(transform(value))
        is Failure -> this
    }

    fun <R> flatMap(
        transform: (T) -> AppResult<R>,
    ): AppResult<R> = when (this) {
        is Success -> transform(value)
        is Failure -> this
    }

    fun getOrNull(): T? = when (this) {
        is Success -> value
        is Failure -> null
    }

    suspend fun tapSuccess(
        block: suspend (T) -> Unit,
    ): AppResult<T> = apply {
        if (this is Success) {
            block(value)
        }
    }

    suspend fun tapFailure(
        block: suspend (AppError) -> Unit,
    ): AppResult<T> = apply {
        if (this is Failure) {
            block(error)
        }
    }
}
