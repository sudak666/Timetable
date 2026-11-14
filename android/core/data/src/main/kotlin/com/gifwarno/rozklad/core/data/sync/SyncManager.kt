package com.gifwarno.rozklad.core.data.sync

import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import com.gifwarno.rozklad.core.data.UserMessage
import com.gifwarno.rozklad.core.database.OutboxDao
import com.gifwarno.rozklad.core.database.OutboxEntity
import com.gifwarno.rozklad.core.model.SyncStatus
import com.gifwarno.rozklad.core.network.RozkladNetworkDataSource
import com.gifwarno.rozklad.core.network.model.WriteResult
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Provider
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonObject

/** Відправляє офлайн-чергу на сервер по порядку. Безпечно викликати будь-коли й скільки завгодно. */
@Singleton
internal class SyncManager @Inject constructor(
    private val network: RozkladNetworkDataSource,
    private val outboxDao: OutboxDao,
    private val json: Json,
    private val workManager: Provider<WorkManager>,
) {
    private val mutex = Mutex()
    private val lastError = MutableStateFlow<String?>(null)

    val status: Flow<SyncStatus> = combine(outboxDao.observeCount(), lastError, ::SyncStatus)

    /** @return true — черга порожня; false — лишилися зміни (немає мережі). */
    suspend fun flush(): Boolean = mutex.withLock {
        // Голову черги беремо щоразу заново: збереження розкладу може оновити версію в наступних записах.
        while (true) {
            val op = outboxDao.pending().firstOrNull() ?: break
            when (val r = network.write(op.toWrite(json))) {
                WriteResult.Ok -> outboxDao.remove(op.seq)

                is WriteResult.Saved -> {
                    outboxDao.remove(op.seq)
                    rebase(op, r.version)
                }

                WriteResult.Retry -> {
                    outboxDao.markAttempt(op.seq)
                    return false
                }

                // Сервер відхилив — повтор не допоможе; показуємо причину, наступний refresh поверне правду сервера.
                is WriteResult.Rejected -> {
                    // Без сирого тексту Postgres: дитина має побачити «Недостатньо прав», а не «violates row-level security».
                    lastError.value = "Зміну не збережено: ${UserMessage.of(IllegalStateException(r.message))}"
                    outboxDao.remove(op.seq)
                }
            }
        }
        true
    }

    /**
     * Розклад збережено, у нього нова версія. Наступні зміни цього ж рядка (зроблені, поки запис летів)
     * походять від нашого ж стану — переводимо їх на нову версію, інакше вони конфліктували б самі з собою.
     */
    private suspend fun rebase(saved: OutboxEntity, version: String) {
        outboxDao.setTimetableVersion(saved.rowId, version)
        for (next in outboxDao.pending()) {
            if (next.tableName != saved.tableName || next.rowId != saved.rowId || next.kind != saved.kind) continue
            val body = json.parseToJsonElement(next.payload).jsonObject
            if (VERSION !in body) continue
            outboxDao.setPayload(next.seq, JsonObject(body + (VERSION to JsonPrimitive(version))).toString())
        }
    }

    fun clearError() {
        lastError.value = null
    }

    /** Фонова відправка, коли з'явиться мережа (переживає закриття застосунку). */
    fun requestSync() {
        val request = OneTimeWorkRequestBuilder<SyncWorker>()
            .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
            .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, BACKOFF_SECONDS, TimeUnit.SECONDS)
            .build()
        workManager.get().enqueueUniqueWork(SyncWorker.NAME, ExistingWorkPolicy.KEEP, request)
    }

    fun cancel() {
        workManager.get().cancelUniqueWork(SyncWorker.NAME)
    }

    private companion object {
        const val BACKOFF_SECONDS = 30L
        const val VERSION = "updated_at"
    }
}
