package app.openstory.settings.background

import kotlinx.coroutines.flow.Flow

fun interface BackgroundWorkStatusPort {
    fun observe(): Flow<SettingsBackgroundWorkStatus>
}

data class SettingsBackgroundWorkStatus(
    val registered: Boolean,
    val lastDispatchAtEpochMillis: Long?,
    val lastErrorCode: String?,
)
