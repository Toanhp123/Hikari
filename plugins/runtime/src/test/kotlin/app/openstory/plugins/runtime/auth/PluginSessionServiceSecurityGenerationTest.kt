package app.openstory.plugins.runtime.auth

import app.openstory.common.id.PluginId
import app.openstory.plugins.api.manifest.PluginAuthenticationCapability
import app.openstory.plugins.api.manifest.PluginAuthenticationCompletionTarget
import app.openstory.plugins.api.manifest.PluginAuthenticationCredentialTarget
import app.openstory.plugins.runtime.capabilities.http.ManagedCredentialRequest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest

class PluginSessionServiceSecurityGenerationTest {
    private val pluginId = PluginId("org.example.secure")
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
    fun `replacement login advances generation even when visible session facts stay equal`() = runTest {
        val store = SecurityGenerationStore()
        val service = service(store)

        val first = service.completeVerifiedLogin(pluginId, capability.policyFingerprint(), listOf(record("first")))
        val replacement = service.completeVerifiedLogin(
            pluginId,
            capability.policyFingerprint(),
            listOf(record("replacement")),
        )

        assertEquals(PluginSessionStatus.AUTHENTICATED, first.status)
        assertEquals(first.status, replacement.status)
        assertEquals(first.expiresAtEpochMillis, replacement.expiresAtEpochMillis)
        assertEquals(1L, first.credentialGeneration)
        assertEquals(2L, replacement.credentialGeneration)
        assertEquals(2L, service.observeInstalledSessions().first().single().credentialGeneration)
    }

    @Test
    fun `successful login publishes generation without rereading credential store`() = runTest {
        val store = SecurityGenerationStore().apply {
            failEveryRead = true
        }
        val service = service(store)

        val summary = service.completeVerifiedLogin(
            pluginId,
            capability.policyFingerprint(),
            listOf(record("first")),
        )

        assertEquals(PluginSessionStatus.AUTHENTICATED, summary.status)
        assertEquals(1L, summary.credentialGeneration)
        assertEquals(0, store.readCalls)
        assertEquals(1L, service.observeInstalledSessions().first().single().credentialGeneration)
    }

    @Test
    fun `logout advances generation after credential authority is cleared`() = runTest {
        val store = SecurityGenerationStore()
        val service = service(store)
        service.completeVerifiedLogin(pluginId, capability.policyFingerprint(), listOf(record("first")))

        service.logout(pluginId)

        val summary = service.observeInstalledSessions().first().single()
        assertEquals(PluginSessionStatus.LOGGED_OUT, summary.status)
        assertEquals(2L, summary.credentialGeneration)
        assertEquals(1, store.clearCalls)
    }

    @Test
    fun `policy mismatch clear advances generation exactly once`() = runTest {
        val oldCapability = capability.copy(loginStartUrl = "https://accounts.example.com/old")
        val store = SecurityGenerationStore(
            mutableListOf(record("old", fingerprint = oldCapability.policyFingerprint())),
        )
        val service = service(store)

        service.invalidateChangedPolicies()

        val summary = service.observeInstalledSessions().first().single()
        assertEquals(PluginSessionStatus.LOGGED_OUT, summary.status)
        assertEquals(1L, summary.credentialGeneration)
        assertEquals(1, store.clearCalls)
    }

    @Test
    fun `policy mismatch clear publishes generation without post clear reread`() = runTest {
        val oldCapability = capability.copy(loginStartUrl = "https://accounts.example.com/old")
        val store = SecurityGenerationStore(
            mutableListOf(record("old", fingerprint = oldCapability.policyFingerprint())),
        ).apply {
            failReadsAfter = 1
        }
        val service = service(store)

        service.invalidateChangedPolicies()

        val summary = service.observeInstalledSessions().first().single()
        assertEquals(PluginSessionStatus.LOGGED_OUT, summary.status)
        assertEquals(1L, summary.credentialGeneration)
        assertEquals(1, store.readCalls)
        assertEquals(1, store.clearCalls)
    }

