package com.gifwarno.rozklad.core.data.repository

import com.gifwarno.rozklad.core.model.FamilyData
import com.gifwarno.rozklad.core.model.Grade
import com.gifwarno.rozklad.core.model.LedgerKind
import com.gifwarno.rozklad.core.model.SchoolSchedule
import com.gifwarno.rozklad.core.model.SyncStatus
import java.time.LocalDate
import kotlinx.coroutines.flow.Flow

/**
 * Дані сім'ї, offline-first: читання — завжди з локальної БД, записи — оптимістично
 * з чергою на сервер. Екрани не знають, є мережа чи ні.
 */
interface FamilyRepository {
    fun observe(familyId: String): Flow<FamilyData?>

    val syncStatus: Flow<SyncStatus>

    /** Відправити чергу й завантажити свіжий знімок з сервера. */
    suspend fun refresh(familyId: String): Result<Unit>

    suspend fun addGrade(familyId: String, childId: String, subject: String, value: Int, date: LocalDate, byParent: Boolean)

    suspend fun approveGrade(familyId: String, grade: Grade)

    suspend fun unapproveGrade(familyId: String, gradeId: String)

    suspend fun deleteGrade(familyId: String, gradeId: String)

    suspend fun addHomework(familyId: String, childId: String, subject: String, due: LocalDate, text: String)

    suspend fun setHomeworkDone(familyId: String, homeworkId: String, done: Boolean)

    suspend fun deleteHomework(familyId: String, homeworkId: String)

    suspend fun addLedger(familyId: String, childId: String, kind: LedgerKind, amount: Int, note: String)

    suspend fun parentInviteCode(familyId: String): Result<String?>

    /** Зберегти розклад дитини (офлайн-черга, повна заміна на сервері). */
    suspend fun saveSchedule(familyId: String, childId: String, schedule: SchoolSchedule)

    fun dismissSyncError()
}
