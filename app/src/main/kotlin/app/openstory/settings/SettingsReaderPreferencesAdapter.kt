package app.openstory.settings

import app.openstory.reader.preferences.ReaderPreferences
import app.openstory.reader.preferences.ReaderPreferencesPort
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class SettingsReaderPreferencesAdapter(
    private val repository: AppSettingsRepository,
) : ReaderPreferencesPort {
    override val preferences: Flow<ReaderPreferences> = repository.settings.map { settings ->
        ReaderPreferences(
            fontScale = settings.readerFontScale,
            languageOrder = settings.contentLanguageOrder,
        )
    }

    override suspend fun setFontScale(value: Float) {
        repository.update { current -> current.copy(readerFontScale = value) }
    }
}
