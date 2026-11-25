package com.gifwarno.rozklad.feature.today

import com.gifwarno.rozklad.core.data.repository.ActiveFamily
import com.gifwarno.rozklad.core.model.LedgerEntry
import com.gifwarno.rozklad.core.model.LedgerKind
import com.gifwarno.rozklad.core.model.LedgerSource
import com.gifwarno.rozklad.core.model.QuizQuestion
import com.gifwarno.rozklad.core.testing.FakeFamilyRepository
import com.gifwarno.rozklad.core.testing.FakeSessionRepository
import com.gifwarno.rozklad.core.testing.MainDispatcherRule
import com.gifwarno.rozklad.core.testing.TestData
import com.gifwarno.rozklad.core.testing.fakePreferences
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

/** Вікторина з сервера й зірки за поведінку від батьків (зауваження дитини 25.11). */
@OptIn(ExperimentalCoroutinesApi::class)
class QuizAndStarsTest {
    @get:Rule val main = MainDispatcherRule()

    private val clock = Clock.fixed(Instant.parse("2026-11-25T12:00:00Z"), ZoneOffset.UTC)
    private val today = LocalDate.parse("2026-11-25")

    private fun active(f: FakeFamilyRepository) = ActiveFamily(FakeSessionRepository.ready(), f, fakePreferences())

    private fun quizFamily() = FakeFamilyRepository(TestData.familyData()).apply {
        quizResult = Result.success(
            listOf(
                QuizQuestion(1, "Скільки буде 7 × 8?", listOf("54", "56", "64", "48")),
                QuizQuestion(2, "Столиця Австралії?", listOf("Сідней", "Канберра")),
            ),
        )
        quizRight = mapOf(1 to "56", 2 to "Канберра")
    }

    @Test
    fun `вікторина — питання з сервера, відповідь перевіряє сервер, у підсумку зароблені гроші`() = runTest {
        val f = quizFamily()
        val vm = QuizViewModel(active(f), f)
        val first = vm.state.value as QuizUiState.Question
        assertEquals(1, first.number)
        assertEquals("Скільки буде 7 × 8?", first.question.question)
        assertEquals(null, first.right)

        vm.answer("56")
        assertEquals("56", (vm.state.value as QuizUiState.Question).right)
        vm.answer("54") // друге натискання під час показу відповіді нічого не надсилає
        advanceTimeBy(1_000)

        vm.answer("Сідней")
        val wrong = vm.state.value as QuizUiState.Question
        assertEquals("Канберра", wrong.right)
        assertEquals("Сідней", wrong.picked)
        advanceTimeBy(1_000)

        assertEquals(QuizUiState.Done(correct = 1, total = 2, earned = 20), vm.state.value)
        assertEquals(listOf(1 to "56", 2 to "Сідней"), f.quizAnswers)
    }

    @Test
    fun `вікторина без інтернету — помилка, «Спробувати ще раз» завантажує знову`() = runTest {
        val f = quizFamily()
        val ok = f.quizResult
        f.quizResult = Result.failure(java.io.IOException("offline"))
        val vm = QuizViewModel(active(f), f)
        assertEquals(QuizUiState.Error, vm.state.value)
        f.quizResult = ok
        vm.start()
        assertTrue(vm.state.value is QuizUiState.Question)
    }

    @Test
    fun `підказка про гроші — для дитини, для батьків і коли ліміт вичерпано`() {
        assertEquals("Правильна відповідь — +20 ₴ (до 3 на день)", QuizPriceNote(isChild = true, price = 20, left = null).text)
        assertEquals("✅ Сьогодні за вікторину вже заплачено — грай для інтересу", QuizPriceNote(true, 20, 0).text)
        assertEquals("Гроші за правильні відповіді отримує дитина: +20 ₴, до 3 на день", QuizPriceNote(false, 20, null).text)
        assertEquals(null, QuizPriceNote(true, 0, null).text)
    }

    @Test
    fun `батьки ставлять зірку — бонус із позначкою «зірка» за ціною з курсу`() = runTest {
        val f = FakeFamilyRepository(TestData.familyData())
        val vm = TodayViewModel(active(f), f, clock)
        vm.giveStar()
        assertEquals(Triple(LedgerKind.BONUS, 50, "⭐ За поведінку"), f.ledgerAdded.single())
        assertEquals(LedgerSource.STAR, f.ledgerSources.single())
    }

    @Test
    fun `більше 5 зірок за день не ставиться`() = runTest {
        val stars = (1..5).map {
            LedgerEntry(
                "s$it",
                TestData.child.userId,
                LedgerKind.BONUS,
                50,
                "",
                today,
                Instant.parse("2026-11-25T0$it:00:00Z"),
                LedgerSource.STAR,
            )
        }
        val f = FakeFamilyRepository(TestData.familyData().copy(ledger = stars))
        val vm = TodayViewModel(active(f), f, clock)
        vm.giveStar()
        assertTrue(f.ledgerAdded.isEmpty())
    }
}
