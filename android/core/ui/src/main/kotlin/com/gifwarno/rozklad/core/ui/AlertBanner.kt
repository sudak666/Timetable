package com.gifwarno.rozklad.core.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.gifwarno.rozklad.core.model.AirAlert
import com.gifwarno.rozklad.core.model.AlertStatus
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

/** Що написати на плашці тривоги. null — плашку не показуємо. */
data class AlertBannerText(val active: Boolean, val title: String, val detail: String)

private val HM = DateTimeFormatter.ofPattern("H:mm")
private val DAY_HM = DateTimeFormatter.ofPattern("d MMM, H:mm", Locale.forLanguageTag("uk-UA"))

/** Чиста функція — тестується без Android. «Станом на» — щоб застарілі дані не видавалися за поточні. */
fun alertBannerText(alert: AirAlert?, now: Instant, zone: ZoneId): AlertBannerText? {
    alert ?: return null
    val status = alert.status ?: return null
    if (status == AlertStatus.NONE) return null
    fun time(i: Instant): String {
        val t = i.atZone(zone)
        return if (t.toLocalDate() == now.atZone(zone).toLocalDate()) HM.format(t) else DAY_HM.format(t)
    }
    val details = listOfNotNull(
        alert.since?.let { "з ${time(it)}" },
        alert.checkedAt?.let { "станом на ${time(it)}" },
    ).joinToString(" · ")
    return if (status == AlertStatus.ACTIVE) {
        AlertBannerText(
            true,
            "🚨 Повітряна тривога · ${alert.regionName}",
            listOf(details, "Пройдіть в укриття.").filter {
                it.isNotEmpty()
            }.joinToString(". "),
        )
    } else {
        AlertBannerText(false, "⚠️ Тривога в частині: ${alert.regionName}", details)
    }
}

/**
 * Плашка тривоги над вкладками — «плаваюча» картка з тими самими відступами й кутами, що й решта карток.
 * Тривога — кольором помилки, «частково» — спокійніше.
 */
@Composable
fun AlertBanner(text: AlertBannerText?, modifier: Modifier = Modifier) {
    text ?: return
    Surface(
        modifier.fillMaxWidth().padding(start = 16.dp, top = 8.dp, end = 16.dp),
        shape = MaterialTheme.shapes.medium,
        color = if (text.active) MaterialTheme.colorScheme.errorContainer else MaterialTheme.colorScheme.tertiaryContainer,
        contentColor = if (text.active) MaterialTheme.colorScheme.onErrorContainer else MaterialTheme.colorScheme.onTertiaryContainer,
    ) {
        Column(Modifier.padding(horizontal = 14.dp, vertical = 10.dp).semantics { liveRegion = LiveRegionMode.Polite }) {
            Text(text.title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
            if (text.detail.isNotEmpty()) {
                Text(text.detail, Modifier.padding(top = 2.dp), style = MaterialTheme.typography.bodyMedium)
            }
        }
    }
}
