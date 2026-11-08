package com.gifwarno.rozklad.core.database

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/*
 * Локальна копія даних сім'ї. Дати — ISO-рядки (YYYY-MM-DD), як на сервері;
 * моменти часу — epoch millis. Перетворення в доменні моделі — у core:data.
 */

@Entity(tableName = "families")
data class FamilyEntity(
    @PrimaryKey val id: String,
    val name: String,
    val inviteCode: String,
    /** Сирий JSON колонки `rates` — розбирається в домені. */
    val settingsJson: String,
)

@Entity(tableName = "members", primaryKeys = ["familyId", "userId"])
data class MemberEntity(
    val familyId: String,
    val userId: String,
    val role: String,
    val name: String,
    val goalTitle: String,
    val goalAmount: Int,
    val avatar: String,
    val theme: String,
)

@Entity(tableName = "grades", indices = [Index("familyId", "childId")])
data class GradeEntity(
    @PrimaryKey val id: String,
    val familyId: String,
    val childId: String,
    val subject: String,
    val value: Int,
    val date: String,
    val status: String,
    val amount: Int?,
    val createdAt: Long,
)

@Entity(tableName = "homework", indices = [Index("familyId", "childId")])
data class HomeworkEntity(
    @PrimaryKey val id: String,
    val familyId: String,
    val childId: String,
    val subject: String,
    val due: String,
    val text: String,
    val done: Boolean,
)

@Entity(tableName = "ledger", indices = [Index("familyId", "childId")])
data class LedgerEntity(
    @PrimaryKey val id: String,
    val familyId: String,
    val childId: String,
    val kind: String,
    val amount: Int,
    val note: String,
    val date: String,
    val createdAt: Long,
)

@Entity(tableName = "challenges", indices = [Index("familyId", "childId")])
data class ChallengeEntity(
    @PrimaryKey val id: String,
    val familyId: String,
    val childId: String,
    val title: String,
    val subject: String?,
    val minGrade: Int,
    val need: Int,
    val reward: Int,
    val startDate: String,
    val endDate: String,
)

/** Розклад дитини. Частини зберігаються як JSON у форматі сервера (school_timetables). */
@Entity(tableName = "timetables", indices = [Index("familyId")])
data class TimetableEntity(
    @PrimaryKey val childId: String,
    val familyId: String,
    val bellsJson: String,
    val weekJson: String,
    val holidaysJson: String,
)

/**
 * Офлайн-черга змін. Кожна зміна має клієнтський UUID рядка → повторна відправка ідемпотентна.
 * [payload] — JSON тіла запиту (формує core:data).
 */
@Entity(tableName = "outbox")
data class OutboxEntity(
    @PrimaryKey(autoGenerate = true) val seq: Long = 0,
    val familyId: String,
    val tableName: String,
    val kind: String,
    val rowId: String,
    val payload: String,
    val attempts: Int = 0,
    val createdAt: Long,
)
