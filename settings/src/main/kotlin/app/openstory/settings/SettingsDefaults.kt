package app.openstory.settings

data class SettingsDefaults(
    val contentLanguageOrder: List<String> = listOf("en"),
    val periodicChapterChecksEnabled: Boolean = true,
    val periodicChapterCheckHours: Int = 6,
    val requireUnmeteredNetwork: Boolean = false,
    val requireBatteryNotLow: Boolean = true,
    val protectedSourceBackgroundRefresh: Boolean = true,
    val notifyNewCanonicalChapters: Boolean = true,
    val notifyPreferredLanguageReleases: Boolean = true,
    val readerFontScale: Float = DEFAULT_FONT_SCALE,
    val automaticCacheQuotaBytes: Long = DEFAULT_CACHE_QUOTA_BYTES,
) {
    fun defaultSettings(): AppSettings = AppSettings(
        contentLanguageOrder = contentLanguageOrder,
        periodicChapterChecksEnabled = periodicChapterChecksEnabled,
        periodicChapterCheckHours = periodicChapterCheckHours,
        requireUnmeteredNetwork = requireUnmeteredNetwork,
        requireBatteryNotLow = requireBatteryNotLow,
        protectedSourceBackgroundRefresh = protectedSourceBackgroundRefresh,
        notifyNewCanonicalChapters = notifyNewCanonicalChapters,
        notifyPreferredLanguageReleases = notifyPreferredLanguageReleases,
        readerFontScale = readerFontScale,
        automaticCacheQuotaBytes = automaticCacheQuotaBytes,
    ).normalized(this)

    companion object {
        val ALLOWED_PERIODIC_HOURS = setOf(1, 3, 6, 12, 24)
        const val MIN_FONT_SCALE = 0.8f
        const val MAX_FONT_SCALE = 1.6f
        const val DEFAULT_FONT_SCALE = 1.0f
        const val MIN_CACHE_QUOTA_BYTES = 0L
        const val MAX_CACHE_QUOTA_BYTES = 2L * 1024 * 1024 * 1024
        const val DEFAULT_CACHE_QUOTA_BYTES = 256L * 1024 * 1024
    }
}
