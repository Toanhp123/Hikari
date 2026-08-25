package app.openstory.plugins.runtime.auth

import app.openstory.common.id.PluginId
import app.openstory.plugins.api.manifest.PluginAuthenticationCapability
import app.openstory.plugins.api.manifest.PluginAuthenticationCompletionTarget
import app.openstory.plugins.api.manifest.PluginAuthenticationCredentialTarget
import app.openstory.plugins.runtime.capabilities.http.ManagedCredentialRequest
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class PluginSessionManagedCredentialProviderTest {
    private val pluginId = PluginId("org.example.plugin")
    private val capability = PluginAuthenticationCapability(
        loginStartUrl = "https://accounts.example.com/login",
        navigationHosts = setOf("accounts.example.com"),
        completion = PluginAuthenticationCompletionTarget("accounts.example.com", "/complete"),
        credentialTargets = listOf(
            PluginAuthenticationCredentialTarget("api.example.com", "/v1", setOf("session")),
        ),
        sessionTtlSeconds = 3600,
    )

    @Test
    fun emitsCookieOnlyForMatchingHostPathAndPolicy() = runTest {
        val store = MemorySessionStore()
        val service = DefaultPluginSessionService(
            store,
            InstalledAuthenticationPolicySource {
                listOf(InstalledAuthenticationPolicy(pluginId, true, capability))
            },
            nowEpochMillis = { 1000 },
        )
        service.completeVerifiedLogin(
            pluginId,
            capability.policyFingerprint(),
            listOf(record(expiresAt = 2000)),
        )
        val provider = PluginSessionManagedCredentialProvider(service)

        assertEquals(
            mapOf("Cookie" to "session=secret"),
            provider.headers(ManagedCredentialRequest(pluginId, "https://api.example.com/v1/books")),
        )
        assertTrue(provider.headers(ManagedCredentialRequest(pluginId, "https://api.example.com/v2/books")).isEmpty())
    }

    @Test
    fun deniesExpiredAndDisabledSessions() = runTest {
        val store = MemorySessionStore(mutableListOf(record(expiresAt = 999)))
        val service = DefaultPluginSessionService(
            store,
            InstalledAuthenticationPolicySource {
                listOf(InstalledAuthenticationPolicy(pluginId, false, capability))
            },
            nowEpochMillis = { 1000 },
        )

        assertTrue(
            PluginSessionManagedCredentialProvider(service)
                .headers(ManagedCredentialRequest(pluginId, "https://api.example.com/v1/books"))
                .isEmpty(),
        )
    }

    @Test
    fun pluginWithoutAuthenticationPolicyEmitsNoCredentials() = runTest {
        val service = DefaultPluginSessionService(
            MemorySessionStore(),
            InstalledAuthenticationPolicySource { emptyList() },
            nowEpochMillis = { 1000 },
        )

        assertTrue(
            PluginSessionManagedCredentialProvider(service)
                .headers(ManagedCredentialRequest(pluginId, "https://api.example.com/v1/books"))
                .isEmpty(),
        )
    }

    private fun record(expiresAt: Long) = PluginSessionRecord(
        pluginId = pluginId,
        targetHost = "api.example.com",
        targetPathPrefix = "/v1",
        cookieName = "session",
        cookieValue = SecretCookieValue.of("secret"),
        createdAtEpochMillis = 0,
        expiresAtEpochMillis = expiresAt,
        authenticationPolicyFingerprint = capability.policyFingerprint(),
    )
}

private class MemorySessionStore(
    private val records: MutableList<PluginSessionRecord> = mutableListOf(),
) : PluginSessionStore {
    override suspend fun readAll(pluginId: PluginId): List<PluginSessionRecord> =
        records.filter { it.pluginId == pluginId }

    override suspend fun replaceAll(pluginId: PluginId, records: List<PluginSessionRecord>) {
        clear(pluginId)
        this.records += records
    }

    override suspend fun clear(pluginId: PluginId) {
        records.removeAll { it.pluginId == pluginId }
    }
}
