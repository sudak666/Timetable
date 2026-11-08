package com.gifwarno.rozklad.feature.profile

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.gifwarno.rozklad.core.data.repository.ActiveFamily
import com.gifwarno.rozklad.core.data.repository.FamilyRepository
import com.gifwarno.rozklad.core.data.repository.SessionRepository
import com.gifwarno.rozklad.core.datastore.ThemeMode
import com.gifwarno.rozklad.core.datastore.UserPreferencesDataSource
import com.gifwarno.rozklad.core.model.Member
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

sealed interface ProfileUiState {
    data object Loading : ProfileUiState

    data class Ready(
        val name: String,
        val email: String,
        val avatar: String,
        val isParent: Boolean,
        val familyName: String,
        /** Код для дитини (вступ до сім'ї). */
        val childInviteCode: String,
        /** Код для другого батька — завантажується на вимогу. */
        val parentInviteCode: String?,
        val members: List<Member>,
        val themeMode: ThemeMode,
    ) : ProfileUiState
}

@HiltViewModel
class ProfileViewModel @Inject constructor(
    private val active: ActiveFamily,
    private val families: FamilyRepository,
    private val session: SessionRepository,
    private val preferences: UserPreferencesDataSource,
) : ViewModel() {
    private val parentCode = MutableStateFlow<String?>(null)

    val uiState: StateFlow<ProfileUiState> = combine(active.context, preferences.preferences, parentCode) { ctx, prefs, code ->
        ctx ?: return@combine ProfileUiState.Loading
        ProfileUiState.Ready(
            name = ctx.me?.name?.ifBlank { null } ?: ctx.user.fullName.ifBlank { ctx.user.email },
            email = ctx.user.email,
            avatar = ctx.me?.avatar?.ifBlank { null } ?: if (ctx.isParent) "🦉" else "🦊",
            isParent = ctx.isParent,
            familyName = ctx.data.family.name,
            childInviteCode = ctx.data.family.inviteCode,
            parentInviteCode = code,
            members = ctx.data.members,
            themeMode = prefs.themeMode,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), ProfileUiState.Loading)

    fun loadParentCode() {
        viewModelScope.launch {
            val ctx = active.context.filterNotNull().first()
            if (ctx.isParent) parentCode.value = families.parentInviteCode(ctx.familyId).getOrNull() ?: "—"
        }
    }

    fun setTheme(mode: ThemeMode) {
        viewModelScope.launch { preferences.setThemeMode(mode) }
    }

    fun signOut() {
        viewModelScope.launch { session.signOut() }
    }

    private companion object {
        const val STOP_TIMEOUT_MS = 5_000L
    }
}
