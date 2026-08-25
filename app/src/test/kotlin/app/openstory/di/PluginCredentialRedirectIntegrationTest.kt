package app.openstory.di

import app.openstory.common.id.PluginId
import app.openstory.plugins.api.manifest.PluginAuthenticationCapability
import app.openstory.plugins.api.manifest.PluginAuthenticationCompletionTarget
import app.openstory.plugins.api.manifest.PluginAuthenticationCredentialTarget
import app.openstory.plugins.api.manifest.PluginCapabilities
import app.openstory.plugins.api.manifest.PluginManifest
import app.openstory.plugins.api.manifest.PluginProtocolVersion
import app.openstory.plugins.api.manifest.PluginService
import app.openstory.plugins.runtime.PluginCallResult
import app.openstory.plugins.runtime.auth.DefaultPluginSessionService
import app.openstory.plugins.runtime.auth.InstalledAuthenticationPolicySource
import app.openstory.plugins.runtime.auth.InstalledPackageAuthenticationPolicySource
import app.openstory.plugins.runtime.auth.PluginSessionManagedCredentialProvider
import app.openstory.plugins.runtime.auth.PluginSessionRecord
import app.openstory.plugins.runtime.auth.PluginSessionStatus
import app.openstory.plugins.runtime.auth.PluginSessionStore
import app.openstory.plugins.runtime.auth.SecretCookieValue
import app.openstory.plugins.runtime.capabilities.http.ManagedCredentialRequest
import app.openstory.plugins.runtime.capabilities.http.PluginHttpCapability
import app.openstory.plugins.runtime.capabilities.http.PluginHttpRequest
import app.openstory.plugins.runtime.capabilities.http.PluginRequestPolicy
import app.openstory.plugins.runtime.install.PluginPackageStorage
import app.openstory.plugins.runtime.install.VerifiedPluginPackage
import app.openstory.plugins.runtime.persistence.PluginStateStore
import app.openstory.plugins.runtime.persistence.StoredPluginState
import app.openstory.plugins.runtime.persistence.StoredPluginVersion
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import okhttp3.Interceptor
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody

class PluginCredentialRedirectIntegrationTest {
    @Test
    fun redirectChainReevaluatesCredentialsForEveryValidatedTarget() = runBlocking {
        val installed = InstalledPackageFixture()
        val service = service(installed.policySource())
        service.completeVerifiedLogin(PLUGIN_ID, CAPABILITY.policyFingerprint(), listOf(record(CAPABILITY)))
        val credentials = PluginSessionManagedCredentialProvider(service)
        val sameHostHeaders = executeRedirect(
            credentials = credentials,
            redirectLocation = "https://api.example.com/profile",
        )
        val crossHostHeaders = executeRedirect(
            credentials = credentials,
            redirectLocation = "https://redirect.example.com/content/chapter/1",
        )

        assertEquals(listOf("session=secret", null), sameHostHeaders)
        assertEquals(listOf("session=secret", null), crossHostHeaders)
    }

    @Test
    fun disablementAndChangedPolicyStopDeliveryImmediatelyAndInvalidateSummary() = runBlocking {
        val installed = InstalledPackageFixture()
        val store = MemoryPluginSessionStore()
        val service = service(installed.policySource(), store)
        service.completeVerifiedLogin(PLUGIN_ID, CAPABILITY.policyFingerprint(), listOf(record(CAPABILITY)))
        val credentials = PluginSessionManagedCredentialProvider(service)

        installed.enabled = false
        assertTrue(credentials.headers(request("https://api.example.com/content/chapter/1")).isEmpty())
        assertEquals(PluginSessionStatus.EXPIRED, service.summary(PLUGIN_ID).status)

        val changed = CAPABILITY.copy(
            credentialTargets = listOf(
                PluginAuthenticationCredentialTarget("api.example.com", "/v2", setOf("session")),
            ),
        )
        installed.enabled = true
        installed.capability = changed
        assertTrue(credentials.headers(request("https://api.example.com/content/chapter/1")).isEmpty())
        service.invalidateChangedPolicies()
        assertTrue(store.readAll(PLUGIN_ID).isEmpty())
        assertEquals(PluginSessionStatus.LOGGED_OUT, service.summary(PLUGIN_ID).status)
    }

    private fun service(
        policies: InstalledAuthenticationPolicySource,
        store: PluginSessionStore = MemoryPluginSessionStore(),
    ) = DefaultPluginSessionService(store, policies, nowEpochMillis = { NOW })

