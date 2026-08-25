package app.openstory.auth

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import app.openstory.common.id.PluginId
import app.openstory.plugins.api.manifest.PluginManifest
import app.openstory.plugins.runtime.PluginCallResult
import app.openstory.plugins.runtime.auth.AndroidKeystorePluginSessionStore
import app.openstory.plugins.runtime.auth.DefaultPluginSessionService
import app.openstory.plugins.runtime.auth.InstalledAuthenticationPolicy
import app.openstory.plugins.runtime.auth.InstalledAuthenticationPolicySource
import app.openstory.plugins.runtime.auth.PluginSessionManagedCredentialProvider
import app.openstory.plugins.runtime.auth.PluginSessionRecord
import app.openstory.plugins.runtime.auth.PluginSessionStatus
import app.openstory.plugins.runtime.auth.SecretCookieValue
import app.openstory.plugins.runtime.capabilities.CapabilityBroker
import app.openstory.plugins.runtime.capabilities.html.HtmlCapability
import app.openstory.plugins.runtime.capabilities.http.ManagedCredentialRequest
import app.openstory.plugins.runtime.capabilities.http.PluginHttpCapability
import app.openstory.plugins.runtime.capabilities.http.PluginRequestPolicy
import app.openstory.plugins.runtime.capabilities.log.SafePluginLogger
import app.openstory.plugins.runtime.persistence.PluginDiagnosticEvent
import app.openstory.plugins.runtime.persistence.PluginDiagnosticsSink
import app.openstory.settings.RuntimePluginSessionControlAdapter
import app.openstory.settings.session.SettingsPluginSessionStatus
import java.io.File
import java.security.KeyStore
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class PluginSessionRuntimeIntegrationTest {
    @Test
    fun verifiedLoginComposesKeystoreSettingsRuntimeCredentialsAndLogout() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val testContext = InstrumentationRegistry.getInstrumentation().context
        val manifest = testContext.assets.open("plugins/authenticated-content/manifest.json").use { input ->
            Json.decodeFromString(PluginManifest.serializer(), input.readBytes().decodeToString())
        }
        val pluginId = PluginId(manifest.id)
        val capability = requireNotNull(manifest.capabilities.authentication)
        val target = capability.credentialTargets.single()
        val policies = InstalledAuthenticationPolicySource {
            listOf(InstalledAuthenticationPolicy(pluginId, enabled = true, capability))
        }
        val store = AndroidKeystorePluginSessionStore(context)
        store.clear(pluginId)
        val service = DefaultPluginSessionService(store, policies, nowEpochMillis = { NOW })
        val settings = RuntimePluginSessionControlAdapter(context, service, policies)
        val secret = "device-session-secret"

        PluginLoginCoordinator(service).complete(
            pluginId = pluginId,
            fingerprint = capability.policyFingerprint(),
            records = listOf(
                PluginSessionRecord(
                    pluginId = pluginId,
                    targetHost = target.host,
                    targetPathPrefix = target.pathPrefix,
                    cookieName = target.cookieNames.single(),
                    cookieValue = SecretCookieValue.of(secret),
                    createdAtEpochMillis = NOW,
                    expiresAtEpochMillis = NOW + capability.sessionTtlSeconds * 1_000,
                    authenticationPolicyFingerprint = capability.policyFingerprint(),
                ),
            ),
        )

        val settingsSummary = settings.sessions.first {
            it.singleOrNull()?.status == SettingsPluginSessionStatus.AUTHENTICATED
        }.single()
        assertEquals(pluginId, settingsSummary.pluginId)
        assertEquals(PluginSessionStatus.AUTHENTICATED, service.summary(pluginId).status)
        assertEquals(
            mapOf("Cookie" to "session=$secret"),
            PluginSessionManagedCredentialProvider(service).headers(
                ManagedCredentialRequest(pluginId, "https://${target.host}${target.pathPrefix}/chapter/1"),
            ),
        )

        val sessionFile = File(context.noBackupFilesDir, "plugin-sessions/${pluginId.value}.json")
        assertTrue(sessionFile.isFile)
        assertFalse(sessionFile.readText().contains(secret))
        val keyStore = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        assertTrue(keyStore.containsAlias("openstory.plugin.sessions.v1"))

        val broker = CapabilityBroker(
            http = PluginHttpCapability(okhttp3.OkHttpClient()),
            html = HtmlCapability(),
            logger = SafePluginLogger(NoOpDiagnostics),
            sessions = service,
        )
        val requestPolicy = PluginRequestPolicy(pluginId, manifest.capabilities.network!!.hosts)
        val authenticated = broker.dispatch(
            pluginId,
            operation = null,
            method = "auth.getState",
            payload = Json.parseToJsonElement("{}"),
            requestPolicy = requestPolicy,
        ) as PluginCallResult.Success
        assertEquals("authenticated", authenticated.value.jsonObject["status"]?.jsonPrimitive?.content)

        service.logout(pluginId)

        assertFalse(sessionFile.exists())
        assertEquals(PluginSessionStatus.LOGGED_OUT, service.summary(pluginId).status)
        assertEquals(
            SettingsPluginSessionStatus.LOGGED_OUT,
            settings.sessions.first { it.singleOrNull()?.status == SettingsPluginSessionStatus.LOGGED_OUT }
                .single().status,
        )
        assertTrue(
            PluginSessionManagedCredentialProvider(service).headers(
                ManagedCredentialRequest(pluginId, "https://${target.host}${target.pathPrefix}/chapter/1"),
            ).isEmpty(),
        )
        val loggedOut = broker.dispatch(
            pluginId,
            operation = null,
            method = "auth.getState",
            payload = Json.parseToJsonElement("{}"),
            requestPolicy = requestPolicy,
        ) as PluginCallResult.Success
        assertEquals("logged_out", loggedOut.value.jsonObject["status"]?.jsonPrimitive?.content)
    }

    private object NoOpDiagnostics : PluginDiagnosticsSink {
        override suspend fun record(event: PluginDiagnosticEvent) = Unit
        override suspend fun recent(pluginId: PluginId, limit: Int): List<PluginDiagnosticEvent> = emptyList()
    }

    private companion object {
        const val NOW = 1_000L
    }
}
