package com.gifwarno.rozklad.feature.profile

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class PasswordProblemTest {
    @Test
    fun `короткий пароль`() = assertEquals("Від 6 символів", passwordProblem("12345", "12345"))

    @Test
    fun `другий ще не введено — без помилки, але зберегти не можна`() = assertEquals("", passwordProblem("123456", ""))

    @Test
    fun `не збігаються`() = assertEquals("Паролі не збігаються", passwordProblem("123456", "123457"))

    @Test
    fun `усе добре`() = assertNull(passwordProblem("123456", "123456"))
}
