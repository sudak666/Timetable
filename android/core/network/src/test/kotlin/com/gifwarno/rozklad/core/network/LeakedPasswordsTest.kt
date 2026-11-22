package com.gifwarno.rozklad.core.network

import org.junit.Assert.assertEquals
import org.junit.Test

class LeakedPasswordsTest {
    @Test
    fun `sha1 — великими літерами, як у Have I Been Pwned`() {
        // SHA-1("password") — відомий вектор.
        assertEquals("5BAA61E4C9B93F3F0682250B6CF8331B7EE68FD8", LeakedPasswords.sha1("password"))
    }

    @Test
    fun `збіг суфікса дає кількість, заповнювач і чужі суфікси — нуль`() {
        val body = listOf(
            "0018A45C4D1DEF81644B54AB7F969B88D65:10",
            "1E4C9B93F3F0682250B6CF8331B7EE68FD8:9545824",
            "FFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFF:0",
        ).joinToString("\r\n")
        assertEquals(9_545_824, LeakedPasswords.leakCount(body, "1e4c9b93f3f0682250b6cf8331b7ee68fd8"))
        assertEquals(0, LeakedPasswords.leakCount(body, "FFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFF"))
        assertEquals(0, LeakedPasswords.leakCount(body, "AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA"))
    }
}
