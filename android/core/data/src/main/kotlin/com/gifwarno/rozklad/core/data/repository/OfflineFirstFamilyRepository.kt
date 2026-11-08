package com.gifwarno.rozklad.core.data.repository

import com.gifwarno.rozklad.core.common.Dispatcher
import com.gifwarno.rozklad.core.common.RozkladDispatchers
import com.gifwarno.rozklad.core.data.mapper.asEntity
import com.gifwarno.rozklad.core.data.mapper.asModel
import com.gifwarno.rozklad.core.data.mapper.asNetwork
import com.gifwarno.rozklad.core.data.mapper.settings
import com.gifwarno.rozklad.core.data.sync.LocalWriter
import com.gifwarno.rozklad.core.data.sync.SyncManager
import com.gifwarno.rozklad.core.data.sync.Tables
import com.gifwarno.rozklad.core.data.sync.with
import com.gifwarno.rozklad.core.database.FamilyDao
import com.gifwarno.rozklad.core.domain.Rewards
import com.gifwarno.rozklad.core.model.FamilyData
import com.gifwarno.rozklad.core.model.Grade
import com.gifwarno.rozklad.core.model.LedgerKind
import com.gifwarno.rozklad.core.model.SchoolSchedule
import com.gifwarno.rozklad.core.model.SyncStatus
import com.gifwarno.rozklad.core.network.RozkladNetworkDataSource
import com.gifwarno.rozklad.core.network.model.NetworkGrade
import com.gifwarno.rozklad.core.network.model.NetworkHomework
import com.gifwarno.rozklad.core.network.model.NetworkLedger
import com.gifwarno.rozklad.core.network.model.NetworkTimetable
import com.gifwarno.rozklad.core.network.model.NetworkWrite
import com.gifwarno.rozklad.core.network.model.WriteKind
import java.time.Clock
import java.time.LocalDate
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.put

