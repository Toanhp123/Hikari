package app.openstory.plugins.runtime.auth

import app.openstory.common.id.PluginId
import app.openstory.plugins.api.manifest.PluginAuthenticationCapability
import app.openstory.plugins.runtime.capabilities.http.ManagedCredentialRequest
import java.net.URI
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow

data class InstalledAuthenticationPolicy(
    val pluginId: PluginId,
    val enabled: Boolean,
    val capability: PluginAuthenticationCapability,
)

fun interface InstalledAuthenticationPolicySource {
    suspend fun installedAuthenticationPolicies(): List<InstalledAuthenticationPolicy>
}

interface PluginSessionService {
    fun observeInstalledSessions(): Flow<List<PluginSessionSummary>>
    suspend fun completeVerifiedLogin(
        pluginId: PluginId,
        authenticationPolicyFingerprint: String,
        records: List<PluginSessionRecord>,
    ): PluginSessionSummary
    suspend fun logout(pluginId: PluginId)
    suspend fun sessionFor(request: ManagedCredentialRequest): List<PluginSessionRecord>
    suspend fun summary(pluginId: PluginId): PluginSessionSummary
    suspend fun invalidateChangedPolicies()
}

class DefaultPluginSessionService(
    private val store: PluginSessionStore,
    private val policies: InstalledAuthenticationPolicySource,
    private val nowEpochMillis: () -> Long = System::currentTimeMillis,
) : PluginSessionService {
    private val summaries = MutableStateFlow<List<PluginSessionSummary>>(emptyList())

    override fun observeInstalledSessions(): Flow<List<PluginSessionSummary>> = summaries

    override suspend fun completeVerifiedLogin(
        pluginId: PluginId,
        authenticationPolicyFingerprint: String,
        records: List<PluginSessionRecord>,
    ): PluginSessionSummary {
        val policy = policy(pluginId)
        require(policy.enabled && policy.capability.policyFingerprint() == authenticationPolicyFingerprint)
        require(records.isNotEmpty() && records.all { record ->
            record.pluginId == pluginId &&
                record.authenticationPolicyFingerprint == authenticationPolicyFingerprint &&
                policy.capability.credentialTargets.any { target ->
                    record.targetHost == target.host &&
                        record.targetPathPrefix == target.pathPrefix &&
                        record.cookieName in target.cookieNames
                }
        })
        store.replaceAll(pluginId, records)
        return refreshSummary(pluginId, policy)
    }

    override suspend fun logout(pluginId: PluginId) {
        store.clear(pluginId)
        publish(PluginSessionSummary(pluginId, PluginSessionStatus.LOGGED_OUT, null))
    }

    override suspend fun sessionFor(request: ManagedCredentialRequest): List<PluginSessionRecord> {
        val policy = policies.installedAuthenticationPolicies()
            .singleOrNull { it.pluginId == request.pluginId }
        return if (policy == null || !policy.enabled) {
            emptyList()
        } else {
            validSessionRecords(request, policy)
        }
    }

    private suspend fun validSessionRecords(
        request: ManagedCredentialRequest,
        policy: InstalledAuthenticationPolicy,
    ): List<PluginSessionRecord> {
        val fingerprint = policy.capability.policyFingerprint()
        val now = nowEpochMillis()
        val uri = URI(request.url)
        val valid = try {
            store.readAll(request.pluginId).filter { record ->
                record.authenticationPolicyFingerprint == fingerprint &&
                    record.expiresAtEpochMillis > now &&
                    record.targetHost == uri.host?.lowercase() &&
                    uri.path.orEmpty().ifBlank { "/" }.startsWith(record.targetPathPrefix)
            }
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            store.clear(request.pluginId)
            emptyList()
        }
        refreshSummary(request.pluginId, policy)
        return valid
    }

    override suspend fun summary(pluginId: PluginId): PluginSessionSummary =
        refreshSummary(pluginId, policy(pluginId))

    override suspend fun invalidateChangedPolicies() {
        policies.installedAuthenticationPolicies().forEach { policy ->
            val records = store.readAll(policy.pluginId)
            if (records.any { it.authenticationPolicyFingerprint != policy.capability.policyFingerprint() }) {
                store.clear(policy.pluginId)
            }
            refreshSummary(policy.pluginId, policy)
        }
    }

    private suspend fun policy(pluginId: PluginId): InstalledAuthenticationPolicy =
        requireNotNull(policies.installedAuthenticationPolicies().singleOrNull { it.pluginId == pluginId })

    private suspend fun refreshSummary(
        pluginId: PluginId,
        policy: InstalledAuthenticationPolicy,
    ): PluginSessionSummary {
        val records = store.readAll(pluginId)
        val expiresAt = records.minOfOrNull(PluginSessionRecord::expiresAtEpochMillis)
        val status = when {
            records.isEmpty() -> PluginSessionStatus.LOGGED_OUT
            !policy.enabled || expiresAt == null || expiresAt <= nowEpochMillis() -> PluginSessionStatus.EXPIRED
            else -> PluginSessionStatus.AUTHENTICATED
        }
        return PluginSessionSummary(pluginId, status, expiresAt).also(::publish)
    }

    private fun publish(summary: PluginSessionSummary) {
        summaries.value = (summaries.value.filterNot { it.pluginId == summary.pluginId } + summary)
            .sortedBy { it.pluginId.value }
    }
}
