package app.openstory.catalog.domain.failure

import java.util.concurrent.CancellationException
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertSame

class CatalogFailureTest {
    @Test
    fun failureExceptionCarriesTypedFailureAndOptionalCauseOnly() {
        val failure = CatalogFailure.Validation(
            field = "title",
            reason = CatalogValidationReason.OVER_LIMIT,
        )
        val cause = IllegalStateException("framework detail")
        val exception = CatalogFailureException(failure, cause)

        assertEquals(failure, exception.failure)
        assertSame(cause, exception.cause)
        assertEquals(failure.toString(), exception.message)
    }

    @Test
    fun cancellationIsRethrownUnchangedInsteadOfWrappedAsCatalogFailure() {
        val cancellation = CancellationException("caller stopped")
        val thrown = assertFailsWith<CancellationException> {
            CatalogFailureException(CatalogFailure.Acquisition(CatalogOperation.DISCOVER), cancellation)
        }

        assertSame(cancellation, thrown)
    }
}
