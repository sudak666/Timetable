package com.gifwarno.rozklad.core.testing

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.emptyPreferences
import com.gifwarno.rozklad.core.data.repository.FamilyRepository
import com.gifwarno.rozklad.core.data.repository.SessionRepository
import com.gifwarno.rozklad.core.datastore.UserPreferencesDataSource
import com.gifwarno.rozklad.core.model.FamilyData
import com.gifwarno.rozklad.core.model.Grade
import com.gifwarno.rozklad.core.model.GradeStatus
import com.gifwarno.rozklad.core.model.Homework
import com.gifwarno.rozklad.core.model.LedgerKind
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

    override suspend fun createFamily(parentName: String) = Result.success(Unit)

    override suspend fun joinFamily(code: String, name: String) = Result.success(Unit)

    override fun retry() = Unit

    override suspend fun signOut() {
        state.value = SessionState.SignedOut
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

    override suspend fun addLedger(familyId: String, childId: String, kind: LedgerKind, amount: Int, note: String) = Unit

    override suspend fun parentInviteCode(familyId: String) = Result.success<String?>("PARENT1")

    override fun dismissSyncError() = sync.update { it.copy(lastError = null) }
}
