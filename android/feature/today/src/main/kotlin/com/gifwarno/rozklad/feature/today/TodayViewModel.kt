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
import java.time.ZoneId
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
    data class NoChild(val isParent: Boolean, val inviteCode: String) : TodayScreenState

    /** Розклад вибраної дитини ще не заповнено. */
    data class NoSchedule(val childName: String, val canEdit: Boolean, val children: List<Member>, val childId: String) :
        TodayScreenState

    data class Ready(
        val today: TodayUiState,
        val canEdit: Boolean,
        val children: List<Member>,
        val childId: String,
        val player: PlayerStrip,
        val quests: List<Quest>,
        val week: WeekSchedule? = null,
        /** Текст «до вихідних» (оновлюється разом із таймером). */
        val weekend: String = "",
    ) : TodayScreenState
}

@HiltViewModel
class TodayViewModel @Inject constructor(private val active: ActiveFamily, private val clock: Clock) : ViewModel() {
    /** Тікає щосекунди лише поки екран видимий (WhileSubscribed) — батарея не страждає. */
    val uiState: StateFlow<TodayScreenState> = combine(active.context, clock.ticks()) { ctx, now -> build(ctx, now) }
        .distinctUntilChanged()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), TodayScreenState.Loading)

    private fun build(ctx: FamilyContext?, now: LocalDateTime): TodayScreenState {
        ctx ?: return TodayScreenState.Loading
        val child = ctx.child ?: return TodayScreenState.NoChild(ctx.isParent, ctx.data.family.inviteCode)
        val schedule = ctx.schedule
        val alertSince = ctx.data.airAlert?.takeIf { it.isActive }?.since?.let { alertLocal(it, clock.zone) }
        val cal = schedule?.let(::SchoolCalendar)
        val today = cal?.let { buildTodayState(now, it, alertSince) }
        // today ≠ null ⇒ cal і schedule теж не null (компілятор це знає — smart cast).
        return if (today == null) {
            TodayScreenState.NoSchedule(child.name, ctx.canEditSchedule, ctx.data.children, child.userId)
        } else {
            TodayScreenState.Ready(
                today = today,
                canEdit = ctx.canEditSchedule,
                children = ctx.data.children,
                childId = child.userId,
                player = buildPlayer(child, ctx.data, now.toLocalDate()),
                quests = buildQuests(child.userId, ctx.data, cal, now.toLocalDate()),
                week = buildWeek(schedule, now.toLocalDate()),
                weekend = weekendText(now, cal, schedule.week.getOrNull(FRIDAY_INDEX)?.size ?: 0),
            )
        }
    }

    fun selectChild(id: String) {
        viewModelScope.launch { active.selectChild(id) }
    }

    private companion object {
        const val STOP_TIMEOUT_MS = 5_000L
        const val FRIDAY_INDEX = 4

        fun alertLocal(since: java.time.Instant, zone: ZoneId): LocalDateTime = LocalDateTime.ofInstant(since, zone)
    }
}
