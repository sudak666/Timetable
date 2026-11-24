package com.gifwarno.rozklad.core.network.supabase

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class RecoverTest {
    @Test
    fun `адреса з поверненням на сайт`() {
        assertEquals(
            "https://x.supabase.co/auth/v1/recover?redirect_to=https%3A%2F%2Fsudak666.github.io%2FTimetable%2F",
            Recover.url("https://x.supabase.co/", "https://sudak666.github.io/Timetable/"),
        )
    }

    @Test
    fun `тіло — лише пошта, без code_challenge (інакше посилання з листа не відкриється на сайті)`() {
        val b = Recover.body("kid+review@gmail.com")
        assertEquals("""{"email":"kid+review@gmail.com"}""", b)
        assertFalse(b.contains("code_challenge"))
    }

    @Test
    fun `помилка Auth — її текст, кривий або порожній JSON — код відповіді`() {
        assertEquals(
            "For security purposes, you can only request this after 42 seconds.",
            Recover.error(
                429,
                """{"code":429,"error_code":"over_email_send_rate_limit","msg":"For security purposes, you can only request this after 42 seconds."}""",
            ),
        )
        assertEquals("email rate limit exceeded", Recover.error(429, """{"error":"email rate limit exceeded"}"""))
        assertEquals("Не вдалося надіслати лист (код 500)", Recover.error(500, "<html>oops</html>"))
        assertEquals("Не вдалося надіслати лист (код 502)", Recover.error(502, """{"msg":""}"""))
    }
}
