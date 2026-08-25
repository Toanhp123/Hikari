package app.openstory.settings

import android.content.Context
import android.content.SharedPreferences
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.distinctUntilChanged

data class BackgroundDispatchStatusSnapshot(
    val lastDispatchAtEpochMillis: Long?,
    val lastErrorCode: String?,
)

class BackgroundDispatchStatusStore(
    context: Context,
) {
    private val preferences = context.applicationContext.getSharedPreferences(
        PREFERENCES_NAME,
        Context.MODE_PRIVATE,
    )

    fun recordSuccess(atEpochMillis: Long) {
        require(atEpochMillis >= 0L)
        check(
            preferences.edit()
                .putLong(KEY_LAST_DISPATCH_AT, atEpochMillis)
                .remove(KEY_LAST_ERROR_CODE)
                .commit(),
        )
    }

    fun recordFailure(errorCode: String) {
        require(errorCode.startsWith("background."))
        check(preferences.edit().putString(KEY_LAST_ERROR_CODE, errorCode).commit())
    }

    fun observe(): Flow<BackgroundDispatchStatusSnapshot> = callbackFlow {
        val listener = SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
            if (key == KEY_LAST_DISPATCH_AT || key == KEY_LAST_ERROR_CODE) trySend(read())
        }
        trySend(read())
        preferences.registerOnSharedPreferenceChangeListener(listener)
        awaitClose { preferences.unregisterOnSharedPreferenceChangeListener(listener) }
    }.distinctUntilChanged()

    private fun read() = BackgroundDispatchStatusSnapshot(
        lastDispatchAtEpochMillis = preferences.getLong(KEY_LAST_DISPATCH_AT, 0L).takeIf { it > 0L },
        lastErrorCode = preferences.getString(KEY_LAST_ERROR_CODE, null),
    )

    private companion object {
        const val PREFERENCES_NAME = "background_dispatch_status"
        const val KEY_LAST_DISPATCH_AT = "last_dispatch_at_epoch_millis"
        const val KEY_LAST_ERROR_CODE = "last_error_code"
    }
}
