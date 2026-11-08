package com.gifwarno.rozklad.feature.schedule

import com.gifwarno.rozklad.core.data.repository.ActiveFamily
import com.gifwarno.rozklad.core.domain.Timetable
import com.gifwarno.rozklad.core.testing.FakeFamilyRepository
import com.gifwarno.rozklad.core.testing.FakeSessionRepository
import com.gifwarno.rozklad.core.testing.MainDispatcherRule
import com.gifwarno.rozklad.core.testing.TestData
import com.gifwarno.rozklad.core.testing.fakePreferences
import java.time.LocalTime
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class ScheduleEditorViewModelTest {
    @get:Rule val main = MainDispatcherRule()

    private val families = FakeFamilyRepository(TestData.familyData())

    // lazy: створюємо після того, як MainDispatcherRule підмінив Dispatchers.Main
    private val vm by lazy {
        ScheduleEditorViewModel(ActiveFamily(FakeSessionRepository.ready(), families, fakePreferences()), families)
    }

    private val editing get() = vm.state.value as ScheduleEditorState.Editing

    @Test
    fun `без розкладу починаємо з порожнього шаблону з типовими дзвінками`() = runTest {
        assertEquals(Timetable.BLANK, editing.draft)
        assertFalse(editing.dirty)
    }

    @Test
    fun `додати, перемістити й зберегти уроки`() = runTest {
        vm.addLesson("Математика")
        vm.addLesson("Фізика")
        vm.moveLesson(1, -1)
        assertEquals(listOf("Фізика", "Математика"), editing.lessons.map { it.subject })
        assertTrue(editing.dirty)

        vm.save()
        val saved = families.savedSchedules.getValue(TestData.child.userId)
        assertEquals(listOf("Фізика", "Математика"), saved.week[0].map { it.subject })
        assertTrue(editing.saved)
    }

    @Test
    fun `не можна додати урок без дзвінка, а видалення дзвінка обрізає дні`() = runTest {
        repeat(Timetable.TYPICAL_BELLS.size + 2) { vm.addLesson("Урок $it") }
        assertEquals(Timetable.TYPICAL_BELLS.size, editing.lessons.size)
        vm.removeLastBell()
        assertEquals(Timetable.TYPICAL_BELLS.size - 1, editing.lessons.size)
    }

    @Test
    fun `некоректний розклад не зберігається`() = runTest {
        vm.setBell(0, LocalTime.of(9, 0), LocalTime.of(8, 0))
        vm.save()
        assertTrue(editing.errors.isNotEmpty())
        assertTrue(families.savedSchedules.isEmpty())
    }

    @Test
    fun `субота додає шостий день і прибирає його`() = runTest {
        vm.setSaturday(true)
        assertEquals(6, editing.draft.week.size)
        vm.selectDay(5)
        vm.setSaturday(false)
        assertEquals(5, editing.draft.week.size)
        assertEquals(0, editing.day)
    }
}
