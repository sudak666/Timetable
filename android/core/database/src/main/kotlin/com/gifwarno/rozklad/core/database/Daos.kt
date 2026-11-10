package com.gifwarno.rozklad.core.database

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

@Dao
interface FamilyDao {
    @Query("SELECT * FROM families WHERE id = :familyId")
    fun observeFamily(familyId: String): Flow<FamilyEntity?>

    @Query("SELECT * FROM members WHERE familyId = :familyId ORDER BY role DESC, name")
    fun observeMembers(familyId: String): Flow<List<MemberEntity>>

    @Query("SELECT * FROM grades WHERE familyId = :familyId ORDER BY date DESC, createdAt DESC")
    fun observeGrades(familyId: String): Flow<List<GradeEntity>>

    @Query("SELECT * FROM homework WHERE familyId = :familyId ORDER BY due, subject")
    fun observeHomework(familyId: String): Flow<List<HomeworkEntity>>

    @Query("SELECT * FROM ledger WHERE familyId = :familyId ORDER BY date DESC, createdAt DESC")
    fun observeLedger(familyId: String): Flow<List<LedgerEntity>>

    @Query("SELECT * FROM challenges WHERE familyId = :familyId")
    fun observeChallenges(familyId: String): Flow<List<ChallengeEntity>>

    @Query("SELECT * FROM timetables WHERE familyId = :familyId")
    fun observeTimetables(familyId: String): Flow<List<TimetableEntity>>

    @Upsert
    suspend fun upsertTimetables(rows: List<TimetableEntity>)

    @Query("DELETE FROM timetables WHERE familyId = :familyId")
    suspend fun clearTimetables(familyId: String)

    @Upsert suspend fun upsertFamily(family: FamilyEntity)

    @Query("SELECT * FROM air_alerts WHERE familyId = :familyId")
    fun observeAlert(familyId: String): Flow<AirAlertEntity?>

    @Query("SELECT * FROM air_alerts WHERE familyId = :familyId")
    suspend fun alert(familyId: String): AirAlertEntity?

    @Upsert suspend fun upsertAlert(alert: AirAlertEntity)

    @Query("DELETE FROM air_alerts WHERE familyId = :familyId")
    suspend fun deleteAlert(familyId: String)

    /** Push про тривогу: оновити стан усіх сімей із цим місцем (зазвичай одна). */
    @Query("UPDATE air_alerts SET status = :status, sinceMillis = :sinceMillis, checkedAtMillis = :checkedAtMillis WHERE uid = :uid")
    suspend fun updateAlertState(uid: Int, status: String, sinceMillis: Long?, checkedAtMillis: Long)

    @Upsert suspend fun upsertMembers(rows: List<MemberEntity>)

    @Upsert suspend fun upsertGrades(rows: List<GradeEntity>)

    @Upsert suspend fun upsertHomework(rows: List<HomeworkEntity>)

    @Upsert suspend fun upsertLedger(rows: List<LedgerEntity>)

    @Upsert suspend fun upsertChallenges(rows: List<ChallengeEntity>)

    @Query("DELETE FROM ledger WHERE id = :id")
    suspend fun deleteLedgerEntry(id: String)

    @Query("UPDATE members SET goalTitle = :title, goalAmount = :amount WHERE familyId = :familyId AND userId = :userId")
    suspend fun setMemberGoal(familyId: String, userId: String, title: String, amount: Int)

    @Query("DELETE FROM challenges WHERE id = :id")
    suspend fun deleteChallenge(id: String)

    @Query("UPDATE families SET settingsJson = :json WHERE id = :familyId")
    suspend fun setSettingsJson(familyId: String, json: String)

    /** Аватар і колір учасника (null — не змінювати). */
    @Query(
        "UPDATE members SET avatar = COALESCE(:avatar, avatar), theme = COALESCE(:theme, theme) " +
            "WHERE familyId = :familyId AND userId = :userId",
    )
    suspend fun setMemberLook(familyId: String, userId: String, avatar: String?, theme: String?)

    @Query("UPDATE homework SET done = :done WHERE id = :id")
    suspend fun setHomeworkDone(id: String, done: Boolean)

    @Query("UPDATE grades SET status = :status, amount = :amount WHERE id = :id")
    suspend fun setGradeStatus(id: String, status: String, amount: Int?)

    @Query("DELETE FROM grades WHERE id = :id")
    suspend fun deleteGrade(id: String)

    @Query("DELETE FROM homework WHERE id = :id")
    suspend fun deleteHomework(id: String)

    @Query("DELETE FROM members WHERE familyId = :familyId")
    suspend fun clearMembers(familyId: String)

    @Query("DELETE FROM grades WHERE familyId = :familyId")
    suspend fun clearGrades(familyId: String)

    @Query("DELETE FROM homework WHERE familyId = :familyId")
    suspend fun clearHomework(familyId: String)

    @Query("DELETE FROM ledger WHERE familyId = :familyId")
    suspend fun clearLedger(familyId: String)

    @Query("DELETE FROM challenges WHERE familyId = :familyId")
    suspend fun clearChallenges(familyId: String)

    /** Атомарна заміна знімка сервера — UI ніколи не бачить напівоновлених даних. */
    @Transaction
    suspend fun replaceSnapshot(
        family: FamilyEntity,
        members: List<MemberEntity>,
        grades: List<GradeEntity>,
        homework: List<HomeworkEntity>,
        ledger: List<LedgerEntity>,
        challenges: List<ChallengeEntity>,
        timetables: List<TimetableEntity>,
        alert: AirAlertEntity?,
    ) {
        upsertFamily(family)
        clearMembers(family.id)
        upsertMembers(members)
        clearGrades(family.id)
        upsertGrades(grades)
        clearHomework(family.id)
        upsertHomework(homework)
        clearLedger(family.id)
        upsertLedger(ledger)
        clearChallenges(family.id)
        upsertChallenges(challenges)
        clearTimetables(family.id)
        upsertTimetables(timetables)
        if (alert == null) deleteAlert(family.id) else upsertAlert(alert)
    }
}

@Dao
interface OutboxDao {
    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun enqueue(op: OutboxEntity): Long

    @Query("SELECT * FROM outbox ORDER BY seq")
    suspend fun pending(): List<OutboxEntity>

    @Query("SELECT * FROM outbox WHERE familyId = :familyId ORDER BY seq")
    suspend fun pendingFor(familyId: String): List<OutboxEntity>

    @Query("DELETE FROM outbox")
    suspend fun clear()

    @Query("SELECT COUNT(*) FROM outbox")
    fun observeCount(): Flow<Int>

    @Query("DELETE FROM outbox WHERE seq = :seq")
    suspend fun remove(seq: Long)

    @Query("UPDATE outbox SET attempts = attempts + 1 WHERE seq = :seq")
    suspend fun markAttempt(seq: Long)
}
