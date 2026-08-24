package app.openstory.settings.background

import app.openstory.common.dispatchers.AppDispatchers
import app.openstory.settings.AppSettingsRepository
import java.util.concurrent.atomic.AtomicBoolean
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

class BackgroundPolicyCoordinator(
    settings: AppSettingsRepository,
    private val scheduler: BackgroundWorkSchedulePort,
    dispatchers: AppDispatchers,
) {
    private val started = AtomicBoolean(false)
    private val scope = CoroutineScope(SupervisorJob() + dispatchers.io)
    private val policies = settings.settings
        .map { current -> current.backgroundWorkPolicy() }
        .distinctUntilChanged()

    fun start() {
        if (!started.compareAndSet(false, true)) return
        scope.launch {
            policies.collect { policy ->
                try {
                    if (policy.enabled) {
                        scheduler.apply(policy)
                    } else {
                        scheduler.cancelPeriodicChapterChecks()
                    }
                } catch (cancelled: CancellationException) {
                    throw cancelled
                } catch (_: Exception) {
                    // Keep the application-scoped collector alive for later policy emissions.
                }
            }
        }
    }
}
