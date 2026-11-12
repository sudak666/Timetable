package com.gifwarno.rozklad.core.testing

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.emptyPreferences
import com.gifwarno.rozklad.core.data.repository.FamilyRepository
import com.gifwarno.rozklad.core.data.repository.SessionRepository
import com.gifwarno.rozklad.core.datastore.UserPreferencesDataSource
import com.gifwarno.rozklad.core.model.AirAlert
import com.gifwarno.rozklad.core.model.AlertLocation
import com.gifwarno.rozklad.core.model.AlertStatus
import com.gifwarno.rozklad.core.model.FamilyData
import com.gifwarno.rozklad.core.model.Grade
import com.gifwarno.rozklad.core.model.GradeStatus
import com.gifwarno.rozklad.core.model.Homework
import com.gifwarno.rozklad.core.model.LedgerKind
import com.gifwarno.rozklad.core.model.SchoolSchedule
import com.gifwarno.rozklad.core.model.SessionState
import com.gifwarno.rozklad.core.model.SyncStatus
import java.time.Instant
import java.time.LocalDate
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update

/** Сховище налаштувань у пам'яті — без файлів і Android. */
class InMemoryPreferencesStore : DataStore<Preferences> {
    private val state = MutableStateFlow(emptyPreferences())
    override val data: Flow<Preferences> = state

    override suspend fun updateData(transform: suspend (t: Preferences) -> Preferences): Preferences =
        transform(state.value).also { state.value = it }
}

fun fakePreferences() = UserPreferencesDataSource(InMemoryPreferencesStore())

class FakeSessionRepository(initial: SessionState) : SessionRepository {
    private val state = MutableStateFlow(initial)
    override val session: StateFlow<SessionState> = state

    fun emit(s: SessionState) {
        state.value = s
    }

    override suspend fun signIn(email: String, password: String) = Result.success(Unit)

    override suspend fun signUp(email: String, password: String) = Result.success(false)

    val resetEmails = mutableListOf<String>()
    val newPasswords = mutableListOf<String>()

    override suspend fun resetPassword(email: String): Result<Unit> = Result.success(Unit).also { resetEmails += email }

    override suspend fun changePassword(password: String): Result<Unit> = Result.success(Unit).also { newPasswords += password }

    override suspend fun createFamily(parentName: String) = Result.success(Unit)

    override suspend fun joinFamily(code: String, name: String) = Result.success(Unit)

    override fun retry() = Unit

    override suspend fun signOut() {
        state.value = SessionState.SignedOut
    }

    var googleCalls = 0
    private val oauthErrors = MutableStateFlow<String?>(null)
    override val oauthError: StateFlow<String?> = oauthErrors

    override suspend fun signInWithGoogle(): Result<Unit> {
        googleCalls++
        return Result.success(Unit)
    }

    override fun completeGoogleSignIn(callbackUri: String) {
        if (callbackUri.contains("error")) {
            oauthErrors.value = "Скасовано"
        } else {
            state.value =
                SessionState.Ready(TestData.parentUser, "f1", TestData.parent.role)
        }
    }

    override fun clearOAuthError() {
        oauthErrors.value = null
    }

    var deleteResult: Result<Unit> = Result.success(Unit)
    var deleteCalls = 0

    override suspend fun deleteAccount(): Result<Unit> {
        deleteCalls++
        if (deleteResult.isSuccess) state.value = SessionState.SignedOut
        return deleteResult
    }

    companion object {
        fun ready() = FakeSessionRepository(SessionState.Ready(TestData.parentUser, "f1", TestData.parent.role))
    }
}

/** Фейк репозиторію: тримає дані в пам'яті й записує, що викликали. */
class FakeFamilyRepository(initial: FamilyData?) : FamilyRepository {
    data class AddedGrade(val childId: String, val subject: String, val value: Int, val date: LocalDate, val byParent: Boolean)

    private val data = MutableStateFlow(initial)
    private val sync = MutableStateFlow(SyncStatus(0, null))
    val added = mutableListOf<AddedGrade>()

    override fun observe(familyId: String): Flow<FamilyData?> = data

    override val syncStatus: Flow<SyncStatus> = sync

    override suspend fun refresh(familyId: String) = Result.success(Unit)

    override suspend fun addGrade(familyId: String, childId: String, subject: String, value: Int, date: LocalDate, byParent: Boolean) {
        added += AddedGrade(childId, subject, value, date, byParent)
        val g = Grade(
            id = "new${added.size}",
            childId = childId,
            subject = subject,
            value = value,
            date = date,
            status = if (byParent) GradeStatus.APPROVED else GradeStatus.PENDING,
            amount = null,
            createdAt = Instant.EPOCH,
        )
        data.update { it?.copy(grades = listOf(g) + it.grades) }
    }

    override suspend fun approveGrade(familyId: String, grade: Grade) = data.update { d ->
        d?.copy(grades = d.grades.map { if (it.id == grade.id) it.copy(status = GradeStatus.APPROVED) else it })
    }

    override suspend fun unapproveGrade(familyId: String, gradeId: String) = data.update { d ->
        d?.copy(grades = d.grades.map { if (it.id == gradeId) it.copy(status = GradeStatus.PENDING, amount = null) else it })
    }

