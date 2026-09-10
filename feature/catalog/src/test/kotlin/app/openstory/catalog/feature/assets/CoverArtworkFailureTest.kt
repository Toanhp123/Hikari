package app.openstory.catalog.feature.assets

import app.openstory.catalog.domain.failure.CatalogArtworkFailureReason
import app.openstory.catalog.domain.failure.CatalogFailure
import app.openstory.catalog.domain.failure.CatalogFailureException
import java.io.IOException
import org.junit.Assert.assertEquals
import org.junit.Test

class CoverArtworkFailureTest {
    @Test
    fun typedArtworkFailureIsPreservedAndGenericDecodeFailureIsScoped() {
        val typed = CatalogFailure.Artwork(CatalogArtworkFailureReason.INVALID_LOCATOR)

        assertEquals(typed, CatalogFailureException(typed).toCoverArtworkFailure())
        assertEquals(
            CatalogFailure.Artwork(CatalogArtworkFailureReason.DECODE_FAILED),
            IOException("decoder detail must not escape").toCoverArtworkFailure(),
        )
    }
}
