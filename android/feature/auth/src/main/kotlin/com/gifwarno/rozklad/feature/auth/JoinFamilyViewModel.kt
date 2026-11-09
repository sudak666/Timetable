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

data class JoinUiState(val name: String = "", val code: String = "", val busy: Boolean = false, val error: String? = null) {
    val canCreate: Boolean get() = !busy && name.isNotBlank()
    val canJoin: Boolean get() = !busy && name.isNotBlank() && code.length >= MIN_CODE

    companion object {
        const val MIN_CODE = 4
    }
}

@HiltViewModel
class JoinFamilyViewModel @Inject constructor(private val session: SessionRepository) : ViewModel() {
    private val _state = MutableStateFlow(JoinUiState())
    val state: StateFlow<JoinUiState> = _state.asStateFlow()

    fun onName(v: String) = _state.update { it.copy(name = v.take(MAX_NAME), error = null) }

    fun onCode(v: String) = _state.update { it.copy(code = v.filter(Char::isLetterOrDigit).uppercase().take(MAX_CODE), error = null) }

    fun create() = run { session.createFamily(_state.value.name) }

    fun join() = run { session.joinFamily(_state.value.code, _state.value.name) }

    fun signOut() {
        viewModelScope.launch { session.signOut() }
    }

    private fun run(action: suspend () -> Result<Unit>) {
        if (_state.value.busy) return
        _state.update { it.copy(busy = true, error = null) }
        viewModelScope.launch {
            val r = action()
            _state.update { it.copy(busy = false, error = r.exceptionOrNull()?.message) }
        }
    }

    private companion object {
        const val MAX_NAME = 40
        const val MAX_CODE = 12
    }
}
