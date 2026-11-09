package com.gifwarno.rozklad.feature.schedule

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.gifwarno.rozklad.core.data.repository.ActiveFamily
import com.gifwarno.rozklad.core.data.repository.FamilyRepository
import com.gifwarno.rozklad.core.domain.ScheduleValidator
import com.gifwarno.rozklad.core.domain.Timetable
import com.gifwarno.rozklad.core.model.Bell
import com.gifwarno.rozklad.core.model.Holiday
import com.gifwarno.rozklad.core.model.Lesson
import com.gifwarno.rozklad.core.model.SchoolSchedule
import dagger.hilt.android.lifecycle.HiltViewModel
import java.time.LocalDate
import java.time.LocalTime
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

sealed interface ScheduleEditorState {
    data object Loading : ScheduleEditorState

    data object NotAllowed : ScheduleEditorState

    data class Editing(
        val childName: String,
        val draft: SchoolSchedule,
        val day: Int = 0,
        val dirty: Boolean = false,
        val errors: List<String> = emptyList(),
        val saved: Boolean = false,
    ) : ScheduleEditorState {
        val lessons: List<Lesson> get() = draft.week.getOrElse(day) { emptyList() }
        val canAddLesson: Boolean get() = lessons.size < draft.bells.size
    }
}

/**
 * Чернетка розкладу живе тут, на сервер іде лише після «Зберегти».
 * Усі зміни — чисті перетворення [SchoolSchedule], тому їх легко тестувати.
 */
@HiltViewModel
class ScheduleEditorViewModel @Inject constructor(private val active: ActiveFamily, private val families: FamilyRepository) : ViewModel() {
    private val _state = MutableStateFlow<ScheduleEditorState>(ScheduleEditorState.Loading)
    val state: StateFlow<ScheduleEditorState> = _state.asStateFlow()

    init {
        viewModelScope.launch {
            val ctx = active.context.filterNotNull().first()
            val child = ctx.child
            _state.value = if (child == null || !ctx.canEditSchedule) {
                ScheduleEditorState.NotAllowed
            } else {
                ScheduleEditorState.Editing(child.name, ctx.schedule ?: Timetable.BLANK)
            }
        }
    }

    private fun edit(block: (SchoolSchedule, Int) -> SchoolSchedule) = _state.update { s ->
        if (s !is ScheduleEditorState.Editing) return@update s
        s.copy(draft = block(s.draft, s.day), dirty = true, errors = emptyList())
    }

    private fun SchoolSchedule.withDay(day: Int, f: (List<Lesson>) -> List<Lesson>) =
        copy(week = week.mapIndexed { i, l -> if (i == day) f(l) else l })

    fun selectDay(day: Int) = _state.update { s -> if (s is ScheduleEditorState.Editing) s.copy(day = day) else s }

    fun addLesson(subject: String) = edit { s, d ->
        if (s.week[d].size >= s.bells.size) s else s.withDay(d) { it + Lesson(subject.trim().take(SchoolSchedule.MAX_SUBJECT)) }
    }

    fun setLesson(index: Int, subject: String) = edit { s, d ->
        s.withDay(d) { l ->
            l.mapIndexed { i, x ->
                if (i ==
                    index
                ) {
                    x.copy(subject = subject.trim().take(SchoolSchedule.MAX_SUBJECT))
                } else {
                    x
                }
            }
        }
    }

    fun toggleOptional(index: Int) = edit { s, d ->
        s.withDay(d) { l -> l.mapIndexed { i, x -> if (i == index) x.copy(optional = !x.optional) else x } }
    }

    fun removeLesson(index: Int) = edit { s, d -> s.withDay(d) { l -> l.filterIndexed { i, _ -> i != index } } }

    fun moveLesson(index: Int, delta: Int) = edit { s, d ->
        s.withDay(d) { l ->
            val to = index + delta
            if (to !in l.indices) l else l.toMutableList().apply { add(to, removeAt(index)) }
        }
    }

    /** Скопіювати уроки іншого дня в поточний (часто дні схожі). */
    fun copyFrom(fromDay: Int) = edit { s, d -> s.withDay(d) { s.week[fromDay].take(s.bells.size) } }

    /** 5 або 6 навчальних днів. Уроки суботи зберігаються, поки не збережено. */
    fun setSaturday(enabled: Boolean) = edit { s, _ ->
        when {
            enabled && s.week.size < SATURDAY_DAYS -> s.copy(week = s.week + listOf(emptyList()))
            !enabled && s.week.size >= SATURDAY_DAYS -> s.copy(week = s.week.take(SchoolSchedule.MIN_DAYS))
            else -> s
        }
    }.also { _state.update { s -> if (s is ScheduleEditorState.Editing && s.day >= s.draft.week.size) s.copy(day = 0) else s } }

    fun setBell(index: Int, start: LocalTime, end: LocalTime) = edit { s, _ ->
        s.copy(bells = s.bells.mapIndexed { i, b -> if (i == index) Bell(start, end) else b })
    }

    /** Новий дзвінок: через 10 хв після останнього, 45 хв. */
    fun addBell() = edit { s, _ ->
        if (s.bells.size >= SchoolSchedule.MAX_LESSONS) return@edit s
        val start = s.bells.lastOrNull()?.end?.plusMinutes(BREAK_MINUTES) ?: LocalTime.of(DEFAULT_START_HOUR, DEFAULT_START_MINUTE)
        s.copy(bells = s.bells + Bell(start, start.plusMinutes(LESSON_MINUTES)))
    }

    /** Видаляємо лише останній дзвінок; уроки з цим номером прибираються з усіх днів. */
    fun removeLastBell() = edit { s, _ ->
        if (s.bells.size <= 1) return@edit s
        val n = s.bells.size - 1
        s.copy(bells = s.bells.take(n), week = s.week.map { it.take(n) })
    }

    fun addHoliday(start: LocalDate, end: LocalDate) = edit { s, _ ->
        if (s.holidays.size >=
            SchoolSchedule.MAX_HOLIDAYS
        ) {
            s
        } else {
            s.copy(holidays = (s.holidays + Holiday(start, end)).sortedBy { it.start })
        }
    }

    fun removeHoliday(index: Int) = edit { s, _ -> s.copy(holidays = s.holidays.filterIndexed { i, _ -> i != index }) }

    fun save() {
        val s = _state.value as? ScheduleEditorState.Editing ?: return
        val errors = ScheduleValidator.validate(s.draft)
        if (errors.isNotEmpty()) {
            _state.value = s.copy(errors = errors)
            return
        }
        viewModelScope.launch {
            val ctx = active.context.filterNotNull().first()
            val child = ctx.child ?: return@launch
            families.saveSchedule(ctx.familyId, child.userId, s.draft)
            _state.value = s.copy(dirty = false, saved = true)
        }
    }

    private companion object {
        const val SATURDAY_DAYS = 6
        const val BREAK_MINUTES = 10L
        const val LESSON_MINUTES = 45L
        const val DEFAULT_START_HOUR = 8
        const val DEFAULT_START_MINUTE = 30
    }
}
