package com.gifwarno.rozklad.feature.today

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.gifwarno.rozklad.core.common.ticks
import com.gifwarno.rozklad.core.data.repository.ActiveFamily
import com.gifwarno.rozklad.core.domain.SchoolCalendar
import com.gifwarno.rozklad.core.model.FamilyContext
import com.gifwarno.rozklad.core.model.Member
import com.gifwarno.rozklad.core.model.SchoolSchedule
import dagger.hilt.android.lifecycle.HiltViewModel
import java.time.Clock
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import javax.inject.Inject
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.shareIn
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
    private val ticks = clock.ticks().shareIn(viewModelScope, SharingStarted.WhileSubscribed(), replay = 1)

    /**
     * Важке (гравець і нагороди з усіх оцінок, квести, тиждень) — лише коли змінилися дані або настав
     * новий день, а не щосекунди разом із таймером уроку.
     */
    private val daily = combine(active.context, ticks.map { it.toLocalDate() }.distinctUntilChanged(), ::daily)

    val uiState: StateFlow<TodayScreenState> = combine(daily, ticks, ::build)
        .distinctUntilChanged()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), TodayScreenState.Loading)

    /** Частини екрана, що залежать лише від даних і дати. */
    private sealed interface Daily {
        data class Done(val state: TodayScreenState) : Daily

        class Parts(val ctx: FamilyContext, val child: Member, val alertSince: LocalDateTime?, val day: DayParts?) : Daily
    }

    private class DayParts(
        val schedule: SchoolSchedule,
        val cal: SchoolCalendar,
        val player: PlayerStrip,
        val quests: List<Quest>,
        val week: WeekSchedule?,
    )

    private fun daily(ctx: FamilyContext?, date: LocalDate): Daily {
        ctx ?: return Daily.Done(TodayScreenState.Loading)
        val child = ctx.child ?: return Daily.Done(TodayScreenState.NoChild(ctx.isParent, ctx.data.family.inviteCode))
        val alertSince = ctx.data.airAlert?.takeIf { it.isActive }?.since?.let { alertLocal(it, clock.zone) }
        val day = ctx.schedule?.let { schedule ->
            val cal = SchoolCalendar(schedule)
            DayParts(
                schedule = schedule,
                cal = cal,
                player = buildPlayer(child, ctx.data, date),
                quests = buildQuests(child.userId, ctx.data, cal, date),
                week = buildWeek(schedule, date),
            )
        }
        return Daily.Parts(ctx, child, alertSince, day)
    }

    private fun build(d: Daily, now: LocalDateTime): TodayScreenState {
        if (d is Daily.Done) return d.state
        d as Daily.Parts
        val day = d.day
        val today = day?.let { buildTodayState(now, it.cal, d.alertSince) }
        return if (day == null || today == null) {
            TodayScreenState.NoSchedule(d.child.name, d.ctx.canEditSchedule, d.ctx.data.children, d.child.userId)
        } else {
            TodayScreenState.Ready(
                today = today,
                canEdit = d.ctx.canEditSchedule,
                children = d.ctx.data.children,
                childId = d.child.userId,
                player = day.player,
                quests = day.quests,
                week = day.week,
                weekend = weekendText(now, day.cal, day.schedule.week.getOrNull(FRIDAY_INDEX)?.size ?: 0),
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
