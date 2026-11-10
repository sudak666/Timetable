package com.gifwarno.rozklad.feature.grades

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.gifwarno.rozklad.core.data.repository.ActiveFamily
import com.gifwarno.rozklad.core.data.repository.FamilyRepository
import com.gifwarno.rozklad.core.domain.Badge
import com.gifwarno.rozklad.core.domain.Rewards
import com.gifwarno.rozklad.core.domain.RoadStep
import com.gifwarno.rozklad.core.domain.SchoolCalendar
import com.gifwarno.rozklad.core.domain.Totals
import com.gifwarno.rozklad.core.domain.Trophies
import com.gifwarno.rozklad.core.model.FamilyContext
import com.gifwarno.rozklad.core.model.Grade
import com.gifwarno.rozklad.core.model.GradeStatus
import com.gifwarno.rozklad.core.model.LedgerKind
import com.gifwarno.rozklad.core.model.Member
import com.gifwarno.rozklad.core.model.Streak
import dagger.hilt.android.lifecycle.HiltViewModel
import java.time.Clock
import java.time.LocalDate
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** Челендж з прогресом (як у вебі: зараховані оцінки в межах дат, ≥ мінімальної, за предметом). */
data class ChallengeItem(
    val id: String,
    val title: String,
    val subject: String?,
    val minGrade: Int,
    val need: Int,
    val hits: Int,
    val reward: Int,
    val endDate: LocalDate,
    val done: Boolean,
    val over: Boolean,
)

data class GradeItem(val grade: Grade, val money: Int, val canApprove: Boolean, val canDelete: Boolean)

sealed interface GradesUiState {
    data object Loading : GradesUiState

    data class NoChild(val isParent: Boolean, val inviteCode: String) : GradesUiState

    data class Ready(
        val isParent: Boolean,
        val children: List<Member>,
        val child: Member,
        val totals: Totals,
        val level: Int,
        val xpInLevel: Int,
        val run: Int,
        val runTarget: Int,
        val average: Double?,
        val pending: List<GradeItem>,
        val recent: List<GradeItem>,
        val rates: Map<Int, Int>,
        /** Шлях нагород за рівнями й досягнення — як у вебі. */
        val road: List<RoadStep> = emptyList(),
        val challenges: List<ChallengeItem> = emptyList(),
        val streak: Streak = Rewards.DEFAULT_STREAK,
        /** Оцінки, бонуси, штрафи, виплати й автонагороди разом — свіжі зверху. */
        val history: List<HistoryEntry> = emptyList(),
        /** Одна дитина — порівняння з минулим місяцем; кілька — рейтинг місяця. */
        val compare: MonthCompare? = null,
        val rank: List<RankRow> = emptyList(),
        val subjectAvgs: List<SubjectAvg> = emptyList(),
        val badges: List<Badge> = emptyList(),
    ) : GradesUiState
}

