package com.gifwarno.rozklad.feature.homework

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.gifwarno.rozklad.core.data.repository.ActiveFamily
import com.gifwarno.rozklad.core.data.repository.FamilyRepository
import com.gifwarno.rozklad.core.domain.SchoolCalendar
import com.gifwarno.rozklad.core.model.FamilyContext
import com.gifwarno.rozklad.core.model.Homework
import com.gifwarno.rozklad.core.model.Member
import dagger.hilt.android.lifecycle.HiltViewModel
import java.time.Clock
import java.time.LocalDate
import javax.inject.Inject
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

sealed interface HomeworkUiState {
    data object Loading : HomeworkUiState

    data object NoChild : HomeworkUiState

    data class Ready(
        val children: List<Member>,
        val childId: String,
        val overdue: List<Homework>,
        val upcoming: List<Homework>,
        val done: List<Homework>,
        val today: LocalDate,
    ) : HomeworkUiState
}

@HiltViewModel
class HomeworkViewModel @Inject constructor(
    private val active: ActiveFamily,
    private val families: FamilyRepository,
    private val clock: Clock,
) : ViewModel() {
    /** Розклад вибраної дитини — для автотерміну «до наступного уроку». */
    private var calendar: SchoolCalendar? = null

    val uiState: StateFlow<HomeworkUiState> = active.context
        .map { ctx -> ctx?.let(::build) ?: HomeworkUiState.Loading }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), HomeworkUiState.Loading)

    private fun build(ctx: FamilyContext): HomeworkUiState {
        val child = ctx.child ?: return HomeworkUiState.NoChild
        calendar = ctx.schedule?.let(::SchoolCalendar)
        val today = LocalDate.now(clock)
        val mine = ctx.data.homework.filter { it.childId == child.userId }
        val (done, open) = mine.partition { it.done }
        return HomeworkUiState.Ready(
            children = ctx.data.children,
            childId = child.userId,
            overdue = open.filter { it.due < today }.sortedBy { it.due },
            upcoming = open.filter { it.due >= today }.sortedBy { it.due },
            done = done.sortedByDescending { it.due }.take(DONE_LIMIT),
            today = today,
        )
    }

    /** Термін за замовчуванням — найближчий урок цього предмета. */
    fun suggestedDue(subject: String): LocalDate {
        val today = LocalDate.now(clock)
        return calendar?.nextLessonDate(subject, today) ?: today.plusDays(1)
    }

    private fun onContext(block: suspend (FamilyContext) -> Unit) {
        viewModelScope.launch { block(active.context.filterNotNull().first()) }
    }

    fun add(subject: String, due: LocalDate, text: String) = onContext { ctx ->
        val child = ctx.child ?: return@onContext
        if (text.isBlank()) return@onContext
        families.addHomework(ctx.familyId, child.userId, subject, due, text)
    }

    fun toggle(hw: Homework) = onContext { families.setHomeworkDone(it.familyId, hw.id, !hw.done) }

    fun delete(hw: Homework) = onContext { families.deleteHomework(it.familyId, hw.id) }

    fun selectChild(id: String) {
        viewModelScope.launch { active.selectChild(id) }
    }

    private companion object {
        const val STOP_TIMEOUT_MS = 5_000L
        const val DONE_LIMIT = 30
    }
}
