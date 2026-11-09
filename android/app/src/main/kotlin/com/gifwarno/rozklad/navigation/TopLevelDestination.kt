package com.gifwarno.rozklad.navigation

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.MenuBook
import androidx.compose.material.icons.automirrored.outlined.MenuBook
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.Today
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material.icons.outlined.StarOutline
import androidx.compose.material.icons.outlined.Today
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.navigation3.runtime.NavKey
import kotlinx.serialization.Serializable

/* Ключі навігації (Navigation 3). Серіалізуються — стек переживає смерть процесу. */

@Serializable data object TodayKey : NavKey

@Serializable data object GradesKey : NavKey

@Serializable data object HomeworkKey : NavKey

@Serializable data object ProfileKey : NavKey

/** Редактор розкладу — відкривається поверх вкладки. */
@Serializable data object ScheduleEditorKey : NavKey

/** Вибір місця для повітряних тривог — відкривається поверх «Профілю». */
@Serializable data object AlertRegionKey : NavKey

enum class TopLevelDestination(val key: NavKey, val label: String, val selectedIcon: ImageVector, val icon: ImageVector) {
    TODAY(TodayKey, "Сьогодні", Icons.Filled.Today, Icons.Outlined.Today),
    GRADES(GradesKey, "Оцінки", Icons.Filled.Star, Icons.Outlined.StarOutline),
    HOMEWORK(HomeworkKey, "Домашка", Icons.AutoMirrored.Filled.MenuBook, Icons.AutoMirrored.Outlined.MenuBook),
    PROFILE(ProfileKey, "Профіль", Icons.Filled.Person, Icons.Outlined.Person),
}