@Singleton
internal class OfflineFirstFamilyRepository @Inject constructor(
    private val dao: FamilyDao,
    private val network: RozkladNetworkDataSource,
    private val writer: LocalWriter,
    private val sync: SyncManager,
    private val json: Json,
    private val clock: Clock,
    @param:Dispatcher(RozkladDispatchers.IO) private val io: CoroutineDispatcher,
) : FamilyRepository {
    override fun observe(familyId: String): Flow<FamilyData?> = combine(
        dao.observeFamily(familyId),
        dao.observeMembers(familyId),
        dao.observeGrades(familyId),
        dao.observeHomework(familyId),
        combine(dao.observeLedger(familyId), dao.observeChallenges(familyId), dao.observeTimetables(familyId), ::Triple),
    ) { family, members, grades, homework, (ledger, challenges, timetables) ->
        family ?: return@combine null
        FamilyData(
            family = family.asModel(),
            members = members.map { it.asModel() },
            grades = grades.map { it.asModel() },
            ledger = ledger.map { it.asModel() },
            challenges = challenges.map { it.asModel() },
            homework = homework.map { it.asModel() },
            settings = family.settings(json),
            timetables = timetables.mapNotNull { t -> t.asModel(json)?.let { t.childId to it } }.toMap(),
        )
    }.flowOn(io)

    override val syncStatus: Flow<SyncStatus> = sync.status

    override suspend fun refresh(familyId: String): Result<Unit> = withContext(io) {
        try {
            sync.flush()
            val s = network.snapshot(familyId)
            dao.replaceSnapshot(
                family = s.family.asEntity(),
                members = s.members.map { it.asEntity(familyId) },
                grades = s.grades.map { it.asEntity(familyId) },
                homework = s.homework.map { it.asEntity(familyId) },
                ledger = s.ledger.map { it.asEntity(familyId) },
                challenges = s.challenges.map { it.asEntity(familyId) },
                timetables = s.timetables.map { it.asEntity(familyId, json) },
            )
            writer.replayPending(familyId)
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (@Suppress("TooGenericExceptionCaught") e: Exception) {
            Result.failure(e)
        }
    }

    private suspend fun submit(familyId: String, write: NetworkWrite) {
        withContext(io) { writer.submit(familyId, write) }
        sync.requestSync()
    }

    private fun withFamily(body: JsonObject, familyId: String) = body.with(mapOf("family_id" to JsonPrimitive(familyId)))

    private suspend fun rates(familyId: String): Map<Int, Int> =
        dao.observeFamily(familyId).first()?.settings(json)?.rates ?: Rewards.DEFAULT_RATES

    override suspend fun addGrade(familyId: String, childId: String, subject: String, value: Int, date: LocalDate, byParent: Boolean) {
        val dto = NetworkGrade(
            id = UUID.randomUUID().toString(),
            childId = childId,
            subject = subject,
            grade = value,
            date = date.toString(),
            status = if (byParent) "approved" else "pending",
            amount = if (byParent) Rewards.rateOf(value, rates(familyId)) else null,
            createdAt = clock.instant().toString(),
        )
        val body = withFamily(json.encodeToJsonElement(NetworkGrade.serializer(), dto).jsonObject, familyId)
        submit(familyId, NetworkWrite(Tables.GRADES, WriteKind.INSERT, dto.id, body))
    }

    override suspend fun approveGrade(familyId: String, grade: Grade) {
        val body = buildJsonObject {
            put("status", "approved")
            put("amount", Rewards.rateOf(grade.value, rates(familyId)))
        }
        submit(familyId, NetworkWrite(Tables.GRADES, WriteKind.UPDATE, grade.id, body))
    }

    override suspend fun unapproveGrade(familyId: String, gradeId: String) {
        val body = JsonObject(mapOf("status" to JsonPrimitive("pending"), "amount" to JsonNull))
        submit(familyId, NetworkWrite(Tables.GRADES, WriteKind.UPDATE, gradeId, body))
    }

    override suspend fun deleteGrade(familyId: String, gradeId: String) =
        submit(familyId, NetworkWrite(Tables.GRADES, WriteKind.DELETE, gradeId, JsonObject(emptyMap())))

    override suspend fun addHomework(familyId: String, childId: String, subject: String, due: LocalDate, text: String) {
        val dto = NetworkHomework(UUID.randomUUID().toString(), childId, subject, due.toString(), text.trim(), done = false)
        val body = withFamily(json.encodeToJsonElement(NetworkHomework.serializer(), dto).jsonObject, familyId)
        submit(familyId, NetworkWrite(Tables.HOMEWORK, WriteKind.INSERT, dto.id, body))
    }

    override suspend fun setHomeworkDone(familyId: String, homeworkId: String, done: Boolean) =
        submit(familyId, NetworkWrite(Tables.HOMEWORK, WriteKind.UPDATE, homeworkId, buildJsonObject { put("done", done) }))

    override suspend fun deleteHomework(familyId: String, homeworkId: String) =
        submit(familyId, NetworkWrite(Tables.HOMEWORK, WriteKind.DELETE, homeworkId, JsonObject(emptyMap())))

    override suspend fun addLedger(familyId: String, childId: String, kind: LedgerKind, amount: Int, note: String) {
        val dto = NetworkLedger(
            id = UUID.randomUUID().toString(),
            childId = childId,
            kind = if (kind == LedgerKind.PAYOUT) "payout" else "bonus",
            amount = amount,
            note = note.trim(),
            date = LocalDate.now(clock).toString(),
            createdAt = clock.instant().toString(),
        )
        val body = withFamily(json.encodeToJsonElement(NetworkLedger.serializer(), dto).jsonObject, familyId)
        submit(familyId, NetworkWrite(Tables.LEDGER, WriteKind.INSERT, dto.id, body))
    }

    override suspend fun parentInviteCode(familyId: String): Result<String?> = withContext(io) {
        runCatching { network.parentCode(familyId) }
    }

    override suspend fun saveSchedule(familyId: String, childId: String, schedule: SchoolSchedule) {
        val dto = json.encodeToJsonElement(NetworkTimetable.serializer(), schedule.asNetwork(childId)).jsonObject
        // updated_at ставить тригер на сервері.
        val body = withFamily(JsonObject(dto.filterKeys { it != "updated_at" }), familyId)
        submit(familyId, NetworkWrite(Tables.TIMETABLES, WriteKind.UPSERT, childId, body, key = Tables.keyOf(Tables.TIMETABLES)))
    }

    override fun dismissSyncError() = sync.clearError()
}
