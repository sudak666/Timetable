package com.gifwarno.rozklad.core.push

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.util.Log
import androidx.core.content.ContextCompat
import com.gifwarno.rozklad.core.common.ApplicationScope
import com.gifwarno.rozklad.core.common.Dispatcher
import com.gifwarno.rozklad.core.common.RozkladDispatchers
import com.gifwarno.rozklad.core.data.repository.FamilyRepository
import com.gifwarno.rozklad.core.data.repository.SessionRepository
import com.gifwarno.rozklad.core.datastore.UserPreferencesDataSource
import com.gifwarno.rozklad.core.model.SessionState
import com.gifwarno.rozklad.core.network.RozkladNetworkDataSource
import com.google.firebase.messaging.FirebaseMessaging
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext

/** Канали сповіщень: людина може вимкнути кожен окремо в налаштуваннях Android. */
object PushChannels {
    const val FAMILY = "family"
    const val HOMEWORK = "homework"
    const val ALERTS = "alerts"
}

/**
 * Push-сповіщення через Firebase. Принципи:
 * • вмикає лише сам користувач (перемикач у профілі), токен до того не створюється;
 * • токен прив'язується до того, хто зараз увійшов, і видаляється під час виходу;
 * • без параметрів Firebase у збірці все тихо вимкнено.
 */
@Singleton
class PushManager @Inject constructor(
    @param:ApplicationContext private val context: Context,
    private val session: SessionRepository,
    private val preferences: UserPreferencesDataSource,
    private val network: RozkladNetworkDataSource,
    private val families: FamilyRepository,
    @param:ApplicationScope private val scope: CoroutineScope,
    @param:Dispatcher(RozkladDispatchers.IO) private val io: CoroutineDispatcher,
) : PushSettings {
    override val isAvailable: Boolean = FirebaseSetup.options() != null

    override val enabled: Flow<Boolean> = preferences.pushEnabled

    /** Останній збій реєстрації (немає мережі тощо) — показується в профілі. */
    private val _lastError = MutableStateFlow<String?>(null)
    override val lastError: Flow<String?> = _lastError

    /** Викликається один раз зі старту застосунку. */
    fun start() {
        // Firebase уже ініціалізовано в Application (FirebaseSetup.init); тут — лише якщо його немає.
        if (!FirebaseSetup.init(context)) return
        createChannels()
        scope.launch {
            combine(session.session, preferences.pushEnabled, preferences.alertsPushEnabled) { s, on, alerts ->
                Triple((s as? SessionState.Ready)?.user?.id, on, alerts)
            }
                .distinctUntilChanged()
                .collect { (userId, on, alerts) -> sync(signedIn = userId != null, wanted = on && hasPermission(), alerts = alerts) }
        }
    }

    override suspend fun setEnabled(enabled: Boolean) = preferences.setPushEnabled(enabled)

    override val alertsEnabled: Flow<Boolean> = preferences.alertsPushEnabled

    override suspend fun setAlertsEnabled(enabled: Boolean) = preferences.setAlertsPushEnabled(enabled)

    override fun hasPermission(): Boolean = Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
        ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED

    /** Токен від Firebase (після register()) — прив'язуємо до того, хто зараз увійшов. */
    internal fun onRegistered(token: String) {
        scope.launch(io) {
            val signedIn = session.session.value is SessionState.Ready
            if (!signedIn || !preferences.pushEnabled.first() || !hasPermission()) return@launch
            try {
                network.registerPushToken(token, alerts = preferences.alertsPushEnabled.first())
                preferences.setPushToken(token)
                _lastError.value = null
            } catch (e: CancellationException) {
                throw e
            } catch (@Suppress("TooGenericExceptionCaught") e: Exception) {
                Log.w(TAG, "Не вдалося зареєструвати токен на сервері", e)
                _lastError.value = OFFLINE_MESSAGE
            }
        }
    }

    /** Чи реєстрували ми пристрій у цьому процесі — щоб знати, що треба відв'язати. */
    private var registered = false

    private suspend fun sync(signedIn: Boolean, wanted: Boolean, alerts: Boolean) = withContext(io) {
        try {
            when {
                // Токен прийде в onRegistered(); якщо він уже є — Firebase поверне той самий.
                signedIn && wanted -> {
                    FirebaseMessaging.getInstance().register().await()
                    registered = true
                    // Перемкнули тривоги при вже зареєстрованому токені — повідомляємо сервер одразу.
                    preferences.pushToken.first()?.let { network.registerPushToken(it, alerts) }
                }

                registered || preferences.pushToken.first() != null -> {
                    // Вимкнули або вийшли: прибираємо пристрій із сервера (якщо ще є сесія)
                    // і скасовуємо токен, щоб наступний користувач телефона отримав новий.
                    if (signedIn) preferences.pushToken.first()?.let { network.unregisterPushToken(it) }
                    preferences.setPushToken(null)
                    FirebaseMessaging.getInstance().unregister().await()
                    registered = false
                }

                else -> Unit
            }
            _lastError.value = null
        } catch (e: CancellationException) {
            throw e
        } catch (@Suppress("TooGenericExceptionCaught") e: Exception) {
            // Немає мережі тощо — спробуємо знову під час наступного запуску.
            Log.w(TAG, "Не вдалося налаштувати сповіщення", e)
            _lastError.value = OFFLINE_MESSAGE
        }
    }

    /**
     * Data-повідомлення про тривогу: оновлюємо стан (плашка й віджет підхоплять із Room)
     * і показуємо сповіщення, якщо тривоги на цьому пристрої увімкнено.
     */
    internal suspend fun onAlert(data: Map<String, String>) {
        val alert = parseAlert(data) ?: return
        families.applyAlertPush(alert.uid, alert.status, alert.since)
        if (!preferences.alertsPushEnabled.first()) return
        PushNotifier.show(
            context = context,
            title = alert.title,
            body = alert.body,
            tag = "alert-${alert.uid}",
            url = null,
            channel = PushChannels.ALERTS,
        )
    }

    private fun createChannels() {
        val nm = context.getSystemService(NotificationManager::class.java) ?: return
        nm.createNotificationChannels(
            listOf(
                NotificationChannel(PushChannels.FAMILY, "Оцінки й гроші", NotificationManager.IMPORTANCE_DEFAULT).apply {
                    description = "Нові оцінки, підтвердження, бонуси, виплати й челенджі"
                },
                NotificationChannel(PushChannels.HOMEWORK, "Домашка", NotificationManager.IMPORTANCE_DEFAULT).apply {
                    description = "Нагадування ввечері про домашку на завтра"
                },
                NotificationChannel(PushChannels.ALERTS, "Повітряні тривоги", NotificationManager.IMPORTANCE_HIGH).apply {
                    description = "Початок і відбій тривоги для місця, обраного в профілі. Не замінює офіційні застосунки й сирени."
                },
            ),
        )
    }

    private companion object {
        const val TAG = "RozkladPush"
        const val OFFLINE_MESSAGE = "Не вдалося налаштувати сповіщення — перевір інтернет"
    }
}
