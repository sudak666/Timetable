package com.gifwarno.rozklad.feature.profile

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.gifwarno.rozklad.core.data.repository.ActiveFamily
import com.gifwarno.rozklad.core.data.repository.FamilyRepository
import com.gifwarno.rozklad.core.data.repository.SessionRepository
import com.gifwarno.rozklad.core.datastore.ThemeMode
import com.gifwarno.rozklad.core.datastore.UserPreferencesDataSource
import com.gifwarno.rozklad.core.designsystem.theme.GameStyle
import com.gifwarno.rozklad.core.domain.Rewards
import com.gifwarno.rozklad.core.domain.Trophies
import com.gifwarno.rozklad.core.model.FamilyContext
import com.gifwarno.rozklad.core.model.GradeStatus
import com.gifwarno.rozklad.core.model.Member
import com.gifwarno.rozklad.core.model.RewardSettings
import com.gifwarno.rozklad.core.model.Role
import com.gifwarno.rozklad.core.model.Streak
import com.gifwarno.rozklad.core.push.PushSettings
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
        /** Місце для повітряних тривог; null — не обрано. */
        val alertRegion: String? = null,
        /** Рівень для відкриття аватарів і кольорів (батькам — усе). */
        val level: Int = Trophies.PARENT_LEVEL,
        /** Обраний колір «#rrggbb» або порожньо. */
        val accent: String = "",
        /** Курс оцінок і правила серії сім'ї. */
        val settings: RewardSettings = Rewards.DEFAULT_SETTINGS,
        val gameStyle: GameStyle = GameStyle.ARENA,
        /** Ім'я в сім'ї як є (може бути порожнім — тоді в шапці пошта). */
        val myName: String = "",
        /** Свій userId — себе прибрати з сім'ї не можна. */
        val myId: String = "",
    ) : ProfileUiState
}

data class FamilyActionState(val busy: Boolean = false, val error: String? = null)

