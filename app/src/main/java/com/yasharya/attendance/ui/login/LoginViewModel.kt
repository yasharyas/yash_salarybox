package com.yasharya.attendance.ui.login

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.yasharya.attendance.data.repository.AuthRepository
import com.yasharya.attendance.data.repository.SignInResult
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class LoginUiState(
    val username: String = "",
    val password: String = "",
    val isSubmitting: Boolean = false,
    val error: String? = null,
) {
    val canSubmit: Boolean
        get() = !isSubmitting && username.isNotBlank() && password.isNotBlank()
}

class LoginViewModel(private val authRepository: AuthRepository) : ViewModel() {

    private val _state = MutableStateFlow(LoginUiState())
    val state: StateFlow<LoginUiState> = _state.asStateFlow()

    // Typing is the user fixing the problem, so the error clears as soon as they
    // start rather than sitting there accusing them while they correct it.
    fun onUsernameChange(value: String) = _state.update { it.copy(username = value, error = null) }

    fun onPasswordChange(value: String) = _state.update { it.copy(password = value, error = null) }

    fun signIn() {
        val current = _state.value
        if (!current.canSubmit) return

        _state.update { it.copy(isSubmitting = true, error = null) }
        viewModelScope.launch {
            when (authRepository.signIn(current.username, current.password)) {
                is SignInResult.Success -> {
                    // Navigation is driven by the session flow, so there is
                    // nothing to do here. The screen is torn down.
                    _state.update { it.copy(isSubmitting = false) }
                }
                SignInResult.InvalidCredentials -> _state.update {
                    it.copy(
                        isSubmitting = false,
                        // Deliberately does not say which of the two was wrong.
                        error = "That username and password do not match.",
                        password = "",
                    )
                }
            }
        }
    }
}
