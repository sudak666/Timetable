package com.gifwarno.rozklad.core.ui

import com.gifwarno.rozklad.core.model.AirAlert
import com.gifwarno.rozklad.core.model.AlertStatus
import java.time.Instant
import java.time.ZoneId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class AlertBannerTextTest {
    private val kyiv = ZoneId.of("Europe/Kyiv")
    private val now = Instant.parse("2026-11-10T09:05:00Z") // 11:05 за Києвом
    private val since = Instant.parse("2026-11-10T08:42:00Z")

    private fun alert(status: AlertStatus?) = AirAlert(31, "м. Київ", status, since, now)

    @Test
    fun `тривога — заголовок, час початку, станом на і заклик`() {
        val t = alertBannerText(alert(AlertStatus.ACTIVE), now, kyiv)!!
        assertEquals(true, t.active)
        assertEquals("🚨 Повітряна тривога · м. Київ", t.title)
        assertEquals("з 10:42 · станом на 11:05. Пройдіть в укриття.", t.detail)
    }

    @Test
    fun `частково — спокійніша плашка`() {
        val t = alertBannerText(alert(AlertStatus.PARTIAL), now, kyiv)!!
        assertEquals(false, t.active)
        assertEquals("⚠️ Тривога в частині: м. Київ", t.title)
    }

    @Test
    fun `немає тривоги, стан невідомий або місце не обрано — без плашки`() {
        assertNull(alertBannerText(alert(AlertStatus.NONE), now, kyiv))
        assertNull(alertBannerText(alert(null), now, kyiv))
        assertNull(alertBannerText(null, now, kyiv))
    }

    @Test
    fun `тривога з учора — з датою, щоб не плутати`() {
        val t = alertBannerText(alert(AlertStatus.ACTIVE).copy(since = Instant.parse("2026-11-09T20:00:00Z")), now, kyiv)!!
        assertEquals(true, t.detail.startsWith("з 9 лист., 22:00") || t.detail.startsWith("з 9 листопада, 22:00"))
    }
}
