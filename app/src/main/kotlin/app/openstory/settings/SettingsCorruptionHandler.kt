package app.openstory.settings

import androidx.datastore.core.CorruptionException
import androidx.datastore.core.handlers.ReplaceFileCorruptionHandler
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.mutablePreferencesOf

class SettingsCorruptionHandler(
    private val defaults: SettingsDefaults,
    private val diagnostics: SettingsDiagnosticSink,
) {
    val dataStoreHandler = ReplaceFileCorruptionHandler<Preferences> { _: CorruptionException ->
        diagnostics.onDiagnostic(SettingsDiagnosticCode.PREFERENCES_CORRUPTED)
        mutablePreferencesOf().also { preferences ->
            SettingsPreferenceCodec.encode(defaults.defaultSettings(), preferences, defaults)
        }
    }
}
