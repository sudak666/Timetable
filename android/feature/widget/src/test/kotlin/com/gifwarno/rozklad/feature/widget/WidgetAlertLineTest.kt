package com.gifwarno.rozklad.feature.widget

import com.gifwarno.rozklad.core.model.AirAlert
import com.gifwarno.rozklad.core.model.AlertStatus
import java.time.Instant
import java.time.ZoneId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class WidgetAlertLineTest {
    private val kyiv = ZoneId.of("Europe/Kyiv")
    private val since = Instant.parse("2026-11-10T08:42:00Z") // 10:42 за Києвом

    private fun alert(status: AlertStatus?, checked: String = "2026-11-10T08:50:00Z") =
        AirAlert(31, "м. Київ", status, since, Instant.parse(checked))

    @Test
    fun `свіжа тривога — коротко, з часом початку`() {
        assertEquals("🚨 Тривога з 10:42", widgetAlertLine(alert(AlertStatus.ACTIVE), Instant.parse("2026-11-10T08:55:00Z"), kyiv))
    }

    @Test
    fun `давні дані — зі «станом на», щоб не видавати їх за поточні`() {
        assertEquals(
            "🚨 Тривога з 10:42 · станом на 10:50",
            widgetAlertLine(alert(AlertStatus.ACTIVE), Instant.parse("2026-11-10T09:30:00Z"), kyiv),
        )
    }

    @Test
    fun `частково, немає тривоги, невідомо`() {
        val now = Instant.parse("2026-11-10T08:55:00Z")
        assertEquals("⚠️ Тривога в частині області", widgetAlertLine(alert(AlertStatus.PARTIAL), now, kyiv))
        assertNull(widgetAlertLine(alert(AlertStatus.NONE), now, kyiv))
        assertNull(widgetAlertLine(alert(null), now, kyiv))
        assertNull(widgetAlertLine(null, now, kyiv))
    }
}
