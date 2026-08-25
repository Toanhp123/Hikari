package app.openstory.settings.ui

import app.openstory.common.id.PluginId
import app.openstory.settings.background.BackgroundWorkStatusPort
import app.openstory.settings.background.SettingsBackgroundWorkStatus
import app.openstory.settings.notification.NotificationControlPort
import app.openstory.settings.notification.NotificationPermissionRequestResult
import app.openstory.settings.notification.SettingsNotificationStatus
import app.openstory.settings.session.PluginLoginCommandResult
import app.openstory.settings.session.PluginSessionControlPort
import app.openstory.settings.session.SettingsPluginSessionStatus
import app.openstory.settings.session.SettingsPluginSessionSummary
import app.openstory.settings.storage.SettingsStorageSummary
import app.openstory.settings.storage.StorageSummaryPort
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain

@OptIn(ExperimentalCoroutinesApi::class)
class SettingsViewModelTest {
    private val dispatcher = StandardTestDispatcher()

    @BeforeTest
    fun setUp() = Dispatchers.setMain(dispatcher)

    @AfterTest
    fun tearDown() = Dispatchers.resetMain()

    @Test
    fun installedSessionSummariesAreProjectedToUiState() = runTest(dispatcher.scheduler) {
        val port = FakePluginSessionControlPort()
        val viewModel = viewModel(port)
        runCurrent()
        val session = SettingsPluginSessionSummary(
            pluginId = PluginId("secured-plugin"),
            displayName = "Secured Plugin",
            status = SettingsPluginSessionStatus.AUTHENTICATED,
            expiresAtEpochMillis = 2_000,
        )
        port.sessionState.value = listOf(session)
        runCurrent()
        assertEquals(listOf(session), viewModel.state.value.pluginSessions)
    }

    @Test
    fun successfulLoginClearsAPreviousStableError() = runTest(dispatcher.scheduler) {
        val port = FakePluginSessionControlPort(loginResult = false)
        val viewModel = viewModel(port)
        runCurrent()
        viewModel.login("secured-plugin")
        runCurrent()
        assertEquals("settings.auth_login_unavailable", viewModel.state.value.authenticationErrorCode)
        port.loginResult = true
        viewModel.login("secured-plugin")
        runCurrent()
        assertNull(viewModel.state.value.authenticationErrorCode)
    }

    @Test
    fun loginAndLogoutFailuresExposeOnlyStableErrorCodes() = runTest(dispatcher.scheduler) {
        val port = FakePluginSessionControlPort(loginFailure = IllegalStateException("secret"))
        val viewModel = viewModel(port)
        runCurrent()
        viewModel.login("secured-plugin")
        runCurrent()
        assertEquals("settings.auth_login_failed", viewModel.state.value.authenticationErrorCode)
        port.loginFailure = null
        port.logoutFailure = IllegalStateException("secret")
        viewModel.logout("secured-plugin")
        runCurrent()
        assertEquals("settings.auth_logout_failed", viewModel.state.value.authenticationErrorCode)
    }

    @Test
    fun cancellationDoesNotBecomeAnAuthenticationFailure() = runTest(dispatcher.scheduler) {
        val port = FakePluginSessionControlPort(loginFailure = CancellationException("cancelled"))
        val viewModel = viewModel(port)
        runCurrent()
        viewModel.login("secured-plugin")
        runCurrent()
        assertNull(viewModel.state.value.authenticationErrorCode)
    }

    private fun viewModel(sessions: PluginSessionControlPort) = SettingsViewModel(
        sessions,
        FakeNotificationControlPort(),
        BackgroundWorkStatusPort {
            flowOf(SettingsBackgroundWorkStatus(true, null, null))
        },
        StorageSummaryPort {
            flowOf(SettingsStorageSummary(0, 0, 512 * 1024 * 1024L))
        },
    )
}

private class FakePluginSessionControlPort(
    var loginResult: Boolean = true,
    var loginFailure: Throwable? = null,
    var logoutFailure: Throwable? = null,
) : PluginSessionControlPort {
    val sessionState = MutableStateFlow<List<SettingsPluginSessionSummary>>(emptyList())
    override val sessions: Flow<List<SettingsPluginSessionSummary>> = sessionState

    override suspend fun beginLogin(pluginId: PluginId): PluginLoginCommandResult {
        loginFailure?.let { throw it }
        return if (loginResult) PluginLoginCommandResult.Launched else {
            PluginLoginCommandResult.Rejected("settings.auth_login_unavailable")
        }
    }

    override suspend fun logout(pluginId: PluginId) {
        logoutFailure?.let { throw it }
    }
}

private class FakeNotificationControlPort : NotificationControlPort {
    override val status = flowOf(SettingsNotificationStatus(true, true, 0))
    override suspend fun requestPermission() = NotificationPermissionRequestResult.Granted
}
