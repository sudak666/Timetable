package com.gifwarno.rozklad.demo

import com.gifwarno.rozklad.core.data.repository.FamilyRepository
import com.gifwarno.rozklad.core.data.repository.SessionRepository
import com.gifwarno.rozklad.core.domain.Rewards
import com.gifwarno.rozklad.core.model.AirAlert
import com.gifwarno.rozklad.core.model.AlertLocation
import com.gifwarno.rozklad.core.model.AlertLocationKind
import com.gifwarno.rozklad.core.model.AlertStatus
import com.gifwarno.rozklad.core.model.Challenge
import com.gifwarno.rozklad.core.model.FamilyData
import com.gifwarno.rozklad.core.model.Grade
import com.gifwarno.rozklad.core.model.GradeStatus
import com.gifwarno.rozklad.core.model.Homework
import com.gifwarno.rozklad.core.model.LedgerEntry
import com.gifwarno.rozklad.core.model.LedgerKind
import com.gifwarno.rozklad.core.model.RewardSettings
import com.gifwarno.rozklad.core.model.SchoolSchedule
import com.gifwarno.rozklad.core.model.SessionState
import com.gifwarno.rozklad.core.model.Streak
import com.gifwarno.rozklad.core.model.SyncStatus
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.util.UUID
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

/** Сесія демо: одразу «увійшли»; вихід і видалення акаунта лише скидають у «не увійшли». */
internal class DemoSessionRepository(private val initial: SessionState) : SessionRepository {
    private val state = MutableStateFlow(initial)
    override val session: StateFlow<SessionState> = state.asStateFlow()
    override val oauthError: StateFlow<String?> = MutableStateFlow(null)

    override suspend fun signIn(email: String, password: String): Result<Unit> = Result.success(Unit).also { state.value = initial }

    override suspend fun signUp(email: String, password: String): Result<Boolean> = Result.success(false)

    override suspend fun createFamily(parentName: String): Result<Unit> = Result.success(Unit)

    override suspend fun joinFamily(code: String, name: String): Result<Unit> = Result.success(Unit)

    override fun retry() = Unit

    override suspend fun signOut() {
        state.value = SessionState.SignedOut
    }

    override suspend fun signInWithGoogle(): Result<Unit> = Result.success(Unit).also { state.value = initial }

    override fun completeGoogleSignIn(callbackUri: String) = Unit

    override fun clearOAuthError() = Unit

    override suspend fun deleteAccount(): Result<Unit> = Result.success(Unit).also { state.value = SessionState.SignedOut }
}

/** Дані демо-сім'ї в пам'яті: усі записи працюють як справжні, але нікуди не відправляються. */
internal class DemoFamilyRepository(initial: FamilyData, private val clock: Clock) : FamilyRepository {
    private val data = MutableStateFlow(initial)

    override fun observe(familyId: String): Flow<FamilyData?> = data

    override val syncStatus: Flow<SyncStatus> = MutableStateFlow(SyncStatus(0, null))

    override suspend fun refresh(familyId: String): Result<Unit> = Result.success(Unit)

    private fun edit(f: (FamilyData) -> FamilyData) = data.update(f)

    private fun id() = UUID.randomUUID().toString()

    private fun today() = LocalDate.now(clock)

    override suspend fun addGrade(familyId: String, childId: String, subject: String, value: Int, date: LocalDate, byParent: Boolean) =
        edit { d ->
            val status = if (byParent) GradeStatus.APPROVED else GradeStatus.PENDING
            val amount = if (byParent) Rewards.rateOf(value, d.settings.rates) else null
            d.copy(grades = listOf(Grade(id(), childId, subject, value, date, status, amount, clock.instant())) + d.grades)
        }

    override suspend fun approveGrade(familyId: String, grade: Grade) = edit { d ->
        val amount = Rewards.rateOf(grade.value, d.settings.rates)
        d.copy(grades = d.grades.map { if (it.id == grade.id) it.copy(status = GradeStatus.APPROVED, amount = amount) else it })
    }

    override suspend fun unapproveGrade(familyId: String, gradeId: String) = edit { d ->
        d.copy(grades = d.grades.map { if (it.id == gradeId) it.copy(status = GradeStatus.PENDING, amount = null) else it })
    }

    override suspend fun deleteGrade(familyId: String, gradeId: String) = edit { d ->
        d.copy(grades = d.grades.filterNot { it.id == gradeId })
    }

