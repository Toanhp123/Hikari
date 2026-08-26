package app.openstory.settings

import android.content.Context
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import java.io.File
import java.nio.file.Files
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class SettingsDataStoreIntegrationTest {
    @Test
    fun productionMigrationMovesEveryLegacyFieldAndCleansUpOnce() = runTest {
        val context = RuntimeEnvironment.getApplication()
        val legacy = context.getSharedPreferences("legacy-${System.nanoTime()}", Context.MODE_PRIVATE)
        legacy.edit()
            .putString(SettingsDataMigration.LEGACY_KEY_LANGUAGES, "ja,en")
            .putBoolean(SettingsDataMigration.LEGACY_KEY_PERIODIC_ENABLED, false)
            .putInt(SettingsDataMigration.LEGACY_KEY_SYNC_INTERVAL, 12)
            .putBoolean(SettingsDataMigration.LEGACY_KEY_WIFI_ONLY, true)
            .putBoolean(SettingsDataMigration.LEGACY_KEY_BATTERY_NOT_LOW, false)
            .putBoolean(SettingsDataMigration.LEGACY_KEY_PROTECTED_REFRESH, false)
            .putBoolean(SettingsDataMigration.LEGACY_KEY_NOTIFY_CHAPTERS, false)
            .putBoolean(SettingsDataMigration.LEGACY_KEY_NOTIFY_LANGUAGES, false)
            .putFloat(SettingsDataMigration.LEGACY_KEY_FONT_SCALE, 1.3f)
            .putLong(SettingsDataMigration.LEGACY_KEY_CACHE_QUOTA, 64L * 1024 * 1024)
            .commit()
        val defaults = SettingsDefaults()
        val file = Files.createTempDirectory("settings-migration").resolve("settings.preferences_pb").toFile()

        val firstScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        val firstStore = PreferenceDataStoreFactory.create(
            migrations = listOf(SharedPreferencesSettingsMigration(legacy, defaults)),
            scope = firstScope,
            produceFile = { file },
        )
        val migrated = DataStoreAppSettingsRepository(firstStore, defaults).settings.first()

        assertEquals(listOf("ja", "en"), migrated.contentLanguageOrder)
        assertEquals(false, migrated.periodicChapterChecksEnabled)
        assertEquals(12, migrated.periodicChapterCheckHours)
        assertEquals(true, migrated.requireUnmeteredNetwork)
        assertEquals(false, migrated.requireBatteryNotLow)
        assertEquals(false, migrated.protectedSourceBackgroundRefresh)
        assertEquals(false, migrated.notifyNewCanonicalChapters)
        assertEquals(false, migrated.notifyPreferredLanguageReleases)
        assertEquals(1.3f, migrated.readerFontScale)
        assertEquals(64L * 1024 * 1024, migrated.automaticCacheQuotaBytes)
        assertTrue(legacy.all.isEmpty())
        firstScope.cancel()

        val secondScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        val secondStore = PreferenceDataStoreFactory.create(
            migrations = listOf(SharedPreferencesSettingsMigration(legacy, defaults)),
            scope = secondScope,
            produceFile = { file },
        )
        assertEquals(migrated, DataStoreAppSettingsRepository(secondStore, defaults).settings.first())
        secondScope.cancel()
    }

    @Test
    fun corruptFileIsReplacedWithDefaultsAndAcceptsLaterWrites() = runTest {
        val defaults = SettingsDefaults(contentLanguageOrder = listOf("th", "en"))
        val directory = Files.createTempDirectory("settings-corruption").toFile()
        val file = File(directory, "settings.preferences_pb").apply { writeBytes(byteArrayOf(1, 2, 3, 4)) }
        val diagnostics = mutableListOf<SettingsDiagnosticCode>()
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        val store = PreferenceDataStoreFactory.create(
            corruptionHandler = SettingsCorruptionHandler(defaults) { code -> diagnostics += code }
                .dataStoreHandler,
            scope = scope,
            produceFile = { file },
        )
        val repository = DataStoreAppSettingsRepository(store, defaults)

        assertEquals(defaults.defaultSettings(), repository.settings.first())
        assertEquals(listOf(SettingsDiagnosticCode.PREFERENCES_CORRUPTED), diagnostics)

        repository.update { current -> current.copy(periodicChapterCheckHours = 12) }
        assertEquals(12, repository.settings.first().periodicChapterCheckHours)
        scope.cancel()
    }
}
