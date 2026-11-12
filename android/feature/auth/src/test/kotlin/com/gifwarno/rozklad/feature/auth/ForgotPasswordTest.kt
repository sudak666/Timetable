package com.gifwarno.rozklad.feature.auth

import com.gifwarno.rozklad.core.model.SessionState
import com.gifwarno.rozklad.core.testing.FakeSessionRepository
import com.gifwarno.rozklad.core.testing.MainDispatcherRule
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class ForgotPasswordTest {
    @get:Rule
    val main = MainDispatcherRule()

    private val session = FakeSessionRepository(SessionState.SignedOut)
    // Лише після MainDispatcherRule: інакше viewModelScope бере справжній Main і корутина не встигає завершитись.
    private val vm by lazy { AuthViewModel(session) }

    @Test
    fun `без пошти — підказка, лист не надсилається`() {
        vm.forgotPassword()
        assertTrue(vm.state.value.messageIsError)
        assertTrue(session.resetEmails.isEmpty())
    }

    @Test
    fun `з поштою — лист і повідомлення без помилки`() {
        vm.onEmail("kid+review@gmail.com ")
        vm.forgotPassword()
        assertEquals(listOf("kid+review@gmail.com"), session.resetEmails)
        val s = vm.state.value
        assertFalse(s.busy)
        assertFalse(s.messageIsError)
        assertTrue(s.message!!.contains("kid+review@gmail.com"))
    }
}