    private fun request(url: String) = ManagedCredentialRequest(PLUGIN_ID, url)

    private suspend fun executeRedirect(
        credentials: PluginSessionManagedCredentialProvider,
        redirectLocation: String,
    ): List<String?> {
        val observedCookies = mutableListOf<String?>()
        val client = OkHttpClient.Builder()
            .addInterceptor(
                Interceptor { chain ->
                    val request = chain.request()
                    observedCookies += request.header("Cookie")
                    Response.Builder()
                        .request(request)
                        .protocol(Protocol.HTTP_1_1)
                        .code(if (observedCookies.size == 1) 302 else 200)
                        .message(if (observedCookies.size == 1) "Found" else "OK")
                        .apply {
                            if (observedCookies.size == 1) header("Location", redirectLocation)
                        }
                        .body("".toResponseBody())
                        .build()
                },
            )
            .build()
        val result = PluginHttpCapability(client, credentials).execute(
            PluginHttpRequest("https://api.example.com/content/start"),
            PluginRequestPolicy(
                pluginId = PLUGIN_ID,
                allowedHosts = setOf("api.example.com", "redirect.example.com"),
            ),
        )

        assertTrue(result is app.openstory.plugins.runtime.PluginCallResult.Success)
        return observedCookies
    }

    private fun record(capability: PluginAuthenticationCapability) = PluginSessionRecord(
        pluginId = PLUGIN_ID,
        targetHost = "api.example.com",
        targetPathPrefix = "/content",
        cookieName = "session",
        cookieValue = SecretCookieValue.of("secret"),
        createdAtEpochMillis = NOW,
        expiresAtEpochMillis = NOW + 60_000,
        authenticationPolicyFingerprint = capability.policyFingerprint(),
    )

    private class InstalledPackageFixture : PluginStateStore, PluginPackageStorage {
        var enabled: Boolean = true
        var capability: PluginAuthenticationCapability = CAPABILITY

        fun policySource(): InstalledAuthenticationPolicySource =
            InstalledPackageAuthenticationPolicySource(this, this, Json)

        override suspend fun find(pluginId: PluginId): StoredPluginState? =
            all().singleOrNull { it.pluginId == pluginId }

        override suspend fun all(): List<StoredPluginState> = listOf(
            StoredPluginState(
                pluginId = PLUGIN_ID,
                services = setOf(PluginService.CONTENT),
                enabled = enabled,
                activeVersion = VERSION,
                previousVersion = null,
                acceptedNetworkHosts = NETWORK_HOSTS,
            ),
        )

        override suspend fun replace(state: StoredPluginState) = Unit

        override suspend fun store(value: VerifiedPluginPackage): PluginCallResult<StoredPluginVersion> =
            PluginCallResult.Success(VERSION)

        override suspend fun readEntry(
            pluginId: PluginId,
            version: String,
            entry: String,
        ): PluginCallResult<ByteArray> = PluginCallResult.Success(
            Json.encodeToString(
                PluginManifest.serializer(),
                PluginManifest(
                    id = PLUGIN_ID.value,
                    name = "Authenticated Content Fixture",
                    version = VERSION.version,
                    protocol = PluginProtocolVersion(1),
                    provides = setOf(PluginService.CONTENT),
                    capabilities = PluginCapabilities(
                        network = app.openstory.plugins.api.manifest.NetworkCapability(NETWORK_HOSTS),
                        authentication = capability,
                    ),
                ),
            ).encodeToByteArray(),
        )

        override suspend fun remove(location: String) = Unit
    }

    private class MemoryPluginSessionStore : PluginSessionStore {
        private val records = mutableListOf<PluginSessionRecord>()

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

    private companion object {
        val PLUGIN_ID = PluginId("org.example.authenticatedcontent")
        const val NOW = 1_000L
        val NETWORK_HOSTS = setOf("accounts.example.com", "api.example.com", "redirect.example.com")
        val VERSION = StoredPluginVersion(
            version = "1.0.0",
            packageLocation = "fixture/authenticated-content.osp",
            sha256 = "a".repeat(64),
            signerFingerprint = null,
        )
        val CAPABILITY = PluginAuthenticationCapability(
            loginStartUrl = "https://accounts.example.com/login",
            navigationHosts = setOf("accounts.example.com"),
            completion = PluginAuthenticationCompletionTarget("accounts.example.com", "/complete"),
            credentialTargets = listOf(
                PluginAuthenticationCredentialTarget("api.example.com", "/content", setOf("session")),
            ),
            sessionTtlSeconds = 3_600,
        )
    }
}
