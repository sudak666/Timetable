package com.gifwarno.rozklad.core.data.sync

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.gifwarno.rozklad.core.data.repository.FamilyRepository
import com.gifwarno.rozklad.core.datastore.UserPreferencesDataSource
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import kotlinx.coroutines.flow.first

@HiltWorker
internal class SyncWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted params: WorkerParameters,
    private val sync: SyncManager,
    private val families: FamilyRepository,
    private val preferences: UserPreferencesDataSource,
) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        if (!sync.flush()) return if (runAttemptCount < MAX_ATTEMPTS) Result.retry() else Result.failure()
        preferences.cachedMembership.first()?.let { families.refresh(it.familyId) }
        return Result.success()
    }

    companion object {
        const val NAME = "rozklad-sync"
        private const val MAX_ATTEMPTS = 20
    }
}
