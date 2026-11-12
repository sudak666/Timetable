package com.gifwarno.rozklad.core.network.supabase

import org.junit.Assert.assertEquals
import org.junit.Test

class OAuthCallbackTest {
    @Test
    fun `код із query`() {
        assertEquals(OAuthCallback("abc-123", null), OAuthCallback.parse("ua.dvanadtsiatka://login-callback?code=abc-123"))
    }

    @Test
    fun `помилка з описом — людський текст`() {
        val r = OAuthCallback.parse("ua.dvanadtsiatka://login-callback?error=access_denied&error_description=User+cancelled")
        assertEquals(null, r.code)
        assertEquals("User cancelled", r.error)
    }

    @Test
    fun `параметри у фрагменті теж розуміємо`() {
        assertEquals("x1", OAuthCallback.parse("app://login-callback#code=x1").code)
    }

    @Test
    fun `кирилиця в описі помилки декодується`() {
        assertEquals(
            "Скасовано",
            OAuthCallback.parse("app://login-callback?error_description=%D0%A1%D0%BA%D0%B0%D1%81%D0%BE%D0%B2%D0%B0%D0%BD%D0%BE").error,
        )
    }

    @Test
    fun `зіпсоване відсоткове кодування в сусідньому полі не губить код`() {
        assertEquals(OAuthCallback("ok", null), OAuthCallback.parse("app://login-callback?code=ok&state=100%ZZ"))
    }

    @Test
    fun `зіпсоване значення помилки показуємо як є`() {
        assertEquals("bad%ZZ", OAuthCallback.parse("app://login-callback?error=bad%ZZ").error)
    }

    @Test
    fun `сміття не валить застосунок`() {
        assertEquals(null, OAuthCallback.parse("::::").code)
    }
}
