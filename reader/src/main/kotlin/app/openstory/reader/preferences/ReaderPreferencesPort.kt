package app.openstory.reader.preferences

import kotlinx.coroutines.flow.Flow

data class ReaderPreferences(
    val fontScale: Float = DEFAULT_FONT_SCALE,
    val languageOrder: List<String> = emptyList(),
) {
    val normalizedFontScale: Float
        get() = fontScale.coerceIn(MIN_FONT_SCALE, MAX_FONT_SCALE)

    companion object {
        const val MIN_FONT_SCALE = 0.8f
        const val MAX_FONT_SCALE = 1.6f
        const val DEFAULT_FONT_SCALE = 1f
    }
}

interface ReaderPreferencesPort {
    val preferences: Flow<ReaderPreferences>

    suspend fun setFontScale(value: Float)
}
