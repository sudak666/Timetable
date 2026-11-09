package com.gifwarno.rozklad.feature.widget

import android.content.Context
import android.os.Build
import androidx.glance.appwidget.GlanceAppWidgetManager
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.updateAll
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.gifwarno.rozklad.core.common.ApplicationScope
import com.gifwarno.rozklad.core.data.repository.ActiveFamily
import dagger.hilt.android.qualifiers.ApplicationContext
import java.time.Duration
import java.time.LocalDateTime
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

/** Системний приймач віджета. При видаленні останнього віджета скасовуємо заплановані оновлення. */
class RozkladWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget = RozkladWidget()

    override fun onDisabled(context: Context) {
        super.onDisabled(context)
        WidgetRefreshScheduler.cancel(context)
    }
}

/**
 * Оновлення рівно на межах уроків (дзвінок, кінець уроку, північ).
 * WorkManager може трохи запізнитися в режимі сну — тому віджет і показує абсолютний час.
 */
internal object WidgetRefreshScheduler {
    private const val WORK = "rozklad-widget-refresh"

    /** Невеликий запас, щоб на момент оновлення урок уже точно змінився. */
    private const val SLACK_SECONDS = 2L
    private const val MIN_DELAY_SECONDS = 15L

    fun scheduleAt(context: Context, now: LocalDateTime, at: LocalDateTime) {
        val delay = (Duration.between(now, at).seconds + SLACK_SECONDS).coerceAtLeast(MIN_DELAY_SECONDS)
        val request = OneTimeWorkRequestBuilder<WidgetRefreshWorker>().setInitialDelay(delay, TimeUnit.SECONDS).build()
        WorkManager.getInstance(context).enqueueUniqueWork(WORK, ExistingWorkPolicy.REPLACE, request)
    }

    fun cancel(context: Context) {
        WorkManager.getInstance(context).cancelUniqueWork(WORK)
    }
}

/** Без залежностей — HiltWorkerFactory повертає null, і WorkManager створює його звичайним способом. */
internal class WidgetRefreshWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        RozkladWidget().updateAll(applicationContext)
        return Result.success()
    }
}

/** Оновлює віджет, щойно змінився розклад, вибрана дитина чи вхід. Без віджетів на екрані — нічого не робить. */
@Singleton
class WidgetUpdater @Inject constructor(
    @param:ApplicationContext private val context: Context,
    private val active: ActiveFamily,
    @param:ApplicationScope private val scope: CoroutineScope,
) {
    @OptIn(FlowPreview::class)
    fun start() {
        // Прев'ю в каталозі (Android 15+). Система обмежує частоту — результат «rate limited» нешкідливий.
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.VANILLA_ICE_CREAM) {
            scope.launch { runCatching { GlanceAppWidgetManager(context).setWidgetPreviews(RozkladWidgetReceiver::class) } }
        }
        scope.launch {
            active.context
                .map { ctx -> Triple(ctx?.child?.userId, ctx?.child?.name, ctx?.schedule) }
                .distinctUntilChanged()
                .debounce(DEBOUNCE_MS)
                .collect { RozkladWidget().updateAll(context) }
        }
    }

    private companion object {
        const val DEBOUNCE_MS = 500L
    }
}
