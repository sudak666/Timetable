package com.gifwarno.rozklad.feature.homework

import com.gifwarno.rozklad.core.model.Homework
import java.time.LocalDate
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Хто може видалити домашку — так само, як політика hw_del у базі (аудит 14.11.2026, Б4). */
class CanDeleteTest {
    private fun hw(by: String?) = Homework("h", "kid", "Математика", LocalDate.of(2026, 11, 16), "№ 5", done = false, createdBy = by)

    @Test
    fun `батьки видаляють будь-яку`() = assertTrue(canDelete(hw("mom"), isParent = true, me = "dad"))

    @Test
    fun `дитина видаляє свою й стару без автора`() {
        assertTrue(canDelete(hw("kid"), isParent = false, me = "kid"))
        assertTrue(canDelete(hw(null), isParent = false, me = "kid"))
    }

    @Test
    fun `дитина не видаляє задану батьками`() = assertFalse(canDelete(hw("mom"), isParent = false, me = "kid"))
}
