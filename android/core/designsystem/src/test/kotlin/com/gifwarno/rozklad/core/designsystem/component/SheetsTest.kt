package com.gifwarno.rozklad.core.designsystem.component

import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.SheetState
import androidx.compose.material3.SheetValue
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Test

@OptIn(ExperimentalMaterial3Api::class)
class SheetsTest {
    private fun sheet(values: Set<SheetValue>) = SheetState(values, { 0f }, { 0f }, SheetValue.Hidden, { true })

    @Test
    fun `шторка створюється закритою і вміє розгортатися повністю`() {
        val state = sheet(FullSheetValues)
        assertEquals(SheetValue.Hidden, state.currentValue)
        assertEquals(setOf(SheetValue.Hidden, SheetValue.Expanded), FullSheetValues)
    }

    @Test
    fun `без проміжного стану «наполовину»`() = assertFalse(SheetValue.PartiallyExpanded in FullSheetValues)

    @Test
    fun `помилка версії 18 — без Expanded шторка падає`() {
        // Саме так було передано у v18 (0.2.16): тест показує, що перевірка справжня.
        assertThrows(IllegalArgumentException::class.java) {
            sheet(setOf(SheetValue.Hidden, SheetValue.PartiallyExpanded))
        }
    }
}
