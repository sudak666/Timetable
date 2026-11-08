package com.gifwarno.rozklad.core.data.sync

import androidx.room.withTransaction
import com.gifwarno.rozklad.core.data.mapper.asEntity
import com.gifwarno.rozklad.core.database.FamilyDao
import com.gifwarno.rozklad.core.database.OutboxDao
import com.gifwarno.rozklad.core.database.OutboxEntity
import com.gifwarno.rozklad.core.database.RozkladDatabase
import com.gifwarno.rozklad.core.network.model.NetworkGrade
import com.gifwarno.rozklad.core.network.model.NetworkHomework
import com.gifwarno.rozklad.core.network.model.NetworkLedger
import com.gifwarno.rozklad.core.network.model.NetworkWrite
import com.gifwarno.rozklad.core.network.model.WriteKind
import java.time.Clock
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

internal object Tables {
    const val GRADES = "school_grades"
    const val HOMEWORK = "school_homework"
    const val LEDGER = "school_ledger"
}

/**
 * Оптимістичний запис: зміна одразу видна в UI (Room) і стає в чергу на сервер —
 * в одній транзакції, тож не буває «показали, але не відправимо».
 */
@Singleton
internal class LocalWriter @Inject constructor(
    private val db: RozkladDatabase,
    private val familyDao: FamilyDao,
    private val outboxDao: OutboxDao,
    private val json: Json,
    private val clock: Clock,
) {
    suspend fun submit(familyId: String, write: NetworkWrite) {
        db.withTransaction {
            applyLocally(familyId, write)
            outboxDao.enqueue(
                OutboxEntity(
                    familyId = familyId,
                    tableName = write.table,
                    kind = write.kind.name,
                    rowId = write.rowId,
                    payload = write.body.toString(),
                    createdAt = clock.millis(),
                ),
            )
        }
    }

    /** Після завантаження знімка сервера накладаємо ще не відправлені зміни. */
    suspend fun replayPending(familyId: String) {
        db.withTransaction {
            outboxDao.pendingFor(familyId).forEach { applyLocally(familyId, it.toWrite(json)) }
        }
    }

    private suspend fun applyLocally(familyId: String, w: NetworkWrite) {
        when (w.table to w.kind) {
            Tables.GRADES to WriteKind.INSERT ->
                familyDao.upsertGrades(listOf(json.decodeFromJsonElement(NetworkGrade.serializer(), w.body).asEntity(familyId)))

            Tables.GRADES to WriteKind.UPDATE ->
                familyDao.setGradeStatus(
                    id = w.rowId,
                    status = w.body["status"]?.jsonPrimitive?.content ?: "pending",
                    amount = w.body["amount"]?.jsonPrimitive?.intOrNull,
                )

            Tables.GRADES to WriteKind.DELETE -> familyDao.deleteGrade(w.rowId)

            Tables.HOMEWORK to WriteKind.INSERT ->
                familyDao.upsertHomework(listOf(json.decodeFromJsonElement(NetworkHomework.serializer(), w.body).asEntity(familyId)))

            Tables.HOMEWORK to WriteKind.UPDATE ->
                w.body["done"]?.jsonPrimitive?.booleanOrNull?.let { familyDao.setHomeworkDone(w.rowId, it) }

            Tables.HOMEWORK to WriteKind.DELETE -> familyDao.deleteHomework(w.rowId)

            Tables.LEDGER to WriteKind.INSERT ->
                familyDao.upsertLedger(listOf(json.decodeFromJsonElement(NetworkLedger.serializer(), w.body).asEntity(familyId)))

            else -> error("Непідтримувана локальна зміна: ${w.table} ${w.kind}")
        }
    }
}

internal fun OutboxEntity.toWrite(json: Json) = NetworkWrite(
    table = tableName,
    kind = WriteKind.valueOf(kind),
    rowId = rowId,
    body = json.parseToJsonElement(payload).jsonObject,
)

internal fun JsonObject.with(extra: Map<String, JsonElement>): JsonObject {
    val merged: Map<String, JsonElement> = LinkedHashMap<String, JsonElement>(this).apply { putAll(extra) }
    return JsonObject(merged)
}
