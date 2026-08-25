package app.openstory.work

import androidx.work.Constraints
import androidx.work.NetworkType
import app.openstory.settings.SettingsDefaults
import app.openstory.settings.background.BackgroundWorkPolicy

object WorkConstraintsFactory {
    fun networkConnected(): Constraints = Constraints.Builder()
        .setRequiredNetworkType(NetworkType.CONNECTED)
        .build()

    fun periodic(policy: BackgroundWorkPolicy): Constraints {
        require(policy.cadenceHours in SettingsDefaults.ALLOWED_PERIODIC_HOURS) {
            "Periodic cadence is outside the typed settings contract"
        }
        return Constraints.Builder()
            .setRequiredNetworkType(
                if (policy.requireUnmeteredNetwork) NetworkType.UNMETERED else NetworkType.CONNECTED,
            )
            .setRequiresBatteryNotLow(policy.requireBatteryNotLow)
            .build()
    }
}
