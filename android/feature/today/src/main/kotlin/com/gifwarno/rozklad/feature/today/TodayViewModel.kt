package com.gifwarno.rozklad.feature.today

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.gifwarno.rozklad.core.common.ticks
import com.gifwarno.rozklad.core.data.repository.ActiveFamily
import com.gifwarno.rozklad.core.data.repository.FamilyRepository
import com.gifwarno.rozklad.core.domain.Rewards
import com.gifwarno.rozklad.core.domain.SchoolCalendar
import com.gifwarno.rozklad.core.model.FamilyContext
import com.gifwarno.rozklad.core.model.LedgerKind
import com.gifwarno.rozklad.core.model.LedgerSource
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
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
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
        /** Дата за годинником застосунку (у демо-режимі — підмінена), а не системна. */
        val date: LocalDate,
        val canEdit: Boolean,
        val children: List<Member>,
        val childId: String,
        val player: PlayerStrip,
        val quests: List<Quest>,
        val week: WeekSchedule? = null,
        /** Текст «до вихідних» (оновлюється разом із таймером). */
        val weekend: String = "",
        /** Зірки за поведінку вибраної дитини сьогодні. */
        val stars: StarsStrip = StarsStrip(),
    ) : TodayScreenState
}

/**
 * Зірки за поведінку за сьогодні: ставлять лише батьки ([canGive]), кожна — бонус [price] ₴ у гаманці.
 * [lastId] — остання сьогоднішня зірка (батьки можуть прибрати помилкову).
 */
data class StarsStrip(
    val count: Int = 0,
    val sum: Int = 0,
    val price: Int = 0,
    val canGive: Boolean = false,
    val childName: String = "",
    val lastId: String? = null,
) {
    val full: Boolean get() = count >= Rewards.STARS_PER_DAY
}

@HiltViewModel
class TodayViewModel @Inject constructor(
    private val active: ActiveFamily,
    private val families: FamilyRepository,
    private val clock: Clock,
) : ViewModel() {
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

        class Parts(val ctx: FamilyContext, val child: Member, val alertSince: LocalDateTime?, val day: DayParts?, val stars: StarsStrip) :
            Daily
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
        return Daily.Parts(ctx, child, alertSince, day, buildStars(ctx, child, date))
    }

    private fun build(d: Daily, now: LocalDateTime): TodayScreenState {
        if (d is Daily.Done) return d.state
        d as Daily.Parts
        val day = d.day
        val today = day?.let { buildTodayState(now, it.cal, d.alertSince, it.schedule.clubs) }
        return if (day == null || today == null) {
            TodayScreenState.NoSchedule(d.child.name, d.ctx.canEditSchedule, d.ctx.data.children, d.child.userId)
        } else {
            TodayScreenState.Ready(
                today = today,
                date = now.toLocalDate(),
                canEdit = d.ctx.canEditSchedule,
                children = d.ctx.data.children,
                childId = d.child.userId,
                player = day.player,
                quests = day.quests,
                week = day.week,
                weekend = weekendText(now, day.cal, day.schedule.week.getOrNull(FRIDAY_INDEX)?.size ?: 0),
                stars = d.stars,
            )
        }
    }

    fun selectChild(id: String) {
        viewModelScope.launch { active.selectChild(id) }
    }

    /** Зірка за поведінку вибраній дитині — лише батьки, не більше [Rewards.STARS_PER_DAY] за день. */
    fun giveStar() {
        viewModelScope.launch {
            val ctx = active.context.filterNotNull().first()
            val child = ctx.child ?: return@launch
            val price = ctx.data.settings.prices.star
            val today = LocalDate.now(clock)
            if (!ctx.isParent || price <= 0 ||
                Rewards.starsOn(ctx.data.ledger, child.userId, today).size >= Rewards.STARS_PER_DAY
            ) {
                return@launch
            }
            families.addLedger(ctx.familyId, child.userId, LedgerKind.BONUS, price, STAR_NOTE, LedgerSource.STAR)
        }
    }

    /** Прибрати помилкову зірку (лише батьки — так дозволяє сервер). */
    fun takeStar(id: String) {
        viewModelScope.launch {
            val ctx = active.context.filterNotNull().first()
            if (ctx.isParent) families.deleteLedger(ctx.familyId, id)
        }
    }

    private fun buildStars(ctx: FamilyContext, child: Member, date: LocalDate): StarsStrip {
        val stars = Rewards.starsOn(ctx.data.ledger, child.userId, date)
        return StarsStrip(
            count = stars.size,
            sum = stars.sumOf { it.amount },
            price = ctx.data.settings.prices.star,
            canGive = ctx.isParent,
            childName = child.name,
            lastId = stars.lastOrNull()?.id,
        )
    }

    private companion object {
        const val STOP_TIMEOUT_MS = 5_000L
        const val FRIDAY_INDEX = 4
        const val STAR_NOTE = "⭐ За поведінку"

        fun alertLocal(since: java.time.Instant, zone: ZoneId): LocalDateTime = LocalDateTime.ofInstant(since, zone)
    }
}
