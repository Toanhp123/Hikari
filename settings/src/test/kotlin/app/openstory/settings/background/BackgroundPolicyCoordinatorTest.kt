package app.openstory.settings.background

import app.openstory.common.dispatchers.FixedAppDispatchers
import app.openstory.settings.AppSettings
import app.openstory.settings.AppSettingsRepository
import app.openstory.settings.SettingsDefaults
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals

@OptIn(ExperimentalCoroutinesApi::class)
class BackgroundPolicyCoordinatorTest {
    @Test
    fun appliesOnlyDistinctSchedulingPolicy() = runTest {
        val values = MutableStateFlow(SettingsDefaults().defaultSettings())
        val repository = FakeSettingsRepository(values)
        val applied = mutableListOf<BackgroundWorkPolicy>()
        val dispatcher = StandardTestDispatcher(testScheduler)
        val coordinator = BackgroundPolicyCoordinator(
            repository,
            BackgroundWorkSchedulePort { policy -> applied += policy },
            FixedAppDispatchers(dispatcher, dispatcher, dispatcher),
        )

        coordinator.start()
        runCurrent()
        values.value = values.value.copy(readerFontScale = 1.2f)
        runCurrent()
        values.value = values.value.copy(requireUnmeteredNetwork = true)
        runCurrent()

        assertEquals(2, applied.size)
        assertEquals(false, applied.first().requireUnmeteredNetwork)
        assertEquals(true, applied.last().requireUnmeteredNetwork)
    }

    @Test
    fun disablingProjectsAnExplicitDisabledPolicy() = runTest {
        val values = MutableStateFlow(SettingsDefaults().defaultSettings())
        val applied = mutableListOf<BackgroundWorkPolicy>()
        val dispatcher = StandardTestDispatcher(testScheduler)
        val coordinator = BackgroundPolicyCoordinator(
            FakeSettingsRepository(values),
            BackgroundWorkSchedulePort { policy -> applied += policy },
            FixedAppDispatchers(dispatcher, dispatcher, dispatcher),
        )

        coordinator.start()
        runCurrent()
        values.value = values.value.copy(periodicChapterChecksEnabled = false)
        runCurrent()

        assertEquals(false, applied.last().enabled)
    }
}

private class FakeSettingsRepository(
    override val settings: Flow<AppSettings>,
) : AppSettingsRepository {
    override suspend fun update(transform: (AppSettings) -> AppSettings) = Unit
    override suspend fun resetToDefaults() = Unit
}
