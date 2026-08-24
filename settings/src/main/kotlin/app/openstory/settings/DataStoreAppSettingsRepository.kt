package app.openstory.settings

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import java.io.IOException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map

class DataStoreAppSettingsRepository(
    private val dataStore: DataStore<Preferences>,
    private val defaults: SettingsDefaults = SettingsDefaults(),
    private val diagnostics: SettingsDiagnosticSink? = null,
) : AppSettingsRepository {
    override val settings: Flow<AppSettings> = dataStore.data
        .catch { failure ->
            when (failure) {
                is CancellationException -> throw failure
                is IOException -> diagnostics?.onDiagnostic(SettingsDiagnosticCode.PREFERENCES_READ_FAILED)
                else -> throw failure
            }
            throw failure
        }
        .map { SettingsPreferenceCodec.decode(it, defaults) }

    override suspend fun update(transform: (AppSettings) -> AppSettings) {
        try {
            dataStore.edit { preferences ->
                val next = transform(SettingsPreferenceCodec.decode(preferences, defaults)).normalized(defaults)
                SettingsPreferenceCodec.encode(next, preferences, defaults)
            }
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (failure: IOException) {
            diagnostics?.onDiagnostic(SettingsDiagnosticCode.PREFERENCES_WRITE_FAILED)
            throw failure
        }
    }

    override suspend fun resetToDefaults() = update { defaults.defaultSettings() }
}
