package app.openstory.plugins.runtime.capabilities.http

import app.openstory.common.id.PluginId
import app.openstory.plugins.runtime.PluginCallResult
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlinx.coroutines.test.runTest
import okhttp3.OkHttpClient
import okhttp3.ResponseBody.Companion.toResponseBody

class PluginHttpCapabilityTest {
    @Test
    fun undeclaredHostFailsClosedBeforeTransport() = runTest {
        val result = capability().execute(
            PluginHttpRequest("https://denied.example/x"),
            policy(setOf("allowed.example")),
        )
        assertEquals("plugin.http_domain_denied", assertIs<PluginCallResult.Failure>(result).code)
    }

    @Test
    fun managedCredentialIsSentButNeverReturnedToPlugin() = runTest {
        val capability = PluginHttpCapability(
            OkHttpClient(),
            ManagedCredentialProvider { request ->
                assertEquals(
                    ManagedCredentialRequest(
                        PluginId("org.example.plugin"),
                        "https://api.example.com/resource",
                    ),
                    request,
                )
                mapOf("Authorization" to "secret-cookie")
            },
        )
        val request = capability.buildRequest(
            PluginHttpRequest("https://api.example.com/resource"),
            policy(setOf("api.example.com")),
        )

        assertEquals("secret-cookie", request.header("Authorization"))
        assertFalse("secret-cookie" in PluginCallResult.Success(Unit).toString())
    }

    @Test
    fun deniedTargetNeverReachesManagedCredentials() = runTest {
        var credentialCalls = 0
        val capability = PluginHttpCapability(
            OkHttpClient(),
            ManagedCredentialProvider {
                credentialCalls += 1
                emptyMap()
            },
        )

        val failure = assertFailsWith<HttpCapabilityFailure> {
            capability.buildRequest(
                PluginHttpRequest("https://denied.example/resource"),
                policy(setOf("api.example.com")),
            )
        }

        assertEquals("plugin.http_domain_denied", failure.code)
        assertEquals(0, credentialCalls)
    }

    @Test
    fun eachAllowedTargetIsNormalizedBeforeCredentialLookup() = runTest {
        val targets = mutableListOf<String>()
        val capability = PluginHttpCapability(
            OkHttpClient(),
            ManagedCredentialProvider { request -> targets += request.url; emptyMap() },
        )
        val policy = policy(setOf("api.example.com", "cdn.example.com"))

        capability.buildRequest(PluginHttpRequest("https://API.example.com/v1/../v2?id=1"), policy)
        capability.buildRequest(PluginHttpRequest("https://cdn.example.com/assets/cover"), policy)

        assertEquals(
            listOf(
                "https://api.example.com/v2?id=1",
                "https://cdn.example.com/assets/cover",
            ),
            targets,
        )
    }

    @Test
    fun responseBodyOverBudgetFailsBeforeDecode() {
        val body = "x".repeat(1024).toResponseBody()
        assertFailsWith<ResponseBudgetExceeded> {
            BoundedResponseReader.read(body, 32)
        }
    }

    @Test
    fun compressedResponseStreamIsBoundedBeforeTransparentDecode() {
        val body = CompressedLimitResponseBody("x".repeat(1024).toResponseBody(), 32)
        assertFailsWith<CompressedResponseBudgetExceeded> {
            body.bytes()
        }
    }

    private fun capability() = PluginHttpCapability(
        OkHttpClient.Builder()
            .addInterceptor { error("Denied host must fail before HTTP transport") }
            .build(),
    )

    private fun policy(hosts: Set<String>, responseBytes: Long = 1024) = PluginRequestPolicy(
        pluginId = PluginId("org.example.plugin"),
        allowedHosts = hosts,
        maxCompressedResponseBytes = responseBytes,
        maxDecompressedResponseBytes = responseBytes,
    )
}
