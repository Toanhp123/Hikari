package app.openstory.settings.ui

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

@OptIn(ExperimentalCoroutinesApi::class)
class SettingsViewModelTest {
    private val dispatcher = StandardTestDispatcher()

    @BeforeTest
    fun setUp() = Dispatchers.setMain(dispatcher)

    @AfterTest
    fun tearDown() = Dispatchers.resetMain()

    @Test
    fun installedSessionSummariesAreProjectedToUiState() = runTest(dispatcher.scheduler) {
        val port = FakeSettingsPluginSessionsPort()
        val viewModel = SettingsViewModel(port)
        runCurrent()

        val session = SettingsPluginSessionSummary(
            pluginId = "secured-plugin",
            displayName = "Secured Plugin",
            status = SettingsPluginSessionStatus.AUTHENTICATED,
            expiresAtEpochMillis = 2_000,
        )
        port.sessions.value = listOf(session)
        runCurrent()

        assertEquals(listOf(session), viewModel.state.value.pluginSessions)
    }

    @Test
    fun successfulLoginClearsAPreviousStableError() = runTest(dispatcher.scheduler) {
        val port = FakeSettingsPluginSessionsPort(loginResult = false)
        val viewModel = SettingsViewModel(port)
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
        val port = FakeSettingsPluginSessionsPort(loginFailure = IllegalStateException("secret"))
        val viewModel = SettingsViewModel(port)
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
        val port = FakeSettingsPluginSessionsPort(loginFailure = CancellationException("cancelled"))
        val viewModel = SettingsViewModel(port)
        runCurrent()

        viewModel.login("secured-plugin")
        runCurrent()

        assertNull(viewModel.state.value.authenticationErrorCode)
    }
}

private class FakeSettingsPluginSessionsPort(
    var loginResult: Boolean = true,
    var loginFailure: Throwable? = null,
    var logoutFailure: Throwable? = null,
) : SettingsPluginSessionsPort {
    val sessions = MutableStateFlow<List<SettingsPluginSessionSummary>>(emptyList())

    override fun observeInstalledSessions(): Flow<List<SettingsPluginSessionSummary>> = sessions

    override suspend fun launchLogin(pluginId: String): Boolean {
        loginFailure?.let { throw it }
        return loginResult
    }

    override suspend fun logout(pluginId: String) {
        logoutFailure?.let { throw it }
    }
}