/** [alerts] — push про тривоги на цей телефон (працює лише разом з [enabled]). */
data class PushUiState(val enabled: Boolean, val error: String?, val alerts: Boolean = false)

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
    private val push: PushSettings,
) : ViewModel() {
    /** Перемикач сповіщень: null — збірка без Firebase, перемикач не показуємо. */
    val pushState: StateFlow<PushUiState?> = if (!push.isAvailable) {
        MutableStateFlow(null)
    } else {
        combine(push.enabled, push.lastError, push.alertsEnabled) { on, err, alerts -> PushUiState(on, err, alerts) }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), PushUiState(false, null))
    }

    fun needsNotificationPermission(): Boolean = !push.hasPermission()

    fun setPushEnabled(enabled: Boolean) {
        viewModelScope.launch { push.setEnabled(enabled) }
    }

    /** Тривоги без загальних сповіщень не працюють — тож вмикаємо їх разом. */
    fun setAlertsPushEnabled(enabled: Boolean) {
        viewModelScope.launch {
            push.setAlertsEnabled(enabled)
            if (enabled) push.setEnabled(true)
        }
    }

    private val _alertError = MutableStateFlow<String?>(null)
    val alertError: StateFlow<String?> = _alertError.asStateFlow()

    /** Вимкнути тривоги для всієї сім'ї (лише батьки). */
    fun clearAlertRegion() {
        viewModelScope.launch {
            val ctx = active.context.filterNotNull().first()
            _alertError.value = families.setAlertRegion(ctx.familyId, null).exceptionOrNull()?.let {
                it.message ?: "Не вдалося — перевір інтернет"
            }
        }
    }

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
            alertRegion = ctx.data.airAlert?.regionName,
            level = if (ctx.isParent) {
                Trophies.PARENT_LEVEL
            } else {
                Rewards.levelOf(
                    Rewards.xpOf(ctx.data.grades.filter { it.childId == ctx.user.id && it.status == GradeStatus.APPROVED }),
                )
            },
            accent = ctx.me?.theme.orEmpty(),
            settings = ctx.data.settings,
            gameStyle = GameStyle.of(prefs.gameStyle),
            myName = ctx.me?.name.orEmpty(),
            myId = ctx.user.id,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), ProfileUiState.Loading)

    fun loadParentCode() {
        viewModelScope.launch {
            val ctx = active.context.filterNotNull().first()
            if (ctx.isParent) parentCode.value = families.parentInviteCode(ctx.familyId).getOrNull() ?: "—"
        }
    }

    private val _familyAction = MutableStateFlow(FamilyActionState())

    /** Зміна коду й видалення учасника — потрібна мережа, тож показуємо «зачекай» і помилку. */
    val familyAction: StateFlow<FamilyActionState> = _familyAction.asStateFlow()

    /** Новий код для дитини: старий (можливо, злитий) перестає працювати, учасники лишаються. */
    fun rotateInviteCode() = familyAction { ctx -> families.rotateInviteCode(ctx.familyId) }

    /** Прибрати учасника (напр., чужого, що увійшов за злитим кодом). Себе — не можна. */
    fun removeMember(userId: String) = familyAction { ctx ->
        if (userId == ctx.user.id) Result.success(Unit) else families.removeMember(ctx.familyId, userId)
    }

    private fun familyAction(action: suspend (FamilyContext) -> Result<Unit>) {
        if (_familyAction.value.busy) return
        _familyAction.value = FamilyActionState(busy = true)
        viewModelScope.launch {
            val ctx = active.context.filterNotNull().first()
            val error = if (ctx.isParent) action(ctx).exceptionOrNull()?.let { it.message ?: "Не вдалося — перевір інтернет" } else null
            _familyAction.value = FamilyActionState(error = error)
        }
    }

    /** Свій аватар і/або колір (як у вебі: school_members.avatar / theme). */
    fun setLook(avatar: String? = null, accent: String? = null) {
        viewModelScope.launch {
            val ctx = active.context.filterNotNull().first()
            families.setMyLook(ctx.familyId, ctx.user.id, avatar, accent)
        }
    }

    /** Своє ім'я; назву сім'ї ([family]) можуть змінити лише батьки — так само обмежує сервер. */
    fun rename(name: String, family: String?) {
        viewModelScope.launch {
            val ctx = active.context.filterNotNull().first()
            if (name.isNotBlank() && name.trim() != ctx.me?.name) families.setMyName(ctx.familyId, ctx.user.id, name)
            if (ctx.isParent && !family.isNullOrBlank() &&
                family.trim() != ctx.data.family.name
            ) {
                families.renameFamily(ctx.familyId, family)
            }
        }
    }

    /** Курс і серія — лише батьки (так само обмежує сервер). */
    fun saveRates(rates: Map<Int, Int>, streak: Streak) {
        viewModelScope.launch {
            val ctx = active.context.filterNotNull().first()
            if (ctx.isParent) families.saveRates(ctx.familyId, rates, streak)
        }
    }

    fun setGameStyle(style: GameStyle) {
        viewModelScope.launch { preferences.setGameStyle(style.name) }
    }

    fun setTheme(mode: ThemeMode) {
        viewModelScope.launch { preferences.setThemeMode(mode) }
    }

    fun signOut() {
        viewModelScope.launch { session.signOut() }
    }

    private val _passwordState = MutableStateFlow<PasswordState>(PasswordState.Idle)
    val passwordState: StateFlow<PasswordState> = _passwordState.asStateFlow()

    fun changePassword(password: String) {
        if (_passwordState.value == PasswordState.Saving || password.length < MIN_PASSWORD) return
        _passwordState.value = PasswordState.Saving
        viewModelScope.launch {
            _passwordState.value = session.changePassword(password).fold(
                onSuccess = { PasswordState.Done },
                onFailure = { e ->
                    val m = e.message.orEmpty()
                    PasswordState.Failed(
                        if (m.contains("different from the old", ignoreCase = true)) "Новий пароль має відрізнятися від старого" else m,
                    )
                },
            )
        }
    }

    fun resetPasswordState() {
        _passwordState.value = PasswordState.Idle
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
