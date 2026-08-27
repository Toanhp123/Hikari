package app.openstory.reader.routing

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class ReaderRemoteSourceResolverTest {
    @Test
    fun registryFailureDegradesToCachedEmptyMap() = runTest {
        var loads = 0
        val resolver = ReaderRemoteSourceResolver {
            loads += 1
            error("registry unavailable")
        }

        assertEquals(emptyMap(), resolver.resolve())
        assertEquals(emptyMap(), resolver.resolve())
        assertEquals(1, loads)
    }

    @Test
    fun cancellationStillPropagates() = runTest {
        val resolver = ReaderRemoteSourceResolver {
            throw CancellationException("cancel")
        }

        assertFailsWith<CancellationException> { resolver.resolve() }
    }
}