@HiltViewModel
class GradesViewModel @Inject constructor(
    private val active: ActiveFamily,
    private val families: FamilyRepository,
    private val clock: Clock,
) : ViewModel() {
    val uiState: StateFlow<GradesUiState> = active.context
        .map { ctx -> ctx?.let(::build) ?: GradesUiState.Loading }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), GradesUiState.Loading)

    private val _message = MutableStateFlow<String?>(null)
    val message: StateFlow<String?> = _message.asStateFlow()

    private fun build(ctx: FamilyContext): GradesUiState {
        val child = ctx.child ?: return GradesUiState.NoChild(ctx.isParent, ctx.data.family.inviteCode)
        val today = LocalDate.now(clock)
        val rates = ctx.data.settings.rates
        val all = ctx.data.grades.filter { it.childId == child.userId }
        val approved = all.filter { it.status == GradeStatus.APPROVED }
        val ledger = ctx.data.ledger.filter { it.childId == child.userId }
        val challenges = ctx.data.challenges.filter { it.childId == child.userId }
        val auto = Rewards.autoRewards(approved, challenges, ctx.data.settings.streak)
        val xp = Rewards.xpOf(approved)
        val scored = approved.filter(Rewards::isScored)

        fun item(g: Grade) = GradeItem(
            grade = g,
            money = Rewards.gradeValue(g, rates),
            canApprove = ctx.isParent && g.status == GradeStatus.PENDING,
            canDelete = ctx.isParent || g.status == GradeStatus.PENDING,
        )
        val totals = Rewards.totals(approved, ledger, auto, rates, today, SchoolCalendar.mondayOf(today))
        val level = Rewards.levelOf(xp)
        return GradesUiState.Ready(
            isParent = ctx.isParent,
            children = ctx.data.children,
            child = child,
            totals = totals,
            level = level,
            xpInLevel = xp % Rewards.XP_PER_LEVEL,
            run = Rewards.currentRun(approved, ctx.data.settings.streak),
            runTarget = ctx.data.settings.streak.len,
            average = scored.takeIf { it.isNotEmpty() }?.map { it.value }?.average(),
            pending = all.filter { it.status == GradeStatus.PENDING }.map(::item),
            recent = approved.take(RECENT_LIMIT).map(::item),
            rates = rates,
            road = Trophies.road(level),
            challenges = challenges.sortedByDescending { it.endDate }.map { c ->
                val hits = Rewards.challengeHits(c, approved.sortedWith(Rewards.byTime)).size
                ChallengeItem(
                    id = c.id,
                    title = c.title,
                    subject = c.subject,
                    minGrade = c.minGrade,
                    need = c.need,
                    hits = minOf(hits, c.need),
                    reward = c.reward,
                    endDate = c.endDate,
                    done = hits >= c.need,
                    over = today > c.endDate,
                )
            },
            streak = ctx.data.settings.streak,
            history = GradesStats.history(approved.map(::item), ledger, auto, ctx.isParent),
            compare = if (ctx.data.children.size > 1) null else GradesStats.compare(child.userId, approved, ctx.data, today),
            rank = if (ctx.data.children.size > 1) GradesStats.rank(ctx.data, today) else emptyList(),
            subjectAvgs = GradesStats.subjectAverages(approved),
            badges = Trophies.badges(approved, totals.earned, rates, SchoolCalendar.mondayOf(today)),
        )
    }

    private fun onContext(block: suspend (FamilyContext) -> Unit) {
        viewModelScope.launch { block(active.context.filterNotNull().first()) }
    }

    fun addGrade(subject: String, value: Int, date: LocalDate) = onContext { ctx ->
        val child = ctx.child ?: return@onContext
        families.addGrade(ctx.familyId, child.userId, subject, value, date, byParent = ctx.isParent)
        _message.value = if (ctx.isParent) "Оцінку додано ✓" else "Оцінку надіслано батькам на підтвердження"
    }

    fun approve(grade: Grade) = onContext { families.approveGrade(it.familyId, grade) }

    fun unapprove(grade: Grade) = onContext { families.unapproveGrade(it.familyId, grade.id) }

    fun delete(grade: Grade) = onContext { families.deleteGrade(it.familyId, grade.id) }

    fun selectChild(id: String) {
        viewModelScope.launch { active.selectChild(id) }
    }

    /** Штраф — від'ємний бонус (як «мінус = штраф» у вебі). */
    fun addFine(amount: Int, note: String) = onContext { ctx ->
        val child = ctx.child ?: return@onContext
        if (!ctx.isParent || amount <= 0) return@onContext
        families.addLedger(ctx.familyId, child.userId, LedgerKind.BONUS, -amount, note.ifBlank { "Штраф" })
        _message.value = "Штраф записано"
    }

    fun addChallenge(draft: ChallengeDraft) = onContext { ctx ->
        val child = ctx.child ?: return@onContext
        if (!ctx.isParent || draft.title.isBlank()) return@onContext
        families.addChallenge(
            ctx.familyId,
            child.userId,
            draft.title,
            draft.subject,
            draft.minGrade.coerceIn(1, MAX_GRADE),
            draft.need.coerceIn(1, MAX_NEED),
            draft.days.coerceIn(1, MAX_DAYS),
            draft.reward.coerceIn(0, MAX_REWARD),
        )
        _message.value = "Челендж створено 🏅"
    }

    fun deleteLedger(id: String) = onContext { ctx ->
        if (ctx.isParent) families.deleteLedger(ctx.familyId, id)
    }

    fun setGoal(title: String, amount: Int) = onContext { ctx ->
        val child = ctx.child ?: return@onContext
        families.setGoal(ctx.familyId, child.userId, title, amount)
    }

    fun deleteChallenge(id: String) = onContext { ctx ->
        if (ctx.isParent) families.deleteChallenge(ctx.familyId, id)
    }

    fun addLedger(kind: LedgerKind, amount: Int, note: String) = onContext { ctx ->
        val child = ctx.child ?: return@onContext
        if (amount <= 0) return@onContext
        families.addLedger(ctx.familyId, child.userId, kind, amount, note.ifBlank { if (kind == LedgerKind.PAYOUT) "Виплачено" else "" })
        _message.value = if (kind == LedgerKind.PAYOUT) "Виплату записано" else "Бонус нараховано 🎁"
    }

    fun messageShown() {
        _message.value = null
    }

    private companion object {
        const val STOP_TIMEOUT_MS = 5_000L
        const val RECENT_LIMIT = 100
        const val MAX_GRADE = 12
        const val MAX_NEED = 50
        const val MAX_DAYS = 90
        const val MAX_REWARD = 100_000
    }
}

/** Форма нового челенджу (значення за замовчуванням — як у вебі). */
data class ChallengeDraft(
    val title: String = "",
    val subject: String? = null,
    val minGrade: Int = 10,
    val need: Int = 3,
    val days: Int = 7,
    val reward: Int = 100,
)