    override suspend fun deleteGrade(familyId: String, gradeId: String) = data.update { d ->
        d?.copy(grades = d.grades.filterNot { it.id == gradeId })
    }

    override suspend fun addHomework(familyId: String, childId: String, subject: String, due: LocalDate, text: String) = data.update { d ->
        d?.copy(homework = d.homework + Homework("hw${d.homework.size}", childId, subject, due, text, false))
    }

    override suspend fun setHomeworkDone(familyId: String, homeworkId: String, done: Boolean) = data.update { d ->
        d?.copy(homework = d.homework.map { if (it.id == homeworkId) it.copy(done = done) else it })
    }

    override suspend fun deleteHomework(familyId: String, homeworkId: String) = data.update { d ->
        d?.copy(homework = d.homework.filterNot { it.id == homeworkId })
    }

    val ledgerAdded = mutableListOf<Triple<LedgerKind, Int, String>>()

    override suspend fun addLedger(familyId: String, childId: String, kind: LedgerKind, amount: Int, note: String) {
        ledgerAdded += Triple(kind, amount, note)
    }

    val challengesAdded = mutableListOf<String>()
    var savedRates: Pair<Map<Int, Int>, com.gifwarno.rozklad.core.model.Streak>? = null

    override suspend fun addChallenge(
        familyId: String,
        childId: String,
        title: String,
        subject: String?,
        minGrade: Int,
        need: Int,
        days: Int,
        reward: Int,
    ) {
        challengesAdded += title
    }

    override suspend fun deleteChallenge(familyId: String, challengeId: String) = Unit

    override suspend fun deleteLedger(familyId: String, entryId: String) = Unit

    var goal: Pair<String, Int>? = null

    override suspend fun setGoal(familyId: String, childId: String, title: String, amount: Int) {
        goal = title to amount
    }

    override suspend fun saveRates(familyId: String, rates: Map<Int, Int>, streak: com.gifwarno.rozklad.core.model.Streak) {
        savedRates = rates to streak
    }

    override suspend fun setMyName(familyId: String, userId: String, name: String) {
        data.value =
            data.value?.let { d -> d.copy(members = d.members.map { if (it.userId == userId) it.copy(name = name.trim()) else it }) }
    }

    override suspend fun renameFamily(familyId: String, name: String) {
        data.value = data.value?.let { d -> d.copy(family = d.family.copy(name = name.trim())) }
    }

    val looks = mutableListOf<Triple<String, String?, String?>>()

    override suspend fun setMyLook(familyId: String, userId: String, avatar: String?, theme: String?) {
        looks += Triple(userId, avatar, theme)
        data.value = data.value?.let { d ->
            d.copy(
                members = d.members.map {
                    if (it.userId == userId) it.copy(avatar = avatar ?: it.avatar, theme = theme ?: it.theme) else it
                },
            )
        }
    }

    override suspend fun parentInviteCode(familyId: String) = Result.success<String?>("PARENT1")

    /** Відповідь на зміну коду / видалення учасника; за замовчуванням — успіх. */
    var familyActionResult: Result<Unit> = Result.success(Unit)
    val removedMembers = mutableListOf<String>()

    override suspend fun rotateInviteCode(familyId: String): Result<Unit> = familyActionResult.onSuccess {
        data.update { d -> d?.copy(family = d.family.copy(inviteCode = "NEWCODE8")) }
    }

    override suspend fun removeMember(familyId: String, userId: String): Result<Unit> = familyActionResult.onSuccess {
        removedMembers += userId
        data.update { d -> d?.copy(members = d.members.filterNot { it.userId == userId }) }
    }

    val savedSchedules = mutableMapOf<String, SchoolSchedule>()

    override suspend fun saveSchedule(familyId: String, childId: String, schedule: SchoolSchedule) {
        savedSchedules[childId] = schedule
        data.update { it?.copy(timetables = it.timetables + (childId to schedule)) }
    }

    override fun dismissSyncError() = sync.update { it.copy(lastError = null) }

    /** Довідник місць: ключ — oblastUid (null — області). */
    var locations: Map<Int?, List<AlertLocation>> = emptyMap()
    var alertResult: Result<Unit> = Result.success(Unit)
    val alertRegions = mutableListOf<Int?>()

    override suspend fun alertLocations(oblastUid: Int?) = Result.success(locations[oblastUid].orEmpty())

    override suspend fun setAlertRegion(familyId: String, uid: Int?): Result<Unit> {
        alertRegions += uid
        if (alertResult.isSuccess) {
            val name = locations.values.flatten().firstOrNull { it.uid == uid }?.name ?: "Місце $uid"
            data.update { it?.copy(airAlert = uid?.let { u -> AirAlert(u, name, null, null, null) }) }
        }
        return alertResult
    }

    override suspend fun refreshAlert(familyId: String) = Result.success(Unit)

    override suspend fun applyAlertPush(uid: Int, status: AlertStatus, since: Instant?) = data.update { d ->
        val a = d?.airAlert
        if (a == null || a.regionUid != uid) d else d.copy(airAlert = a.copy(status = status, since = since))
    }
}
