package com.gifwarno.rozklad.core.data.sync

import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import com.gifwarno.rozklad.core.database.OutboxDao
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
        for (op in outboxDao.pending()) {
            when (val r = network.write(op.toWrite(json))) {
                WriteResult.Ok -> outboxDao.remove(op.seq)

                WriteResult.Retry -> {
                    outboxDao.markAttempt(op.seq)
                    return false
                }

                // Сервер відхилив — повтор не допоможе; показуємо причину, наступний refresh поверне правду сервера.
                is WriteResult.Rejected -> {
                    lastError.value = "Зміну не збережено: ${r.message}"
                    outboxDao.remove(op.seq)
                }
            }
        }
        true
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
    }
}
