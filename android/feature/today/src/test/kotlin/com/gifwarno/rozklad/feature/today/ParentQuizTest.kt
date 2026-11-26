package com.gifwarno.rozklad.feature.today

import com.gifwarno.rozklad.core.data.repository.ActiveFamily
import com.gifwarno.rozklad.core.model.QuizTodayEntry
import com.gifwarno.rozklad.core.model.Role
import com.gifwarno.rozklad.core.model.SessionState
import com.gifwarno.rozklad.core.model.UserAccount
import com.gifwarno.rozklad.core.testing.FakeFamilyRepository
import com.gifwarno.rozklad.core.testing.FakeSessionRepository
import com.gifwarno.rozklad.core.testing.MainDispatcherRule
import com.gifwarno.rozklad.core.testing.TestData
import com.gifwarno.rozklad.core.testing.fakePreferences
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

/** Батьки бачать, як дитина сьогодні відповідала у вікторині (побажання 26.11). */
@OptIn(ExperimentalCoroutinesApi::class)
class ParentQuizTest {
    @get:Rule val main = MainDispatcherRule()

    private val answers = listOf(
        QuizTodayEntry("Скільки буде 7 × 8?", "56", "56", correct = true, paid = 20),
        QuizTodayEntry("Столиця Австралії?", "Сідней", "Канберра", correct = false, paid = 0),
        QuizTodayEntry("Яка планета найбільша?", "Юпітер", "Юпітер", correct = true, paid = 20),
    )

    @Test
    fun `батьки бачать сьогоднішні відповіді вибраної дитини й підсумок`() = runTest {
        val f = FakeFamilyRepository(TestData.familyData()).apply { quizTodayResult = Result.success(answers) }
        val vm = ParentQuizViewModel(ActiveFamily(FakeSessionRepository.ready(), f, fakePreferences()), f)
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { vm.state.collect {} }

        val s = vm.state.value as ParentQuizUiState.Ready
        assertEquals(listOf("k1"), f.quizTodayAsked)
        assertEquals(answers, s.entries)
        assertEquals("Сашко сьогодні: 3 відповіді, 2 правильно — +40 ₴", s.summary)
    }

    @Test
    fun `без інтернету — помилка, «Ще раз» питає сервер знову`() = runTest {
        val f = FakeFamilyRepository(TestData.familyData()).apply { quizTodayResult = Result.failure(java.io.IOException("offline")) }
        val vm = ParentQuizViewModel(ActiveFamily(FakeSessionRepository.ready(), f, fakePreferences()), f)
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { vm.state.collect {} }
        assertEquals(ParentQuizUiState.Error("Сашко"), vm.state.value)

        f.quizTodayResult = Result.success(emptyList())
        vm.refresh()
        assertEquals(ParentQuizUiState.Ready("Сашко", emptyList()), vm.state.value)
        assertEquals(2, f.quizTodayAsked.size)
    }

    @Test
    fun `дитині — гра, а не чужі відповіді`() = runTest {
        val f = FakeFamilyRepository(TestData.familyData())
        val kid = SessionState.Ready(UserAccount("k1", "kid@example.com", "Сашко"), "f1", Role.CHILD)
        val vm = ParentQuizViewModel(ActiveFamily(FakeSessionRepository(kid), f, fakePreferences()), f)
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { vm.state.collect {} }
        assertEquals(ParentQuizUiState.NotParent, vm.state.value)
        assertEquals(emptyList<String>(), f.quizTodayAsked)
    }

    @Test
    fun `відмінювання «відповідь»`() {
        assertEquals(
            listOf("відповідь", "відповіді", "відповідей", "відповідей", "відповідь", "відповіді", "відповідей"),
            listOf(1, 3, 5, 11, 21, 22, 112).map(::answersWord),
        )
    }
}