    @Test
    fun `credential store read failure clear advances generation`() = runTest {
        val store = SecurityGenerationStore(mutableListOf(record("first"))).apply {
            failNextRead = true
        }
        val service = service(store)

        val result = service.sessionFor(
            ManagedCredentialRequest(pluginId, "https://api.example.com/v1/books"),
        )

        assertEquals(emptyList(), result)
        val summary = service.observeInstalledSessions().first().single()
        assertEquals(PluginSessionStatus.LOGGED_OUT, summary.status)
        assertEquals(1L, summary.credentialGeneration)
        assertEquals(1, store.clearCalls)
    }

    @Test
    fun `persistent credential store read failure publishes cleared generation without reread`() = runTest {
        val store = SecurityGenerationStore(mutableListOf(record("first"))).apply {
            failEveryRead = true
        }
        val service = service(store)

        val result = service.sessionFor(
            ManagedCredentialRequest(pluginId, "https://api.example.com/v1/books"),
        )

        assertEquals(emptyList(), result)
        val summary = service.observeInstalledSessions().first().single()
        assertEquals(PluginSessionStatus.LOGGED_OUT, summary.status)
        assertEquals(1L, summary.credentialGeneration)
        assertEquals(1, store.clearCalls)
    }

    @Test
    fun `plain reads and summary publication do not advance generation`() = runTest {
        val store = SecurityGenerationStore()
        val service = service(store)
        val login = service.completeVerifiedLogin(pluginId, capability.policyFingerprint(), listOf(record("first")))

        service.summary(pluginId)
        service.sessionFor(ManagedCredentialRequest(pluginId, "https://api.example.com/v1/books"))
        val finalSummary = service.summary(pluginId)

        assertEquals(1L, login.credentialGeneration)
        assertEquals(1L, finalSummary.credentialGeneration)
    }

    @Test
    fun `failed mutation does not advance generation`() = runTest {
        val store = SecurityGenerationStore().apply { failReplace = true }
        val service = service(store)

        assertFailsWith<IllegalStateException> {
            service.completeVerifiedLogin(pluginId, capability.policyFingerprint(), listOf(record("first")))
        }

        store.failReplace = false
        assertEquals(0L, service.summary(pluginId).credentialGeneration)
    }

    private fun service(store: SecurityGenerationStore) = DefaultPluginSessionService(
        store = store,
        policies = InstalledAuthenticationPolicySource {
            listOf(InstalledAuthenticationPolicy(pluginId, true, capability))
        },
        nowEpochMillis = { NOW },
    )

    private fun record(
        secret: String,
        fingerprint: String = capability.policyFingerprint(),
    ) = PluginSessionRecord(
        pluginId = pluginId,
        targetHost = "api.example.com",
        targetPathPrefix = "/v1",
        cookieName = "session",
        cookieValue = SecretCookieValue.of(secret),
        createdAtEpochMillis = 1,
        expiresAtEpochMillis = EXPIRES_AT,
        authenticationPolicyFingerprint = fingerprint,
    )

    private companion object {
        const val NOW = 1_000L
        const val EXPIRES_AT = 10_000L
    }
}

private class SecurityGenerationStore(
    private val records: MutableList<PluginSessionRecord> = mutableListOf(),
) : PluginSessionStore {
    var clearCalls = 0
    var failNextRead = false
    var failEveryRead = false
    var failReplace = false
    var failReadsAfter: Int? = null
    var readCalls = 0

    override suspend fun readAll(pluginId: PluginId): List<PluginSessionRecord> {
        readCalls += 1
        if (failEveryRead || failReadsAfter?.let { readCalls > it } == true) {
            throw IllegalStateException("persistent read failure")
        }
        if (failNextRead) {
            failNextRead = false
            throw IllegalStateException("read failed")
        }
        return records.filter { it.pluginId == pluginId }
    }

    override suspend fun replaceAll(pluginId: PluginId, records: List<PluginSessionRecord>) {
        if (failReplace) throw IllegalStateException("replace failed")
        this.records.removeAll { it.pluginId == pluginId }
        this.records += records
    }

    override suspend fun clear(pluginId: PluginId) {
        clearCalls += 1
        records.removeAll { it.pluginId == pluginId }
    }
}
