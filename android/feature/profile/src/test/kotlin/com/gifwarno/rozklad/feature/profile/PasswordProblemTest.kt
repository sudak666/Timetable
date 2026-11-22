package com.gifwarno.rozklad.feature.profile

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class PasswordProblemTest {
    @Test
    fun `короткий пароль`() = assertEquals("Від 8 символів", passwordProblem("1234567", "1234567"))

    @Test
    fun `другий ще не введено — без помилки, але зберегти не можна`() = assertEquals("", passwordProblem("12345678", ""))

    @Test
    fun `не збігаються`() = assertEquals("Паролі не збігаються", passwordProblem("12345678", "12345679"))

    @Test
    fun `усе добре`() = assertNull(passwordProblem("12345678", "12345678"))
}
