package app.openstory.startup

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.preferencesOf
import java.io.IOException
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

class AppLaunchStateStoreTest {
    @Test
    fun absentCompletionFactResolvesFirstRun() = runTest {
        val store = AppLaunchStateStore(FakePreferencesDataStore())

        assertEquals(AppLaunchState.FirstRun, store.resolve())
    }

    @Test
    fun trueCompletionFactResolvesReady() = runTest {
        val completed = preferencesOf(
            booleanPreferencesKey("initial_setup_completed") to true,
        )
        val store = AppLaunchStateStore(FakePreferencesDataStore(initial = completed))

        assertEquals(AppLaunchState.Ready, store.resolve())
    }

    @Test
    fun completionWritePersistsTrue() = runTest {
        val store = AppLaunchStateStore(FakePreferencesDataStore())

        assertTrue(store.markInitialSetupCompleted())
        assertEquals(AppLaunchState.Ready, store.resolve())
    }

    @Test
    fun readIoFailureFallsBackToFirstRun() = runTest {
        val failure = IOException("read failed")
        val reports = mutableListOf<Pair<String, Throwable>>()
        val store = AppLaunchStateStore(
            dataStore = FakePreferencesDataStore(readFailure = failure),
            reportFailure = { code, reportedFailure -> reports += code to reportedFailure },
        )

        assertEquals(AppLaunchState.FirstRun, store.resolve())
        assertEquals(1, reports.size)
        assertEquals("launch_state_read_failed", reports.single().first)
        assertSame(failure, reports.single().second)
    }

    @Test
    fun writeIoFailureReturnsFalse() = runTest {
        val failure = IOException("write failed")
        val reports = mutableListOf<Pair<String, Throwable>>()
        val store = AppLaunchStateStore(
            dataStore = FakePreferencesDataStore(writeFailure = failure),
            reportFailure = { code, reportedFailure -> reports += code to reportedFailure },
        )

        assertFalse(store.markInitialSetupCompleted())
        assertEquals(1, reports.size)
        assertEquals("launch_state_write_failed", reports.single().first)
        assertSame(failure, reports.single().second)
    }

    @Test
    fun readCancellationPropagates() = runTest {
        val cancellation = CancellationException("read cancelled")
        val store = AppLaunchStateStore(
            FakePreferencesDataStore(readFailure = cancellation),
        )

        assertSame(cancellation, captureCancellation { store.resolve() })
    }

    @Test
    fun writeCancellationPropagates() = runTest {
        val cancellation = CancellationException("write cancelled")
        val store = AppLaunchStateStore(
            FakePreferencesDataStore(writeFailure = cancellation),
        )

        assertSame(cancellation, captureCancellation { store.markInitialSetupCompleted() })
    }

    private suspend fun captureCancellation(
        block: suspend () -> Unit,
    ): CancellationException {
        try {
            block()
        } catch (cancelled: CancellationException) {
            return cancelled
        }

        throw AssertionError("Expected CancellationException")
    }
}

private class FakePreferencesDataStore(
    initial: Preferences = emptyPreferences(),
    private val readFailure: Throwable? = null,
    private val writeFailure: Throwable? = null,
) : DataStore<Preferences> {
    private val state = MutableStateFlow(initial)

    override val data: Flow<Preferences> = flow {
        readFailure?.let { throw it }
        emitAll(state)
    }

    override suspend fun updateData(
        transform: suspend (t: Preferences) -> Preferences,
    ): Preferences {
        writeFailure?.let { throw it }
        return transform(state.value).also { state.value = it }
    }
}
