package app.openstory.plugins.runtime.capabilities.http

import app.openstory.common.id.PluginId
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlinx.coroutines.test.runTest

class CompositeManagedCredentialProviderTest {
    private val request = ManagedCredentialRequest(
        PluginId("org.example.plugin"),
        "https://api.example.com/v1/story",
    )

    @Test
    fun composesProvidersInFixedOrder() = runTest {
        val calls = mutableListOf<Int>()
        val provider = CompositeManagedCredentialProvider(
            listOf(
                ManagedCredentialProvider { calls += 1; mapOf("X-Client" to "client") },
                ManagedCredentialProvider { calls += 2; mapOf("Cookie" to "session=value") },
            ),
        )

        assertEquals(
            mapOf("X-Client" to "client", "Cookie" to "session=value"),
            provider.headers(request),
        )
        assertEquals(listOf(1, 2), calls)
    }

    @Test
    fun rejectsCaseInsensitiveHeaderOwnershipCollision() = runTest {
        val provider = CompositeManagedCredentialProvider(
            listOf(
                ManagedCredentialProvider { mapOf("Authorization" to "first") },
                ManagedCredentialProvider { mapOf("authorization" to "second") },
            ),
        )

        val failure = assertFailsWith<HttpCapabilityFailure> { provider.headers(request) }
        assertEquals("plugin.http_managed_header_collision", failure.code)
    }

    @Test
    fun providerExceptionIsReplacedWithStableRedactedFailure() = runTest {
        val provider = CompositeManagedCredentialProvider(
            listOf(ManagedCredentialProvider { error("marker-secret") }),
        )

        val failure = assertFailsWith<HttpCapabilityFailure> { provider.headers(request) }
        assertEquals("plugin.http_credentials_failed", failure.code)
        assertEquals(false, failure.toString().contains("marker-secret"))
    }

    @Test
    fun emptyProviderListReturnsNoHeaders() = runTest {
        assertEquals(emptyMap(), CompositeManagedCredentialProvider(emptyList()).headers(request))
    }
}
