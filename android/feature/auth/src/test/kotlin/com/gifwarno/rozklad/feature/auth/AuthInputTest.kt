package com.gifwarno.rozklad.feature.auth

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AuthInputTest {
    @Test
    fun `пробіли й переноси з пошти прибираються`() {
        assertEquals("kid+review@gmail.com", cleanEmail(" kid+review@gmail.com\n"))
        assertEquals("kid@gmail.com", cleanEmail("kid@ gmail.com"))
    }

    @Test
    fun `пошта з плюсом і паролем від 6 символів вмикає кнопку`() {
        assertTrue(AuthUiState(email = cleanEmail("kid+review@gmail.com "), password = "abcdef").canSubmit)
    }

    @Test
    fun `кілька адрес у полі — кнопка вимкнена, а не тиха помилка входу`() {
        // Так було на телефоні: однорядкове поле показувало першу адресу, а всередині — три.
        val pasted = cleanEmail("a@gmail\na@gmail.com\na@gmail.com")
        assertFalse(AuthUiState(email = pasted, password = "abcdef").canSubmit)
    }
}
