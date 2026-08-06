package app.openstory.database.repository

import app.openstory.common.AppError
import app.openstory.common.AppResult
import java.util.concurrent.CancellationException

@Suppress("TooGenericExceptionCaught")
internal suspend fun <T> executeStorageWrite(
    block: suspend () -> T,
): AppResult<T> =
    try {
        AppResult.Success(
            block(),
        )
    } catch (exception: Exception) {
        if (exception is CancellationException) {
            throw exception
        }

        AppResult.Failure(
            AppError.Storage(
                code = "storage.write_failed",
                retryable = true,
            ),
        )
    }
