package app.openstory.settings.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class SettingsUiState(
    val pluginSessions: List<SettingsPluginSessionSummary> = emptyList(),
    val authenticationErrorCode: String? = null,
)

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val pluginSessions: SettingsPluginSessionsPort,
) : ViewModel() {
    private val mutableState = MutableStateFlow(SettingsUiState())
    val state: StateFlow<SettingsUiState> = mutableState.asStateFlow()

    init {
        viewModelScope.launch {
            pluginSessions.observeInstalledSessions().collect { sessions ->
                mutableState.value = mutableState.value.copy(pluginSessions = sessions)
            }
        }
    }

    fun login(pluginId: String) {
        viewModelScope.launch {
            try {
                if (!pluginSessions.launchLogin(pluginId)) {
                    mutableState.value = mutableState.value.copy(
                        authenticationErrorCode = "settings.auth_login_unavailable",
                    )
                } else {
                    mutableState.value = mutableState.value.copy(authenticationErrorCode = null)
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                mutableState.value = mutableState.value.copy(
                    authenticationErrorCode = "settings.auth_login_failed",
                )
            }
        }
    }

    fun logout(pluginId: String) {
        viewModelScope.launch {
            try {
                pluginSessions.logout(pluginId)
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                mutableState.value = mutableState.value.copy(
                    authenticationErrorCode = "settings.auth_logout_failed",
                )
            }
        }
    }
}