    override suspend fun addHomework(familyId: String, childId: String, subject: String, due: LocalDate, text: String) = edit { d ->
        d.copy(homework = d.homework + Homework(id(), childId, subject, due, text.trim(), false))
    }

    override suspend fun setHomeworkDone(familyId: String, homeworkId: String, done: Boolean) = edit { d ->
        d.copy(homework = d.homework.map { if (it.id == homeworkId) it.copy(done = done) else it })
    }

    override suspend fun deleteHomework(familyId: String, homeworkId: String) = edit { d ->
        d.copy(
            homework = d.homework.filterNot {
                it.id ==
                    homeworkId
            },
        )
    }

    override suspend fun addLedger(familyId: String, childId: String, kind: LedgerKind, amount: Int, note: String) = edit { d ->
        d.copy(ledger = d.ledger + LedgerEntry(id(), childId, kind, amount, note.trim(), today(), clock.instant()))
    }

    override suspend fun addChallenge(
        familyId: String,
        childId: String,
        title: String,
        subject: String?,
        minGrade: Int,
        need: Int,
        days: Int,
        reward: Int,
    ) = edit { d ->
        val start = today()
        d.copy(
            challenges =
                d.challenges + Challenge(id(), childId, title.trim(), subject, minGrade, need, reward, start, start.plusDays(days - 1L)),
        )
    }

    override suspend fun deleteChallenge(familyId: String, challengeId: String) = edit { d ->
        d.copy(
            challenges = d.challenges.filterNot {
                it.id ==
                    challengeId
            },
        )
    }

    override suspend fun deleteLedger(familyId: String, entryId: String) = edit { d ->
        d.copy(ledger = d.ledger.filterNot { it.id == entryId })
    }

    override suspend fun setGoal(familyId: String, childId: String, title: String, amount: Int) = edit { d ->
        d.copy(
            members = d.members.map {
                if (it.userId ==
                    childId
                ) {
                    it.copy(goalTitle = title.trim(), goalAmount = amount.coerceAtLeast(0))
                } else {
                    it
                }
            },
        )
    }

    override suspend fun saveRates(familyId: String, rates: Map<Int, Int>, streak: Streak) = edit { d ->
        d.copy(settings = RewardSettings(rates, streak))
    }

    override suspend fun setMyLook(familyId: String, userId: String, avatar: String?, theme: String?) = edit { d ->
        d.copy(
            members = d.members.map {
                if (it.userId ==
                    userId
                ) {
                    it.copy(avatar = avatar ?: it.avatar, theme = theme ?: it.theme)
                } else {
                    it
                }
            },
        )
    }

    override suspend fun setMyName(familyId: String, userId: String, name: String) = edit { d ->
        d.copy(members = d.members.map { if (it.userId == userId) it.copy(name = name.trim()) else it })
    }

    override suspend fun renameFamily(familyId: String, name: String) = edit { d -> d.copy(family = d.family.copy(name = name.trim())) }

    override suspend fun parentInviteCode(familyId: String): Result<String?> = Result.success("DEMO34")

    override suspend fun saveSchedule(familyId: String, childId: String, schedule: SchoolSchedule) = edit { d ->
        d.copy(timetables = d.timetables + (childId to schedule))
    }

    override fun dismissSyncError() = Unit

    override suspend fun alertLocations(oblastUid: Int?): Result<List<AlertLocation>> = Result.success(
        if (oblastUid == null) {
            listOf(
                AlertLocation(31, "м. Київ", AlertLocationKind.CITY, null, null),
                AlertLocation(14, "Київська область", AlertLocationKind.OBLAST, null, null),
                AlertLocation(12, "Львівська область", AlertLocationKind.OBLAST, null, null),
            )
        } else {
            emptyList()
        },
    )

    override suspend fun setAlertRegion(familyId: String, uid: Int?): Result<Unit> {
        val name = alertLocations(null).getOrThrow().firstOrNull { it.uid == uid }?.name ?: "Місце $uid"
        edit { d -> d.copy(airAlert = uid?.let { AirAlert(it, name, AlertStatus.NONE, null, clock.instant()) }) }
        return Result.success(Unit)
    }

    override suspend fun refreshAlert(familyId: String): Result<Unit> = Result.success(Unit)

    override suspend fun applyAlertPush(uid: Int, status: AlertStatus, since: Instant?) = edit { d ->
        val a = d.airAlert
        if (a == null || a.regionUid != uid) d else d.copy(airAlert = a.copy(status = status, since = since))
    }
}
