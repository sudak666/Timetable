package com.gifwarno.rozklad.core.data.repository

import com.gifwarno.rozklad.core.model.AlertLocation
import com.gifwarno.rozklad.core.model.AlertStatus
import com.gifwarno.rozklad.core.model.FamilyData
import com.gifwarno.rozklad.core.model.Grade
import com.gifwarno.rozklad.core.model.LedgerKind
import com.gifwarno.rozklad.core.model.SchoolSchedule
import com.gifwarno.rozklad.core.model.Streak
import com.gifwarno.rozklad.core.model.SyncStatus
import java.time.Instant
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

    /** Челендж для дитини: [need] оцінок ≥ [minGrade] (з [subject] або будь-якого) за [days] днів → [reward] ₴. */
    suspend fun addChallenge(
        familyId: String,
        childId: String,
        title: String,
        subject: String?,
        minGrade: Int,
        need: Int,
        days: Int,
        reward: Int,
    )

    suspend fun deleteChallenge(familyId: String, challengeId: String)

    /** Видалити бонус, штраф чи виплату (лише батьки). */
    suspend fun deleteLedger(familyId: String, entryId: String)

    /** Ціль дитини: на що збирає і скільки коштує. */
    suspend fun setGoal(familyId: String, childId: String, title: String, amount: Int)

    /** Курс оцінок і правила серії сім'ї (school_families.rates) — діє для нових підтверджень. */
    suspend fun saveRates(familyId: String, rates: Map<Int, Int>, streak: Streak)

    /** Свій аватар і/або колір (null — не змінювати). Сервер пускає лише свій рядок (або батьків). */
    suspend fun setMyLook(familyId: String, userId: String, avatar: String?, theme: String?)

    /** Своє ім'я в сім'ї (обрізається до [NAME_MAX]). */
    suspend fun setMyName(familyId: String, userId: String, name: String)

    /** Назва сім'ї (лише батьки — так дозволяє RLS). */
    suspend fun renameFamily(familyId: String, name: String)

    suspend fun parentInviteCode(familyId: String): Result<String?>

    /** Новий код для дитини (лише батьки, потрібна мережа); старий перестає працювати. */
    suspend fun rotateInviteCode(familyId: String): Result<Unit>

    /** Прибрати учасника з сім'ї (лише батьки, потрібна мережа). */
    suspend fun removeMember(familyId: String, userId: String): Result<Unit>

    /** Зберегти розклад дитини (офлайн-черга, повна заміна на сервері). */
    suspend fun saveSchedule(familyId: String, childId: String, schedule: SchoolSchedule)

    fun dismissSyncError()

    /** Довідник місць для тривог (потрібна мережа): null — області, інакше райони й громади області. */
    suspend fun alertLocations(oblastUid: Int?): Result<List<AlertLocation>>

    /** Обрати місце для тривог сім'ї (лише батьки, потрібна мережа); null — вимкнути тривоги. */
    suspend fun setAlertRegion(familyId: String, uid: Int?): Result<Unit>

    /** Легке оновлення лише стану тривоги — для плашки й віджета, без повного знімка. */
    suspend fun refreshAlert(familyId: String): Result<Unit>

    /** Стан тривоги з push-повідомлення — без мережі. */
    suspend fun applyAlertPush(uid: Int, status: AlertStatus, since: Instant?)
}

/** Найдовше ім'я чи назва сім'ї — як у вебі (maxlength=40). */
const val NAME_MAX = 40
