package com.gifwarno.rozklad.feature.widget

import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.glance.appwidget.testing.unit.runGlanceAppWidgetUnitTest
import androidx.glance.testing.unit.hasText
import com.gifwarno.rozklad.core.domain.Timetable
import java.time.LocalDateTime
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** UI віджета: усі уроки потрапляють у віджет (Glance Column обрізає понад 10 елементів — тому LazyColumn). */
// Robolectric ще не має образу Android 37 (наш compileSdk) — тестуємо на 35.
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class WidgetContentTest {
    // Понеділок прикладу — 7 уроків, 7-й (факультатив) раніше обрізався.
    private val monday = buildWidgetState(LocalDateTime.parse("2026-11-09T09:40"), "Сашко", Timetable.EXAMPLE)

    @Test
    fun `великий віджет показує всі уроки дня`() = runGlanceAppWidgetUnitTest {
        setAppWidgetSize(DpSize(300.dp, 400.dp))
        provideComposable { WidgetContent(monday, null) }
        onAllNodes(hasText("Всесв. історія")).assertCountEquals(2) // заголовок + рядок у списку
        onNode(hasText("ЗБД")).assertDoesNotExist()
        onAllNodes(hasText("Математика")).assertCountEquals(2)
        onNode(hasText("13:35")).assertExists()
        onNode(hasText("14:30")).assertExists()
    }

    @Test
    fun `малий віджет — лише що зараз, без списку`() = runGlanceAppWidgetUnitTest {
        setAppWidgetSize(DpSize(180.dp, 60.dp))
        provideComposable { WidgetContent(monday, null) }
        onNode(hasText("до 10:10")).assertExists()
        onNode(hasText("8:30")).assertDoesNotExist()
    }

    @Test
    fun `не увійшов — підказка`() = runGlanceAppWidgetUnitTest {
        provideComposable { WidgetContent(WidgetState.SignedOut, null) }
        onNode(hasText("Увійди")).assertExists()
    }
}
