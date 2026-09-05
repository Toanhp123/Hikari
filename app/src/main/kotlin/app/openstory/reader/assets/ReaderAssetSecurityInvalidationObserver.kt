package app.openstory.reader.assets

import app.openstory.plugins.runtime.auth.PluginSessionService
import app.openstory.plugins.runtime.auth.PluginSessionStatus
import app.openstory.plugins.runtime.auth.PluginSessionSummary
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch

@Singleton
class ReaderAssetSecurityInvalidationObserver @Inject constructor(
    private val sessions: PluginSessionService,
    private val store: ReaderAssetStorePort,
    private val coordinator: ReaderAssetCoordinator,
) {
    fun start(scope: CoroutineScope): Job = scope.launch {
        val previous = mutableMapOf<String, SecurityFacts>()
        sessions.observeInstalledSessions().collect { summaries ->
            summaries.forEach { summary ->
                val key = summary.pluginId.value
                val current = summary.securityFacts()
                val prior = previous.put(key, current)
                if (prior != null && current.crossesSecurityBoundaryFrom(prior)) {
                    val sourceNamespace = ReaderAssetSourceNamespace.fromPluginId(summary.pluginId)
                    coordinator.invalidateSecurityScopedSource(sourceNamespace)
                    clearDurableAccountScopesBestEffort(sourceNamespace)
                }
            }
        }
    }

    private suspend fun clearDurableAccountScopesBestEffort(sourceNamespace: ReaderAssetSourceNamespace) {
        try {
            store.clearAutomatic(ReaderAssetClearScope.AllAccountScopesForSource(sourceNamespace))
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (@Suppress("TooGenericExceptionCaught") ignored: Exception) {
            // Runtime state has already crossed the credential boundary. Production does not
            // synthesize persistent account namespaces; bounded storage maintenance may retry
            // detached/orphan cleanup independently.
        }
    }

    private data class SecurityFacts(
        val status: PluginSessionStatus,
        val credentialGeneration: Long,
    )

    private fun PluginSessionSummary.securityFacts() = SecurityFacts(status, credentialGeneration)

    private fun SecurityFacts.crossesSecurityBoundaryFrom(previous: SecurityFacts): Boolean =
        credentialGeneration != previous.credentialGeneration ||
            (status != previous.status && status.isSecurityTerminal())

    private fun PluginSessionStatus.isSecurityTerminal(): Boolean =
        this == PluginSessionStatus.LOGGED_OUT || this == PluginSessionStatus.EXPIRED
}
