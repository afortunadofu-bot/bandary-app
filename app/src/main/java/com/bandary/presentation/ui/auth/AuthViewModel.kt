package com.bandary.presentation.ui.auth

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.bandary.data.local.SettingsDataStore
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class AuthUiState(
    val email: String = "",
    val password: String = "",
    val loading: Boolean = false,
    val errorMessage: String? = null,
    val loggedInEmail: String? = null
)

@HiltViewModel
class AuthViewModel @Inject constructor(
    private val settings: SettingsDataStore
) : ViewModel() {

    private val _state = MutableStateFlow(AuthUiState())
    val state: StateFlow<AuthUiState> = _state.asStateFlow()

    init {
        viewModelScope.launch {
            val current = settings.loggedInEmail.first()
            _state.update { it.copy(loggedInEmail = current) }
        }
    }

    fun onEmailChange(v: String) = _state.update { it.copy(email = v, errorMessage = null) }
    fun onPasswordChange(v: String) = _state.update { it.copy(password = v, errorMessage = null) }

    fun login(onSuccess: () -> Unit) {
        val email = _state.value.email.trim()
        val pwd = _state.value.password
        if (email.isBlank() || !email.contains("@")) {
            _state.update { it.copy(errorMessage = "請輸入有效的 Email") }
            return
        }
        if (pwd.length < 4) {
            _state.update { it.copy(errorMessage = "密碼至少 4 個字") }
            return
        }
        _state.update { it.copy(loading = true, errorMessage = null) }
        viewModelScope.launch {
            // Stub auth — Firebase Auth replaces this once google-services.json is in place.
            // Dev/admin combos unlock special states for testing.
            val isDevAdmin = email == "dev@bandary.app" && pwd == "bandary-admin"
            val isDevPro = email == "dev@bandary.app" && pwd == "bandary-pro"
            settings.setLoggedInEmail(email)
            if (isDevAdmin) {
                settings.setAdmin(true)
                settings.setPro(true)
            } else if (isDevPro) {
                settings.setPro(true)
            }
            _state.update { it.copy(loading = false, loggedInEmail = email) }
            onSuccess()
        }
    }

    fun logout() {
        viewModelScope.launch {
            settings.setLoggedInEmail(null)
            settings.setAdmin(false)
            settings.setPro(false)
            settings.setProPlus(false)
            _state.update { AuthUiState() }
        }
    }

    fun unlockWithAdminCode(code: String): Boolean {
        // Hidden gesture unlocks the entry to admin code dialog; this validates it.
        if (code.trim() == "BANDARY-1218") {
            viewModelScope.launch {
                settings.setAdmin(true)
                settings.setPro(true)
            }
            return true
        }
        return false
    }
}
