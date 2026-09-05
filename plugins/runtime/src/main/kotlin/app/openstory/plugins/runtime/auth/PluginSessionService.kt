package app.openstory.plugins.runtime.auth

import app.openstory.common.id.PluginId
import app.openstory.plugins.api.manifest.PluginAuthenticationCapability
import app.openstory.plugins.runtime.capabilities.http.ManagedCredentialRequest
import java.net.URI
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update

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
    private val credentialGenerations = mutableMapOf<PluginId, Long>()

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
        val generation = advanceCredentialGeneration(pluginId)
        return summaryForRecords(pluginId, policy, records, generation).also(::publish)
    }

    override suspend fun logout(pluginId: PluginId) {
        store.clear(pluginId)
        val generation = advanceCredentialGeneration(pluginId)
        publish(PluginSessionSummary(pluginId, PluginSessionStatus.LOGGED_OUT, null, generation))
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
            val generation = advanceCredentialGeneration(request.pluginId)
            publish(
                PluginSessionSummary(
                    pluginId = request.pluginId,
                    status = PluginSessionStatus.LOGGED_OUT,
                    expiresAtEpochMillis = null,
                    credentialGeneration = generation,
                ),
            )
            return emptyList()
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
                val generation = advanceCredentialGeneration(policy.pluginId)
                publish(PluginSessionSummary(policy.pluginId, PluginSessionStatus.LOGGED_OUT, null, generation))
            } else {
                refreshSummary(policy.pluginId, policy)
            }
        }
    }

    private suspend fun policy(pluginId: PluginId): InstalledAuthenticationPolicy =
        requireNotNull(policies.installedAuthenticationPolicies().singleOrNull { it.pluginId == pluginId })

    private suspend fun refreshSummary(
        pluginId: PluginId,
        policy: InstalledAuthenticationPolicy,
    ): PluginSessionSummary {
        val records = store.readAll(pluginId)
        return summaryForRecords(
            pluginId = pluginId,
            policy = policy,
            records = records,
            generation = credentialGeneration(pluginId),
        ).also(::publish)
    }

    private fun summaryForRecords(
        pluginId: PluginId,
        policy: InstalledAuthenticationPolicy,
        records: List<PluginSessionRecord>,
        generation: Long,
    ): PluginSessionSummary {
        val expiresAt = records.minOfOrNull(PluginSessionRecord::expiresAtEpochMillis)
        val status = when {
            records.isEmpty() -> PluginSessionStatus.LOGGED_OUT
            !policy.enabled || expiresAt == null || expiresAt <= nowEpochMillis() -> PluginSessionStatus.EXPIRED
            else -> PluginSessionStatus.AUTHENTICATED
        }
        return PluginSessionSummary(pluginId, status, expiresAt, generation)
    }

    private fun credentialGeneration(pluginId: PluginId): Long = synchronized(credentialGenerations) {
        credentialGenerations[pluginId] ?: 0L
    }

    private fun advanceCredentialGeneration(pluginId: PluginId): Long = synchronized(credentialGenerations) {
        val current = credentialGenerations[pluginId] ?: 0L
        check(current < Long.MAX_VALUE) { "Plugin credential generation exhausted." }
        (current + 1L).also { credentialGenerations[pluginId] = it }
    }

    private fun publish(summary: PluginSessionSummary) {
        summaries.update { current ->
            (current.filterNot { it.pluginId == summary.pluginId } + summary)
                .sortedBy { it.pluginId.value }
        }
    }
}
