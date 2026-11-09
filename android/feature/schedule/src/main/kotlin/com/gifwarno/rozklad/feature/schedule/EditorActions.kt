package com.gifwarno.rozklad.feature.schedule

import androidx.compose.runtime.Immutable
import java.time.LocalDate
import java.time.LocalTime

/** Дії редактора: екран не знає про ViewModel (state hoisting, легко робити прев'ю й тести UI). */
@Immutable
internal data class EditorActions(
    val selectDay: (Int) -> Unit,
    val addLesson: (String) -> Unit,
    val setLesson: (Int, String) -> Unit,
    val toggleOptional: (Int) -> Unit,
    val removeLesson: (Int) -> Unit,
    val moveLesson: (Int, Int) -> Unit,
    val copyFrom: (Int) -> Unit,
    val setSaturday: (Boolean) -> Unit,
    val setBell: (Int, LocalTime, LocalTime) -> Unit,
    val addBell: () -> Unit,
    val removeLastBell: () -> Unit,
    val addHoliday: (LocalDate, LocalDate) -> Unit,
    val removeHoliday: (Int) -> Unit,
    val save: () -> Unit,
)

internal fun ScheduleEditorViewModel.actions() = EditorActions(
    selectDay = ::selectDay,
    addLesson = ::addLesson,
    setLesson = ::setLesson,
    toggleOptional = ::toggleOptional,
    removeLesson = ::removeLesson,
    moveLesson = ::moveLesson,
    copyFrom = ::copyFrom,
    setSaturday = ::setSaturday,
    setBell = ::setBell,
    addBell = ::addBell,
    removeLastBell = ::removeLastBell,
    addHoliday = ::addHoliday,
    removeHoliday = ::removeHoliday,
    save = ::save,
)
