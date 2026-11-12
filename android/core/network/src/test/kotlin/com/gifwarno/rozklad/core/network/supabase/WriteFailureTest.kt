package com.gifwarno.rozklad.core.network.supabase

import org.junit.Assert.assertEquals
import org.junit.Test

class WriteFailureTest {
    @Test
    fun `збій сервера не викидає зміну з черги`() {
        listOf(500, 502, 503, 504).forEach { assertEquals("HTTP $it", WriteFailure.TRANSIENT, writeFailureOf(it)) }
    }

    @Test
    fun `таймаут і ліміт запитів — повторити пізніше`() {
        listOf(408, 425, 429).forEach { assertEquals("HTTP $it", WriteFailure.TRANSIENT, writeFailureOf(it)) }
    }

    @Test
    fun `протермінований токен — оновити сесію й повторити`() {
        assertEquals(WriteFailure.AUTH, writeFailureOf(401))
    }

    @Test
    fun `свідома відмова сервера — відкинути`() {
        // 400 — CHECK/формат, 403 — RLS, 404 — немає таблиці, 409 — конфлікт ключів.
        listOf(400, 403, 404, 409, 422).forEach { assertEquals("HTTP $it", WriteFailure.REJECTED, writeFailureOf(it)) }
    }
}
