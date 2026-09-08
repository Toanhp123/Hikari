package app.openstory.startup

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import java.io.IOException
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.flow.first

private val initialSetupCompletedKey =
    booleanPreferencesKey("initial_setup_completed")

internal class AppLaunchStateStore(
    private val dataStore: DataStore<Preferences>,
    private val reportFailure: (code: String, failure: Throwable) -> Unit = { _, _ -> },
) {
    suspend fun resolve(): AppLaunchState = try {
        if (dataStore.data.first()[initialSetupCompletedKey] == true) {
            AppLaunchState.Ready
        } else {
            AppLaunchState.FirstRun
        }
    } catch (cancelled: CancellationException) {
        throw cancelled
    } catch (failure: IOException) {
        reportFailure("launch_state_read_failed", failure)
        AppLaunchState.FirstRun
    }

    suspend fun markInitialSetupCompleted(): Boolean = try {
        dataStore.edit { preferences ->
            preferences[initialSetupCompletedKey] = true
        }
        true
    } catch (cancelled: CancellationException) {
        throw cancelled
    } catch (failure: IOException) {
        reportFailure("launch_state_write_failed", failure)
        false
    }
}
