package app.openstory.plugins.runtime.capabilities.http

import app.openstory.common.id.PluginId
import app.openstory.plugins.runtime.PluginCallResult
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertFailsWith
import kotlinx.coroutines.test.runTest
import okhttp3.OkHttpClient
import okhttp3.ResponseBody.Companion.toResponseBody

class PluginHttpCapabilityTest {
    @Test
    fun undeclaredHostFailsClosed() {
        val result = capability().validateTarget(
            "https://denied.example/x",
            allowedHosts = setOf("allowed.example"),
        )
        assertEquals("plugin.http_domain_denied", assertIs<PluginCallResult.Failure>(result).code)
    }

    @Test
    fun managedCredentialIsSentButNeverReturnedToPlugin() = runTest {
        val capability = PluginHttpCapability(
            OkHttpClient(),
            ManagedCredentialProvider { _, _ -> mapOf("Authorization" to "secret-cookie") },
        )
        val request = capability.buildRequest(
            PluginHttpRequest("https://api.example.com/resource"),
            policy(setOf("api.example.com")),
            "api.example.com",
        )

        assertEquals("secret-cookie", request.header("Authorization"))
        assertFalse("secret-cookie" in PluginCallResult.Success(Unit).toString())
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

    private fun capability() = PluginHttpCapability(OkHttpClient())

    private fun policy(hosts: Set<String>, responseBytes: Long = 1024) = PluginRequestPolicy(
        pluginId = PluginId("org.example.plugin"),
        allowedHosts = hosts,
        maxCompressedResponseBytes = responseBytes,
        maxDecompressedResponseBytes = responseBytes,
    )
}
