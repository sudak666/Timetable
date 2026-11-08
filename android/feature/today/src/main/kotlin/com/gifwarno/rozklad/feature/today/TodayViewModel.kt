package com.gifwarno.rozklad.feature.today

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.gifwarno.rozklad.core.common.ticks
import com.gifwarno.rozklad.core.data.repository.ActiveFamily
import com.gifwarno.rozklad.core.domain.SchoolCalendar
import com.gifwarno.rozklad.core.model.FamilyContext
import com.gifwarno.rozklad.core.model.Member
import dagger.hilt.android.lifecycle.HiltViewModel
import java.time.Clock
import java.time.LocalDateTime
import javax.inject.Inject
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

sealed interface TodayScreenState {
    data object Loading : TodayScreenState

    /** У сім'ї ще немає дитини. */
    data object NoChild : TodayScreenState

    /** Розклад вибраної дитини ще не заповнено. */
    data class NoSchedule(val childName: String, val canEdit: Boolean, val children: List<Member>, val childId: String) :
        TodayScreenState

    data class Ready(val today: TodayUiState, val canEdit: Boolean, val children: List<Member>, val childId: String) :
        TodayScreenState
}

@HiltViewModel
class TodayViewModel @Inject constructor(private val active: ActiveFamily, clock: Clock) : ViewModel() {
    /** Тікає щосекунди лише поки екран видимий (WhileSubscribed) — батарея не страждає. */
    val uiState: StateFlow<TodayScreenState> = combine(active.context, clock.ticks()) { ctx, now -> build(ctx, now) }
        .distinctUntilChanged()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), TodayScreenState.Loading)

    private fun build(ctx: FamilyContext?, now: LocalDateTime): TodayScreenState {
        ctx ?: return TodayScreenState.Loading
        val child = ctx.child ?: return TodayScreenState.NoChild
        val schedule = ctx.schedule
        val today = schedule?.let { buildTodayState(now, SchoolCalendar(it)) }
        return if (today == null) {
            TodayScreenState.NoSchedule(child.name, ctx.canEditSchedule, ctx.data.children, child.userId)
        } else {
            TodayScreenState.Ready(today, ctx.canEditSchedule, ctx.data.children, child.userId)
        }
    }

    fun selectChild(id: String) {
        viewModelScope.launch { active.selectChild(id) }
    }

    private companion object {
        const val STOP_TIMEOUT_MS = 5_000L
    }
}
