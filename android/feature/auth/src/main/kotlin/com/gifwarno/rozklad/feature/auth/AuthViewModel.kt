package com.gifwarno.rozklad.feature.auth

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.gifwarno.rozklad.core.data.repository.SessionRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class AuthUiState(
    val email: String = "",
    val password: String = "",
    val isSignUp: Boolean = false,
    val busy: Boolean = false,
    val message: String? = null,
    val messageIsError: Boolean = false,
) {
    val canSubmit: Boolean get() = !busy && EMAIL.matches(email.trim()) && password.length >= MIN_PASSWORD

    companion object {
        const val MIN_PASSWORD = 6
        private val EMAIL = Regex("^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$")
    }
}

@HiltViewModel
class AuthViewModel @Inject constructor(private val session: SessionRepository) : ViewModel() {
    private val _state = MutableStateFlow(AuthUiState())
    val state: StateFlow<AuthUiState> = _state.asStateFlow()

    fun onEmail(v: String) = _state.update { it.copy(email = v.take(MAX_EMAIL), message = null) }

    fun onPassword(v: String) = _state.update { it.copy(password = v.take(MAX_PASSWORD), message = null) }

    fun toggleMode() = _state.update { it.copy(isSignUp = !it.isSignUp, message = null) }

    fun submit() {
        val s = _state.value
        if (!s.canSubmit) return
        _state.update { it.copy(busy = true, message = null) }
        viewModelScope.launch {
            if (s.isSignUp) {
                session.signUp(s.email, s.password)
                    .onSuccess { needConfirm ->
                        _state.update {
                            it.copy(
                                busy = false,
                                isSignUp = !needConfirm,
                                message = if (needConfirm) "Готово! Підтвердь пошту з листа і увійди." else null,
                                messageIsError = false,
                            )
                        }
                    }
                    .onFailure { e -> _state.update { it.copy(busy = false, message = e.message, messageIsError = true) } }
            } else {
                // Успіх обробляє SessionRepository — застосунок сам перейде далі.
                session.signIn(s.email, s.password)
                    .onFailure { e -> _state.update { it.copy(busy = false, message = e.message, messageIsError = true) } }
                    .onSuccess { _state.update { it.copy(busy = false) } }
            }
        }
    }

    private companion object {
        const val MAX_EMAIL = 254
        const val MAX_PASSWORD = 128
    }
}
