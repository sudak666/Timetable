package com.gifwarno.rozklad.feature.grades

import app.cash.turbine.test
import com.gifwarno.rozklad.core.data.repository.ActiveFamily
import com.gifwarno.rozklad.core.datastore.UserPreferencesDataSource
import com.gifwarno.rozklad.core.model.Challenge
import com.gifwarno.rozklad.core.model.GradeStatus
import com.gifwarno.rozklad.core.model.LedgerKind
import com.gifwarno.rozklad.core.testing.FakeFamilyRepository
import com.gifwarno.rozklad.core.testing.FakeSessionRepository
import com.gifwarno.rozklad.core.testing.MainDispatcherRule
import com.gifwarno.rozklad.core.testing.TestData
import com.gifwarno.rozklad.core.testing.fakePreferences
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class GradesViewModelTest {
    @get:Rule val main = MainDispatcherRule()

    private val clock = Clock.fixed(Instant.parse("2026-11-10T12:00:00Z"), ZoneOffset.UTC)

    private fun vm(families: FakeFamilyRepository): GradesViewModel {
        val prefs: UserPreferencesDataSource = fakePreferences()
        return GradesViewModel(ActiveFamily(FakeSessionRepository.ready(), families, prefs), families, clock)
    }

    @Test
    fun `рахує баланс і розділяє оцінки на підтверджені й очікувані`() = runTest {
        val families = FakeFamilyRepository(
            TestData.familyData(
                grades = listOf(
                    TestData.grade("a", 12, "2026-11-10"),
                    TestData.grade("b", 10, "2026-11-09", GradeStatus.PENDING),
                ),
            ),
        )
        vm(families).uiState.test {
            val ready = awaitItem().let { if (it is GradesUiState.Loading) awaitItem() else it } as GradesUiState.Ready
            assertEquals(300, ready.totals.balance)
            assertEquals(300, ready.totals.today)
            assertEquals(listOf("b"), ready.pending.map { it.grade.id })
            assertTrue(ready.pending.single().canApprove)
            assertEquals(listOf("a"), ready.recent.map { it.grade.id })
        }
    }

    @Test
    fun `батько додає вже підтверджену оцінку`() = runTest {
        val families = FakeFamilyRepository(TestData.familyData())
        val vm = vm(families)
        vm.uiState.test {
            skipItems(1)
            vm.addGrade("Математика", 11, java.time.LocalDate.parse("2026-11-10"))
            val added = families.added.single()
            assertTrue(added.byParent)
            assertEquals(11, added.value)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `штраф — від'ємний бонус із поясненням`() = runTest {
        val families = FakeFamilyRepository(TestData.familyData())
        val vm = vm(families)
        vm.uiState.test {
            skipItems(1)
            vm.addFine(150, "")
            assertEquals(Triple(LedgerKind.BONUS, -150, "Штраф"), families.ledgerAdded.single())
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `прогрес челенджу рахує лише підтверджені оцінки з потрібного предмета`() = runTest {
        val ch =
            Challenge(
                "c1", TestData.child.userId, "3 десятки", "Математика", 10, 3, 100,
                LocalDate.parse(
                    "2026-11-03",
                ),
                LocalDate.parse("2026-11-09"),
            )
        val families = FakeFamilyRepository(
            TestData.familyData(
                grades = listOf(
                    TestData.grade("a", 10, "2026-11-04"),
                    TestData.grade("b", 12, "2026-11-05"),
                    TestData.grade("c", 11, "2026-11-06", GradeStatus.PENDING),
                ),
            ).copy(challenges = listOf(ch)),
        )
        vm(families).uiState.test {
            val ready = awaitItem().let { if (it is GradesUiState.Loading) awaitItem() else it } as GradesUiState.Ready
            val item = ready.challenges.single()
            assertEquals(2, item.hits)
            assertTrue(item.over)
            assertEquals(false, item.done)
        }
    }
}
