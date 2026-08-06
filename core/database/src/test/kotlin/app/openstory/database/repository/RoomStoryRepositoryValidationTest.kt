package app.openstory.database.repository

import app.openstory.common.AppError
import app.openstory.common.AppResult
import java.util.concurrent.CancellationException
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class RoomStoryRepositoryValidationTest {
    @Test
    fun cancellationIsNotConvertedIntoStorageFailure() = runTest {
        assertFailsWith<CancellationException> {
            executeStorageWrite {
                throw CancellationException("cancel")
            }
        }
    }

    @Test
    fun ordinaryWriteFailureRemainsTypedStorageFailure() = runTest {
        val result = executeStorageWrite<Unit> {
            error("boom")
        }

        assertEquals(
            AppResult.Failure(
                AppError.Storage(
                    code = "storage.write_failed",
                    retryable = true,
                ),
            ),
            result,
        )
    }
}
