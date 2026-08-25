package app.openstory.auth

import app.openstory.plugins.api.manifest.PluginAuthenticationCapability
import app.openstory.plugins.api.manifest.PluginAuthenticationCompletionTarget
import app.openstory.plugins.api.manifest.PluginAuthenticationCredentialTarget
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class PluginLoginNavigationPolicyTest {
    private val policy = PluginLoginNavigationPolicy(
        PluginAuthenticationCapability(
            loginStartUrl = "https://accounts.example.com/login",
            navigationHosts = setOf("accounts.example.com"),
            completion = PluginAuthenticationCompletionTarget("accounts.example.com", "/complete"),
            credentialTargets = listOf(
                PluginAuthenticationCredentialTarget("api.example.com", "/v1", setOf("session")),
            ),
            sessionTtlSeconds = 3600,
        ),
    )

    @Test
    fun blocksNavigationOutsideDeclaredHttpsHosts() {
        assertTrue(policy.allows("https://accounts.example.com/login"))
        assertFalse(policy.allows("https://evil.example.com/login"))
        assertFalse(policy.allows("http://accounts.example.com/login"))
    }

    @Test
    fun completesOnlyOnDeclaredHostAndPath() {
        assertTrue(policy.isCompletion("https://accounts.example.com/complete/success"))
        assertFalse(policy.isCompletion("https://accounts.example.com/login"))
        assertFalse(policy.isCompletion("https://evil.example.com/complete"))
    }
}
