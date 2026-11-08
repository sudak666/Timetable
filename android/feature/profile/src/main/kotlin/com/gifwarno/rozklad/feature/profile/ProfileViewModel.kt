package com.gifwarno.rozklad.feature.profile

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.gifwarno.rozklad.core.data.repository.ActiveFamily
import com.gifwarno.rozklad.core.data.repository.FamilyRepository
import com.gifwarno.rozklad.core.data.repository.SessionRepository
import com.gifwarno.rozklad.core.datastore.ThemeMode
import com.gifwarno.rozklad.core.datastore.UserPreferencesDataSource
import com.gifwarno.rozklad.core.model.Member
import com.gifwarno.rozklad.core.model.Role
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
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
        /** Що саме зникне, якщо видалити акаунт — показуємо чесно до підтвердження. */
        val deletionWarnings: List<String>,
        val pendingChanges: Int,
    ) : ProfileUiState
}

sealed interface DeleteAccountState {
    data object Idle : DeleteAccountState

    data object InProgress : DeleteAccountState

    data class Failed(val message: String) : DeleteAccountState
}

/** Слово, яке треба ввести, щоб підтвердити видалення (захист від випадкового натискання). */
internal const val DELETE_CONFIRM_WORD = "ВИДАЛИТИ"

/**
 * Наслідки видалення — та сама логіка, що й у school_delete_account() на сервері:
 * останній з батьків видаляє всю сім'ю, інакше видаляється лише свій акаунт і свої дані.
 */
internal fun deletionWarnings(isParent: Boolean, members: List<Member>, myId: String, familyName: String): List<String> {
    val otherParents = members.count { it.role == Role.PARENT && it.userId != myId }
    val children = members.filter { it.role == Role.CHILD }.map { it.name.ifBlank { "дитина" } }
    return buildList {
        add("Твій акаунт і вхід буде видалено назавжди. Відновити його неможливо.")
        when {
            !isParent -> add("Усі твої оцінки, бонуси, челенджі, домашку й розклад буде видалено.")

            otherParents > 0 -> add("Сім’я «$familyName» лишиться другому з батьків — їхні дані не зникнуть.")

            else -> {
                add("Ти — єдиний з батьків, тому сім’ю «$familyName» буде видалено повністю.")
                if (children.isNotEmpty()) {
                    add("Разом із нею зникнуть оцінки, гроші, домашка й розклад: ${children.joinToString()}.")
                    add("Акаунти дітей лишаться, але без сім’ї — їм треба буде приєднатися знову.")
                }
            }
        }
    }
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
            deletionWarnings = deletionWarnings(ctx.isParent, ctx.data.members, ctx.user.id, ctx.data.family.name),
            pendingChanges = ctx.sync.pendingChanges,
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

    private val _deleteState = MutableStateFlow<DeleteAccountState>(DeleteAccountState.Idle)
    val deleteState: StateFlow<DeleteAccountState> = _deleteState.asStateFlow()

    /** Безповоротно. Успіх → SessionRepository переведе застосунок на екран входу. */
    fun deleteAccount(confirmation: String) {
        if (confirmation.trim().uppercase() != DELETE_CONFIRM_WORD) return
        if (_deleteState.value == DeleteAccountState.InProgress) return
        _deleteState.value = DeleteAccountState.InProgress
        viewModelScope.launch {
            _deleteState.value = session.deleteAccount().fold(
                onSuccess = { DeleteAccountState.Idle },
                onFailure = { DeleteAccountState.Failed(it.message ?: "Не вдалося видалити акаунт") },
            )
        }
    }

    fun resetDeleteState() {
        if (_deleteState.value is DeleteAccountState.Failed) _deleteState.value = DeleteAccountState.Idle
    }

    private companion object {
        const val STOP_TIMEOUT_MS = 5_000L
    }
}
