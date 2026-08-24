package app.openstory.auth

import app.openstory.common.id.PluginId
import app.openstory.plugins.runtime.auth.PluginSessionRecord
import app.openstory.plugins.runtime.auth.PluginSessionService
import kotlinx.coroutines.sync.Mutex

class PluginLoginCoordinator(
    private val sessions: PluginSessionService,
) {
    fun tryAcquireCapture(): Boolean = loginMutex.tryLock()

    fun releaseCapture() {
        if (loginMutex.isLocked) loginMutex.unlock()
    }

    suspend fun complete(
        pluginId: PluginId,
        fingerprint: String,
        records: List<PluginSessionRecord>,
    ) = sessions.completeVerifiedLogin(pluginId, fingerprint, records)

    companion object {
        private val loginMutex = Mutex()
    }
}
