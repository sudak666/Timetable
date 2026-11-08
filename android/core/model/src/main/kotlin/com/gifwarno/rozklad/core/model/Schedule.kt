package com.gifwarno.rozklad.core.model

import java.time.LocalTime

data class Lesson(val subject: String, val optional: Boolean = false)

data class SchoolDay(val name: String, val lessons: List<Lesson>)

data class Bell(val start: LocalTime, val end: LocalTime)

/** Предмет: колір (ARGB) та емодзі для інтерфейсу. */
data class SubjectInfo(val name: String, val color: Long, val emoji: String)
