package com.gifwarno.rozklad

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.gifwarno.rozklad.core.data.repository.ActiveFamily
import com.gifwarno.rozklad.core.data.repository.FamilyRepository
import com.gifwarno.rozklad.core.data.repository.SessionRepository
import com.gifwarno.rozklad.core.datastore.ThemeMode
import com.gifwarno.rozklad.core.datastore.UserPreferencesDataSource
import com.gifwarno.rozklad.core.model.SessionState
import com.gifwarno.rozklad.core.model.SyncStatus
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filterIsInstance
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

@HiltViewModel
class MainActivityViewModel @Inject constructor(
    private val session: SessionRepository,
    private val families: FamilyRepository,
    active: ActiveFamily,
    preferences: UserPreferencesDataSource,
) : ViewModel() {
    val sessionState: StateFlow<SessionState> = session.session

    val themeMode: StateFlow<ThemeMode> = preferences.preferences
        .map { it.themeMode }
        .stateIn(viewModelScope, SharingStarted.Eagerly, ThemeMode.SYSTEM)

    val sync: StateFlow<SyncStatus> = active.context
        .map { it?.sync ?: SyncStatus(0, null) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), SyncStatus(0, null))

    /** Ще немає локальних даних сім'ї — показуємо індикатор, а не порожні екрани. */
    val hasFamilyData: StateFlow<Boolean> = active.context
        .map { it != null }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), false)

    init {
        // Нова сім'я (вхід/вступ) → одразу тягнемо дані.
        viewModelScope.launch {
            session.session.filterIsInstance<SessionState.Ready>().map { it.familyId }.distinctUntilChanged().collect {
                families.refresh(it)
            }
        }
    }

    /** Повернулися в застосунок — оновлюємо (замість постійного realtime-з'єднання, що їсть батарею). */
    fun refresh() {
        val s = session.session.value as? SessionState.Ready ?: return
        viewModelScope.launch { families.refresh(s.familyId) }
    }

    fun retrySession() = session.retry()

    fun completeGoogleSignIn(callbackUri: String) = session.completeGoogleSignIn(callbackUri)

    fun signOut() {
        viewModelScope.launch { session.signOut() }
    }

    fun dismissSyncError() = families.dismissSyncError()

    private companion object {
        const val STOP_TIMEOUT_MS = 5_000L
    }
}
