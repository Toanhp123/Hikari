package app.openstory.settings

import androidx.datastore.preferences.core.MutablePreferences
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import kotlinx.coroutines.CancellationException

object SettingsPreferenceCodec {
    private val contentLanguages = stringPreferencesKey("settings.content_languages")
    private val periodicChecksEnabled = booleanPreferencesKey("settings.periodic_checks_enabled")
    private val periodicCheckHours = intPreferencesKey("settings.periodic_check_hours")
    private val requireUnmetered = booleanPreferencesKey("settings.require_unmetered_network")
    private val requireBatteryNotLow = booleanPreferencesKey("settings.require_battery_not_low")
    private val protectedSourceRefresh = booleanPreferencesKey("settings.protected_source_refresh")
    private val notifyNewChapters = booleanPreferencesKey("settings.notify_new_canonical_chapters")
    private val notifyPreferredReleases = booleanPreferencesKey("settings.notify_preferred_language_releases")
    private val readerFontScale = floatPreferencesKey("settings.reader_font_scale")
    private val automaticCacheQuota = longPreferencesKey("settings.automatic_cache_quota_bytes")

    fun decode(preferences: Preferences, defaults: SettingsDefaults): AppSettings = AppSettings(
        contentLanguageOrder = read(preferences, contentLanguages)
            ?.split(',')
            ?.map(String::trim)
            ?.filter(String::isNotEmpty)
            ?: defaults.contentLanguageOrder,
        periodicChapterChecksEnabled = read(preferences, periodicChecksEnabled)
            ?: defaults.periodicChapterChecksEnabled,
        periodicChapterCheckHours = read(preferences, periodicCheckHours)
            ?: defaults.periodicChapterCheckHours,
        requireUnmeteredNetwork = read(preferences, requireUnmetered)
            ?: defaults.requireUnmeteredNetwork,
        requireBatteryNotLow = read(preferences, requireBatteryNotLow)
            ?: defaults.requireBatteryNotLow,
        protectedSourceBackgroundRefresh = read(preferences, protectedSourceRefresh)
            ?: defaults.protectedSourceBackgroundRefresh,
        notifyNewCanonicalChapters = read(preferences, notifyNewChapters)
            ?: defaults.notifyNewCanonicalChapters,
        notifyPreferredLanguageReleases = read(preferences, notifyPreferredReleases)
            ?: defaults.notifyPreferredLanguageReleases,
        readerFontScale = read(preferences, readerFontScale) ?: defaults.readerFontScale,
        automaticCacheQuotaBytes = read(preferences, automaticCacheQuota)
            ?: defaults.automaticCacheQuotaBytes,
    ).normalized(defaults)

    fun encode(settings: AppSettings, preferences: MutablePreferences, defaults: SettingsDefaults) {
        val normalized = settings.normalized(defaults)
        preferences[contentLanguages] = normalized.contentLanguageOrder.joinToString(",")
        preferences[periodicChecksEnabled] = normalized.periodicChapterChecksEnabled
        preferences[periodicCheckHours] = normalized.periodicChapterCheckHours
        preferences[requireUnmetered] = normalized.requireUnmeteredNetwork
        preferences[requireBatteryNotLow] = normalized.requireBatteryNotLow
        preferences[protectedSourceRefresh] = normalized.protectedSourceBackgroundRefresh
        preferences[notifyNewChapters] = normalized.notifyNewCanonicalChapters
        preferences[notifyPreferredReleases] = normalized.notifyPreferredLanguageReleases
        preferences[readerFontScale] = normalized.readerFontScale
        preferences[automaticCacheQuota] = normalized.automaticCacheQuotaBytes
    }

    private fun <T> read(preferences: Preferences, key: Preferences.Key<T>): T? = try {
        preferences[key]
    } catch (cancelled: CancellationException) {
        throw cancelled
    } catch (_: RuntimeException) {
        null
    }
}
