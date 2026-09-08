package app.openstory.startup

import android.content.Context
import android.util.Log
import androidx.datastore.preferences.preferencesDataStore

private val Context.launchStateDataStore by preferencesDataStore(
    name = "hikari_launch_state",
)

internal fun createAppLaunchStateStore(
    context: Context,
): AppLaunchStateStore = AppLaunchStateStore(
    dataStore = context.applicationContext.launchStateDataStore,
    reportFailure = { code, failure ->
        Log.w("HikariLaunchState", code, failure)
    },
)
