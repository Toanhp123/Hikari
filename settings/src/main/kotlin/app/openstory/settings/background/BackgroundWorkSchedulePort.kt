package app.openstory.settings.background

data class BackgroundWorkPolicy(
    val enabled: Boolean,
    val cadenceHours: Int,
    val requireUnmeteredNetwork: Boolean,
    val requireBatteryNotLow: Boolean,
)

fun interface BackgroundWorkSchedulePort {
    suspend fun apply(policy: BackgroundWorkPolicy)
}
