package app.openstory.settings.background

import app.openstory.settings.AppSettings

data class BackgroundWorkPolicy(
    val enabled: Boolean,
    val cadenceHours: Int,
    val requireUnmeteredNetwork: Boolean,
    val requireBatteryNotLow: Boolean,
)

fun AppSettings.backgroundWorkPolicy(): BackgroundWorkPolicy = BackgroundWorkPolicy(
    enabled = periodicChapterChecksEnabled,
    cadenceHours = periodicChapterCheckHours,
    requireUnmeteredNetwork = requireUnmeteredNetwork,
    requireBatteryNotLow = requireBatteryNotLow,
)

enum class BackgroundWorkScheduleStatus {
    APPLIED,
    UNCHANGED,
    CANCELLED,
    FAILED,
}

data class BackgroundWorkScheduleResult(
    val status: BackgroundWorkScheduleStatus,
    val errorCode: String? = null,
) {
    init {
        require((status == BackgroundWorkScheduleStatus.FAILED) == (errorCode != null))
    }

    companion object {
        fun applied() = BackgroundWorkScheduleResult(BackgroundWorkScheduleStatus.APPLIED)
        fun unchanged() = BackgroundWorkScheduleResult(BackgroundWorkScheduleStatus.UNCHANGED)
        fun cancelled() = BackgroundWorkScheduleResult(BackgroundWorkScheduleStatus.CANCELLED)
        fun failed() = BackgroundWorkScheduleResult(
            BackgroundWorkScheduleStatus.FAILED,
            errorCode = "background.schedule_failed",
        )
    }
}

interface BackgroundWorkSchedulePort {
    suspend fun apply(policy: BackgroundWorkPolicy): BackgroundWorkScheduleResult
    suspend fun cancelPeriodicChapterChecks(): BackgroundWorkScheduleResult
}
