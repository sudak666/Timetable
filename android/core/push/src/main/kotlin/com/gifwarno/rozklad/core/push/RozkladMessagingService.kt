package com.gifwarno.rozklad.core.push

import android.Manifest
import android.annotation.SuppressLint
import android.app.PendingIntent
import android.content.Context
import android.content.pm.PackageManager
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

/**
 * Коли застосунок закритий, сповіщення показує сама система (канал/іконка з маніфесту).
 * Коли відкритий — Firebase передає їх сюди, і показуємо ми, з тим самим виглядом.
 */
// Lint ще вимагає onNewToken(), але у firebase-messaging 26 він застарів: токен приходить в onRegistered().
@SuppressLint("MissingFirebaseInstanceTokenRefresh")
@AndroidEntryPoint
class RozkladMessagingService : FirebaseMessagingService() {
    @Inject lateinit var push: PushManager

    /** Новий API firebase-messaging 26: токен приходить сюди після FirebaseMessaging.register(). */
    override fun onRegistered(token: String) {
        push.onRegistered(token)
    }

    override fun onMessageReceived(message: RemoteMessage) {
        val n = message.notification ?: return
        PushNotifier.show(
            context = this,
            title = n.title.orEmpty(),
            body = n.body.orEmpty(),
            tag = message.data["tag"] ?: message.messageId.orEmpty(),
            url = message.data["url"],
            channel = message.data["channel"] ?: PushChannels.FAMILY,
        )
    }
}

internal object PushNotifier {
    /** Ключ, яким MainActivity дізнається, яку вкладку відкрити («#grades», «#hw»). */
    const val EXTRA_URL = "url"

    fun show(context: Context, title: String, body: String, tag: String, url: String?, channel: String) {
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED &&
            android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU
        ) {
            return
        }
        val launch = context.packageManager.getLaunchIntentForPackage(context.packageName)?.apply {
            if (url != null) putExtra(EXTRA_URL, url)
        } ?: return
        val pending = PendingIntent.getActivity(
            context,
            tag.hashCode(),
            launch,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val notification = NotificationCompat.Builder(context, channel)
            .setSmallIcon(R.drawable.ic_stat_12)
            .setColor(ContextCompat.getColor(context, R.color.push_accent))
            .setContentTitle(title)
            .setContentText(body)
            .setStyle(NotificationCompat.BigTextStyle().bigText(body))
            .setAutoCancel(true)
            .setContentIntent(pending)
            .build()
        NotificationManagerCompat.from(context).notify(tag, 0, notification)
    }
}
