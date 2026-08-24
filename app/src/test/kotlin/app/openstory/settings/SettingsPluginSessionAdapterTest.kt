package app.openstory.settings

import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import app.openstory.auth.PluginLoginActivity
import app.openstory.common.id.PluginId
import app.openstory.plugins.api.manifest.PluginAuthenticationCapability
import app.openstory.plugins.api.manifest.PluginAuthenticationCompletionTarget
import app.openstory.plugins.api.manifest.PluginAuthenticationCredentialTarget
import app.openstory.plugins.runtime.auth.InstalledAuthenticationPolicy
import app.openstory.plugins.runtime.auth.InstalledAuthenticationPolicySource
import app.openstory.plugins.runtime.auth.PluginSessionRecord
import app.openstory.plugins.runtime.auth.PluginSessionService
import app.openstory.plugins.runtime.auth.PluginSessionStatus
import app.openstory.plugins.runtime.auth.PluginSessionSummary
import app.openstory.plugins.runtime.capabilities.http.ManagedCredentialRequest
import app.openstory.settings.ui.SettingsPluginSessionStatus
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class SettingsPluginSessionAdapterTest {
    @Test
    fun summariesIncludeOnlyEnabledInstalledAuthenticationPolicies() = runTest {
        val authenticated = PluginId("authenticated")
        val expired = PluginId("expired")
        val disabled = PluginId("disabled")
        val sessions = FakePluginSessionService(
            listOf(
                PluginSessionSummary(authenticated, PluginSessionStatus.AUTHENTICATED, 2_000),
                PluginSessionSummary(expired, PluginSessionStatus.EXPIRED, 1_000),
                PluginSessionSummary(disabled, PluginSessionStatus.AUTHENTICATED, 3_000),
            ),
        )
        val adapter = SettingsPluginSessionAdapter(
            RecordingContext(RuntimeEnvironment.getApplication()),
            sessions,
            FakeInstalledAuthenticationPolicySource(
                listOf(policy(authenticated), policy(expired), policy(disabled, enabled = false)),
            ),
        )

        val summaries = adapter.observeInstalledSessions().first()

        assertEquals(listOf("authenticated", "expired"), summaries.map { it.pluginId })
        assertEquals(
            listOf(SettingsPluginSessionStatus.AUTHENTICATED, SettingsPluginSessionStatus.EXPIRED),
            summaries.map { it.status },
        )
        assertEquals(listOf(2_000L, 1_000L), summaries.map { it.expiresAtEpochMillis })
    }

    @Test
    fun loginStartsTheGuardedActivityOnlyForAnEnabledPolicy() = runTest {
        val context = RecordingContext(RuntimeEnvironment.getApplication())
        val enabled = PluginId("enabled")
        val adapter = SettingsPluginSessionAdapter(
            context,
            FakePluginSessionService(),
            FakeInstalledAuthenticationPolicySource(listOf(policy(enabled))),
        )

        assertTrue(adapter.launchLogin(enabled.value))
        assertEquals(enabled.value, context.startedIntent?.getStringExtra(PluginLoginActivity.EXTRA_PLUGIN_ID))
        assertTrue(context.startedIntent?.flags?.and(Intent.FLAG_ACTIVITY_NEW_TASK) != 0)

        context.startedIntent = null
        assertFalse(adapter.launchLogin("not-installed"))
        assertEquals(null, context.startedIntent)
    }

    @Test
    fun loginPolicyCancellationIsRethrown() = runTest {
        val adapter = SettingsPluginSessionAdapter(
            RecordingContext(RuntimeEnvironment.getApplication()),
            FakePluginSessionService(),
            InstalledAuthenticationPolicySource { throw CancellationException("cancelled") },
        )

        assertFailsWith<CancellationException> { adapter.launchLogin("enabled") }
    }

    @Test
    fun logoutDelegatesTheValidatedPluginIdToTheRuntime() = runTest {
        val sessions = FakePluginSessionService()
        val adapter = SettingsPluginSessionAdapter(
            RecordingContext(RuntimeEnvironment.getApplication()),
            sessions,
            FakeInstalledAuthenticationPolicySource(emptyList()),
        )

        adapter.logout("secured-plugin")

        assertEquals(PluginId("secured-plugin"), sessions.loggedOutPluginId)
    }
}

private class RecordingContext(base: Context) : ContextWrapper(base) {
    var startedIntent: Intent? = null

    override fun startActivity(intent: Intent) {
        startedIntent = intent
    }
}

private class FakeInstalledAuthenticationPolicySource(
    private val policies: List<InstalledAuthenticationPolicy>,
) : InstalledAuthenticationPolicySource {
    override suspend fun installedAuthenticationPolicies(): List<InstalledAuthenticationPolicy> = policies
}

private class FakePluginSessionService(
    initial: List<PluginSessionSummary> = emptyList(),
) : PluginSessionService {
    private val summaries = MutableStateFlow(initial)
    var loggedOutPluginId: PluginId? = null

    override fun observeInstalledSessions(): Flow<List<PluginSessionSummary>> = summaries

    override suspend fun logout(pluginId: PluginId) {
        loggedOutPluginId = pluginId
    }

    override suspend fun completeVerifiedLogin(
        pluginId: PluginId,
        authenticationPolicyFingerprint: String,
        records: List<PluginSessionRecord>,
    ): PluginSessionSummary = error("Not used")

    override suspend fun sessionFor(request: ManagedCredentialRequest): List<PluginSessionRecord> = error("Not used")
    override suspend fun summary(pluginId: PluginId): PluginSessionSummary = error("Not used")
    override suspend fun invalidateChangedPolicies() = Unit
}

private fun policy(
    pluginId: PluginId,
    enabled: Boolean = true,
): InstalledAuthenticationPolicy = InstalledAuthenticationPolicy(
    pluginId = pluginId,
    enabled = enabled,
    capability = PluginAuthenticationCapability(
        loginStartUrl = "https://auth.example.com/login",
        navigationHosts = setOf("auth.example.com"),
        completion = PluginAuthenticationCompletionTarget("auth.example.com", "/complete"),
        credentialTargets = listOf(
            PluginAuthenticationCredentialTarget("auth.example.com", "/api", setOf("session")),
        ),
        sessionTtlSeconds = 3_600,
    ),
)
