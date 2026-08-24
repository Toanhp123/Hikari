package app.openstory.settings.background

import app.openstory.common.dispatchers.AppDispatchers
import app.openstory.settings.AppSettingsRepository
import java.util.concurrent.atomic.AtomicBoolean
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.collect
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
        .map { current ->
            BackgroundWorkPolicy(
                enabled = current.periodicChapterChecksEnabled,
                cadenceHours = current.periodicChapterCheckHours,
                requireUnmeteredNetwork = current.requireUnmeteredNetwork,
                requireBatteryNotLow = current.requireBatteryNotLow,
            )
        }
        .distinctUntilChanged()

    fun start() {
        if (!started.compareAndSet(false, true)) return
        scope.launch {
            policies.collect { policy -> scheduler.apply(policy) }
        }
    }
}
