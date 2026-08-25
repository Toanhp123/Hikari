package app.openstory.settings

import android.content.Context
import androidx.work.WorkInfo
import androidx.work.WorkManager
import app.openstory.settings.background.BackgroundWorkStatusPort
import app.openstory.settings.background.SettingsBackgroundWorkStatus
import app.openstory.work.WorkNames
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map

class WorkManagerBackgroundWorkStatusAdapter(
    context: Context,
) : BackgroundWorkStatusPort {
    private val workManager = WorkManager.getInstance(context.applicationContext)
    private val statusStore = BackgroundDispatchStatusStore(context)

    override fun observe(): Flow<SettingsBackgroundWorkStatus> = combine(
        workManager
        .getWorkInfosForUniqueWorkFlow(WorkNames.LIBRARY_CHAPTER_PERIODIC)
        .map { workInfos ->
            val current = workInfos.filter { it.state in REGISTERED_STATES }
                .maxByOrNull { it.runAttemptCount }
                ?: workInfos.maxByOrNull { it.runAttemptCount }
            current?.state in REGISTERED_STATES
        }
        .catch {
            emit(false)
        },
        statusStore.observe(),
    ) { registered, status ->
        SettingsBackgroundWorkStatus(
            registered = registered,
            lastDispatchAtEpochMillis = status.lastDispatchAtEpochMillis,
            lastErrorCode = status.lastErrorCode,
        )
    }

    companion object {
        private val REGISTERED_STATES = setOf(WorkInfo.State.ENQUEUED, WorkInfo.State.RUNNING, WorkInfo.State.BLOCKED)
    }
}
