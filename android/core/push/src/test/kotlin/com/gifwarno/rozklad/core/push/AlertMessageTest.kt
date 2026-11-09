package com.gifwarno.rozklad.core.push

import com.gifwarno.rozklad.core.model.AlertStatus
import java.time.Instant
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class AlertMessageTest {
    private val valid = mapOf(
        "type" to "alert",
        "uid" to "31",
        "status" to "A",
        "since" to "2026-11-10T08:00:00.000Z",
        "title" to "🚨 Повітряна тривога",
        "body" to "м. Київ. Пройдіть в укриття.",
    )

    @Test
    fun `повне повідомлення від сервера`() {
        val a = parseAlert(valid)!!
        assertEquals(31, a.uid)
        assertEquals(AlertStatus.ACTIVE, a.status)
        assertEquals(Instant.parse("2026-11-10T08:00:00Z"), a.since)
        assertEquals("м. Київ. Пройдіть в укриття.", a.body)
    }

    @Test
    fun `час зі зсувом як у Postgres`() {
        assertEquals(Instant.parse("2026-11-10T08:00:00Z"), parseAlert(valid + ("since" to "2026-11-10T10:00:00+02:00"))!!.since)
    }

    @Test
    fun `відбій без заголовка — спокійний текст за замовчуванням`() {
        val a = parseAlert(valid + ("status" to "N") - "title")!!
        assertEquals(AlertStatus.NONE, a.status)
        assertEquals("✅ Відбій тривоги", a.title)
    }

    @Test
    fun `зламане повідомлення ігноруємо — не лякаємо й не скасовуємо тривогу випадково`() {
        assertNull(parseAlert(valid - "uid"))
        assertNull(parseAlert(valid + ("uid" to "abc")))
        assertNull(parseAlert(valid - "status"))
        assertNull(parseAlert(valid + ("status" to "X")))
    }

    @Test
    fun `невідомий час — null, а не 1970 рік`() {
        assertNull(parseAlert(valid + ("since" to "вчора"))!!.since)
    }
}
