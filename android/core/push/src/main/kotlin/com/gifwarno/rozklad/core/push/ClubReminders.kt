package com.gifwarno.rozklad.core.push

import android.app.AlarmManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.gifwarno.rozklad.core.common.ApplicationScope
import com.gifwarno.rozklad.core.data.repository.ActiveFamily
import com.gifwarno.rozklad.core.datastore.UserPreferencesDataSource
import com.gifwarno.rozklad.core.domain.Clubs
import com.gifwarno.rozklad.core.model.Club
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import java.time.Clock
import java.time.LocalDateTime
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull

/**
 * Локальні нагадування про позашкільні заняття — без сервера й інтернету.
 * Завжди заплановано лише одне найближче нагадування; коли воно спрацює — плануємо наступне.
 * setAndAllowWhileIdle не потребує дозволу на точні будильники: система може зсунути його на кілька хвилин.
 */
@Singleton
class ClubReminders @Inject constructor(
    @param:ApplicationContext private val context: Context,
    private val active: ActiveFamily,
    private val preferences: UserPreferencesDataSource,
    private val clock: Clock,
    @param:ApplicationScope private val scope: CoroutineScope,
) {
    fun start() {
        createChannel()
        scope.launch {
            combine(active.context, preferences.clubReminderMinutes) { ctx, lead -> ctx?.schedule?.clubs.orEmpty() to lead }
                .distinctUntilChanged()
                .collect { (clubs, lead) -> schedule(clubs, lead) }
        }
    }

    /** Після спрацювання, перезавантаження чи оновлення застосунку. */
    suspend fun reschedule() {
        // Одразу після старту процесу даних ще немає (null) — чекаємо їх, а не скасовуємо план.
        val ctx = withTimeoutOrNull(LOAD_TIMEOUT_MS) { active.context.filterNotNull().first() } ?: return
        schedule(ctx.schedule?.clubs.orEmpty(), preferences.clubReminderMinutes.first())
    }

    private fun schedule(clubs: List<Club>, lead: Int) {
        val alarms = context.getSystemService(AlarmManager::class.java) ?: return
        val now = LocalDateTime.now(clock)
        val next = Clubs.nextReminder(now, clubs, lead)
        if (next == null) {
            alarms.cancel(pending(null))
            return
        }
        val (at, club) = next
        val millis = at.atZone(clock.zone).toInstant().toEpochMilli()
        alarms.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, millis, pending(reminderText(club, lead)))
    }

    private fun pending(text: Pair<String, String>?): PendingIntent {
        val intent = Intent(context, ClubReminderReceiver::class.java).setAction(ACTION_REMIND)
        text?.let { (title, body) -> intent.putExtra(EXTRA_TITLE, title).putExtra(EXTRA_BODY, body) }
        return PendingIntent.getBroadcast(context, 0, intent, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
    }

    private fun createChannel() {
        val nm = context.getSystemService(NotificationManager::class.java) ?: return
        nm.createNotificationChannel(
            NotificationChannel(CHANNEL, "Заняття після школи", NotificationManager.IMPORTANCE_DEFAULT).apply {
                description = "Нагадування перед тренуванням чи гуртком — з телефона, без інтернету"
            },
        )
    }

    internal companion object {
        const val CHANNEL = "clubs"
        const val LOAD_TIMEOUT_MS = 10_000L
        const val ACTION_REMIND = "com.gifwarno.rozklad.CLUB_REMINDER"
        const val EXTRA_TITLE = "title"
        const val EXTRA_BODY = "body"

        /** «⚽ Футбол о 16:00» / «Через 30 хв · 📍 Стадіон · 🎒 форма, бутси». */
        fun reminderText(club: Club, lead: Int): Pair<String, String> {
            val start = Clubs.timeText(club).substringBefore('–')
            val body = listOfNotNull(
                "Через $lead хв",
                club.place.takeIf { it.isNotBlank() }?.let { "📍 $it" },
                club.bring.takeIf { it.isNotBlank() }?.let { "🎒 $it" },
            ).joinToString(" · ")
            return "${club.emoji} ${club.title} о $start" to body
        }
    }
}

@EntryPoint
@InstallIn(SingletonComponent::class)
internal interface ClubRemindersEntryPoint {
    fun clubReminders(): ClubReminders
}

/** Показує нагадування й планує наступне; також відновлює план після перезавантаження телефона. */
class ClubReminderReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == ClubReminders.ACTION_REMIND) {
            val title = intent.getStringExtra(ClubReminders.EXTRA_TITLE)
            val body = intent.getStringExtra(ClubReminders.EXTRA_BODY)
            if (title != null && body != null) {
                PushNotifier.show(context, title, body, tag = "club", url = null, channel = ClubReminders.CHANNEL)
            }
        }
        val reminders = EntryPointAccessors.fromApplication(context.applicationContext, ClubRemindersEntryPoint::class.java).clubReminders()
        val done = goAsync()
        CoroutineScope(Dispatchers.Default).launch {
            try {
                reminders.reschedule()
            } finally {
                done.finish()
            }
        }
    }
}
