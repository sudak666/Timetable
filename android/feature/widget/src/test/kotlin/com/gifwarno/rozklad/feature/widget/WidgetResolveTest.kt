package com.gifwarno.rozklad.feature.widget

import com.gifwarno.rozklad.core.model.Role
import com.gifwarno.rozklad.core.model.SessionState
import com.gifwarno.rozklad.core.testing.TestData
import org.junit.Assert.assertEquals
import org.junit.Test

class WidgetResolveTest {
    private val ready = SessionState.Ready(TestData.parentUser, "f1", Role.PARENT)
    private val built = WidgetState.NoSchedule("зібрано")

    @Test
    fun `тайм-аут сесії — завантаження, а не «увійди»`() {
        assertEquals(WidgetState.Loading, resolveWidgetState(null, null) { built })
        assertEquals(WidgetState.Loading, resolveWidgetState(SessionState.Loading, null) { built })
    }

    @Test
    fun `увійшли, але дані сім'ї ще не піднялись — завантаження`() {
        assertEquals(WidgetState.Loading, resolveWidgetState(ready, null) { built })
    }

    @Test
    fun `справді не увійшли — просимо увійти`() {
        assertEquals(WidgetState.SignedOut, resolveWidgetState(SessionState.SignedOut, null) { built })
    }

    @Test
    fun `є сім'я і дитина — будуємо стан`() {
        assertEquals(built, resolveWidgetState(ready, TestData.parentContext()) { built })
    }
}
