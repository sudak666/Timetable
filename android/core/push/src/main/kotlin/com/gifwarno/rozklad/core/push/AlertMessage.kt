package com.gifwarno.rozklad.core.push

import com.gifwarno.rozklad.core.model.AlertStatus
import java.time.Instant
import java.time.OffsetDateTime

/** Вміст data-повідомлення про тривогу (див. fcmAlertMessage у supabase/functions/school-push/fcm.ts). */
internal data class AlertMessage(val uid: Int, val status: AlertStatus, val since: Instant?, val title: String, val body: String)

private val CODES = setOf("A", "P", "N")

/** null — повідомлення зламане (немає місця чи статусу): тоді нічого не змінюємо й не показуємо. */
internal fun parseAlert(data: Map<String, String>): AlertMessage? {
    val uid = data["uid"]?.toIntOrNull() ?: return null
    val code = data["status"]?.takeIf { it in CODES } ?: return null
    val status = AlertStatus.fromCode(code)
    val since = data["since"]?.let { s ->
        runCatching { OffsetDateTime.parse(s).toInstant() }.recoverCatching { Instant.parse(s) }.getOrNull()
    }
    val title = data["title"]?.takeIf { it.isNotBlank() }
        ?: if (status == AlertStatus.ACTIVE) "🚨 Повітряна тривога" else "✅ Відбій тривоги"
    return AlertMessage(uid, status, since, title, data["body"].orEmpty())
}
