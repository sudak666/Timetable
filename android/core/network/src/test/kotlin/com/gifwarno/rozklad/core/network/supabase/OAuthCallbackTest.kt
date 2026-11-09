package com.gifwarno.rozklad.core.network.supabase

import org.junit.Assert.assertEquals
import org.junit.Test

class OAuthCallbackTest {
    @Test
    fun `код із query`() {
        assertEquals(OAuthCallback("abc-123", null), OAuthCallback.parse("com.gifwarno.dvanadtsiatka://login-callback?code=abc-123"))
    }

    @Test
    fun `помилка з описом — людський текст`() {
        val r = OAuthCallback.parse("com.gifwarno.dvanadtsiatka://login-callback?error=access_denied&error_description=User+cancelled")
        assertEquals(null, r.code)
        assertEquals("User cancelled", r.error)
    }

    @Test
    fun `параметри у фрагменті теж розуміємо`() {
        assertEquals("x1", OAuthCallback.parse("app://login-callback#code=x1").code)
    }

    @Test
    fun `сміття не валить застосунок`() {
        assertEquals(null, OAuthCallback.parse("::::").code)
    }
}
