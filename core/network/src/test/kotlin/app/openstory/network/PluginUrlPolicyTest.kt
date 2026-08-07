package app.openstory.network

import app.openstory.common.AppResult
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

class PluginUrlPolicyTest {
    @Test
    fun relativeUrlResolvesAgainstAllowedHttpsBase() {
        val result = PluginUrlPolicy(
            allowedHosts = setOf("example.com"),
            baseUrl = "https://example.com/catalog/",
        ).resolve("../story/1")

        val validated = assertIs<AppResult.Success<ValidatedPluginUrl>>(result).value
        assertEquals("https://example.com/story/1", validated.value)
        assertEquals("example.com", validated.host)
    }

    @Test
    fun absoluteAllowedHttpsUrlSucceeds() {
        val result = PluginUrlPolicy(setOf("example.com"))
            .resolve("https://EXAMPLE.com/story")

        assertEquals("https://example.com/story", assertIs<AppResult.Success<ValidatedPluginUrl>>(result).value.value)
    }

    @Test
    fun cleartextUrlIsRejected() {
        assertEquals(
            "plugin.https_required",
            PluginUrlPolicy(setOf("example.com")).resolve("http://example.com").errorCode(),
        )
    }

    @Test
    fun undeclaredHostIsRejected() {
        assertEquals(
            "plugin.domain_denied",
            PluginUrlPolicy(setOf("example.com")).resolve("https://other.example").errorCode(),
        )
    }

    @Test
    fun userInfoUrlIsInvalid() {
        assertEquals(
            "plugin.invalid_url",
            PluginUrlPolicy(setOf("example.com")).resolve("https://user:secret@example.com").errorCode(),
        )
    }
}

private fun AppResult<*>.errorCode(): String? =
    when (this) {
        is AppResult.Failure -> error.code
        is AppResult.Success -> null
    }
