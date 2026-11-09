package com.gifwarno.rozklad.feature.profile

import com.gifwarno.rozklad.core.data.repository.ActiveFamily
import com.gifwarno.rozklad.core.model.Member
import com.gifwarno.rozklad.core.model.Role
import com.gifwarno.rozklad.core.push.PushSettings
import com.gifwarno.rozklad.core.testing.FakeFamilyRepository
import com.gifwarno.rozklad.core.testing.FakeSessionRepository
import com.gifwarno.rozklad.core.testing.MainDispatcherRule
import com.gifwarno.rozklad.core.testing.TestData
import com.gifwarno.rozklad.core.testing.fakePreferences
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

/** Збірка без Firebase. */
private object NoPush : PushSettings {
    override val isAvailable = false
    override val enabled = flowOf(false)
    override val lastError = flowOf<String?>(null)

    override suspend fun setEnabled(enabled: Boolean) = Unit

    override val alertsEnabled = flowOf(false)

    override suspend fun setAlertsEnabled(enabled: Boolean) = Unit

    override fun hasPermission() = true
}

class DeleteAccountTest {
    @get:Rule val main = MainDispatcherRule()

    private val parent2 = Member("p2", Role.PARENT, "Тато", "", 0, "", "")

    @Test
    fun `останній з батьків — попередження про видалення всієї сім'ї й дітей`() {
        val w = deletionWarnings(true, listOf(TestData.parent, TestData.child), TestData.parent.userId, "Сім’я")
        assertTrue(w.any { "буде видалено повністю" in it })
        assertTrue(w.any { TestData.child.name in it })
    }

    @Test
    fun `є другий з батьків — сім'я лишається`() {
        val w = deletionWarnings(true, listOf(TestData.parent, parent2, TestData.child), TestData.parent.userId, "Сім’я")
        assertTrue(w.any { "лишиться другому з батьків" in it })
        assertTrue(w.none { "повністю" in it })
    }

    @Test
    fun `дитина — видаляються лише її дані`() {
        val w = deletionWarnings(false, listOf(TestData.parent, TestData.child), TestData.child.userId, "Сім’я")
        assertTrue(w.any { "Усі твої оцінки" in it })
        assertTrue(w.none { "сім’ю" in it })
    }

    private fun vm(session: FakeSessionRepository): ProfileViewModel {
        val families = FakeFamilyRepository(TestData.familyData())
        return ProfileViewModel(ActiveFamily(session, families, fakePreferences()), families, session, fakePreferences(), NoPush)
    }

    @Test
    fun `без правильного слова видалення не викликається`() = runTest {
        val session = FakeSessionRepository.ready()
        vm(session).deleteAccount("видалити б")
        assertEquals(0, session.deleteCalls)
    }

    @Test
    fun `помилка сервера показується, а акаунт лишається`() = runTest {
        val session = FakeSessionRepository.ready().apply { deleteResult = Result.failure(IllegalStateException("Немає інтернету")) }
        val vm = vm(session)
        vm.deleteAccount(" видалити ")
        assertEquals(1, session.deleteCalls)
        assertEquals(DeleteAccountState.Failed("Немає інтернету"), vm.deleteState.value)
    }
}
