package app.openstory.settings

import androidx.datastore.preferences.core.MutablePreferences

object SettingsDataMigration {
    const val LEGACY_KEY_LANGUAGES = "app.openstory.languages"
    const val LEGACY_KEY_PERIODIC_ENABLED = "app.openstory.periodic_enabled"
    const val LEGACY_KEY_SYNC_INTERVAL = "app.openstory.sync_interval"
    const val LEGACY_KEY_WIFI_ONLY = "app.openstory.wifi_only"
    const val LEGACY_KEY_BATTERY_NOT_LOW = "app.openstory.battery_saver"
    const val LEGACY_KEY_PROTECTED_REFRESH = "app.openstory.protected_refresh"
    const val LEGACY_KEY_NOTIFY_CHAPTERS = "app.openstory.notify_chapters"
    const val LEGACY_KEY_NOTIFY_LANGUAGES = "app.openstory.notify_languages"
    const val LEGACY_KEY_FONT_SCALE = "app.openstory.font_size"
    const val LEGACY_KEY_CACHE_QUOTA = "app.openstory.cache_quota"

    val legacyKeys = setOf(
        LEGACY_KEY_LANGUAGES,
        LEGACY_KEY_PERIODIC_ENABLED,
        LEGACY_KEY_SYNC_INTERVAL,
        LEGACY_KEY_WIFI_ONLY,
        LEGACY_KEY_BATTERY_NOT_LOW,
        LEGACY_KEY_PROTECTED_REFRESH,
        LEGACY_KEY_NOTIFY_CHAPTERS,
        LEGACY_KEY_NOTIFY_LANGUAGES,
        LEGACY_KEY_FONT_SCALE,
        LEGACY_KEY_CACHE_QUOTA,
    )

    fun migrateLegacyMap(
        legacy: Map<String, *>,
        defaults: SettingsDefaults = SettingsDefaults(),
    ): AppSettings = AppSettings(
        contentLanguageOrder = (legacy[LEGACY_KEY_LANGUAGES] as? String)
            ?.split(',')
            ?.map(String::trim)
            ?.filter(String::isNotEmpty)
            ?: defaults.contentLanguageOrder,
        periodicChapterChecksEnabled = legacy[LEGACY_KEY_PERIODIC_ENABLED] as? Boolean
            ?: defaults.periodicChapterChecksEnabled,
        periodicChapterCheckHours = (legacy[LEGACY_KEY_SYNC_INTERVAL] as? Number)?.toInt()
            ?: defaults.periodicChapterCheckHours,
        requireUnmeteredNetwork = legacy[LEGACY_KEY_WIFI_ONLY] as? Boolean
            ?: defaults.requireUnmeteredNetwork,
        requireBatteryNotLow = legacy[LEGACY_KEY_BATTERY_NOT_LOW] as? Boolean
            ?: defaults.requireBatteryNotLow,
        protectedSourceBackgroundRefresh = legacy[LEGACY_KEY_PROTECTED_REFRESH] as? Boolean
            ?: defaults.protectedSourceBackgroundRefresh,
        notifyNewCanonicalChapters = legacy[LEGACY_KEY_NOTIFY_CHAPTERS] as? Boolean
            ?: defaults.notifyNewCanonicalChapters,
        notifyPreferredLanguageReleases = legacy[LEGACY_KEY_NOTIFY_LANGUAGES] as? Boolean
            ?: defaults.notifyPreferredLanguageReleases,
        readerFontScale = (legacy[LEGACY_KEY_FONT_SCALE] as? Number)?.toFloat()
            ?: defaults.readerFontScale,
        automaticCacheQuotaBytes = (legacy[LEGACY_KEY_CACHE_QUOTA] as? Number)?.toLong()
            ?: defaults.automaticCacheQuotaBytes,
    ).normalized(defaults)

    fun apply(
        legacy: Map<String, *>,
        preferences: MutablePreferences,
        defaults: SettingsDefaults = SettingsDefaults(),
    ) = SettingsPreferenceCodec.encode(migrateLegacyMap(legacy, defaults), preferences, defaults)
}
