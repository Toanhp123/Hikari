package app.openstory.settings

import android.content.SharedPreferences
import androidx.datastore.core.DataMigration
import androidx.datastore.preferences.core.MutablePreferences
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.mutablePreferencesOf

class LegacySettingsDataMigration(
    private val legacy: SharedPreferences,
    private val defaults: SettingsDefaults,
) : DataMigration<Preferences> {
    override suspend fun shouldMigrate(currentData: Preferences): Boolean =
        legacy.all.keys.any(SettingsDataMigration.legacyKeys::contains)

    override suspend fun migrate(currentData: Preferences): Preferences =
        currentData.mutableCopy().also { migrated ->
            SettingsDataMigration.apply(legacy.all, migrated, defaults)
        }

    override suspend fun cleanUp() {
        check(legacy.edit().clear().commit()) { "Legacy settings cleanup was not durable" }
    }

    companion object {
        const val LEGACY_PREFERENCES_NAME = "openstory_settings"
    }
}

private fun Preferences.mutableCopy(): MutablePreferences = mutablePreferencesOf().also { target ->
    asMap().forEach { (key, value) -> target.setUntyped(key, value) }
}

@Suppress("UNCHECKED_CAST")
private fun MutablePreferences.setUntyped(key: Preferences.Key<*>, value: Any) {
    this[key as Preferences.Key<Any>] = value
}
