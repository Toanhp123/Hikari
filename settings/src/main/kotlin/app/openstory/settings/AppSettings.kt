package app.openstory.settings

data class AppSettings(
    val contentLanguageOrder: List<String>,
    val periodicChapterChecksEnabled: Boolean,
    val periodicChapterCheckHours: Int,
    val requireUnmeteredNetwork: Boolean,
    val requireBatteryNotLow: Boolean,
    val protectedSourceBackgroundRefresh: Boolean,
    val notifyNewCanonicalChapters: Boolean,
    val notifyPreferredLanguageReleases: Boolean,
    val readerFontScale: Float,
    val automaticCacheQuotaBytes: Long,
) {
    fun normalized(defaults: SettingsDefaults = SettingsDefaults()): AppSettings = copy(
        contentLanguageOrder = contentLanguageOrder
            .map(String::trim)
            .map(String::lowercase)
            .filter { it.isNotBlank() && it.none(Char::isWhitespace) && it.none(Char::isISOControl) }
            .distinct()
            .ifEmpty { defaults.contentLanguageOrder.ifEmpty { listOf("en") } },
        periodicChapterCheckHours = periodicChapterCheckHours.takeIf {
            it in SettingsDefaults.ALLOWED_PERIODIC_HOURS
        } ?: defaults.periodicChapterCheckHours,
        readerFontScale = readerFontScale.coerceIn(
            SettingsDefaults.MIN_FONT_SCALE,
            SettingsDefaults.MAX_FONT_SCALE,
        ),
        automaticCacheQuotaBytes = automaticCacheQuotaBytes.coerceIn(
            SettingsDefaults.MIN_CACHE_QUOTA_BYTES,
            SettingsDefaults.MAX_CACHE_QUOTA_BYTES,
        ),
    )
}
