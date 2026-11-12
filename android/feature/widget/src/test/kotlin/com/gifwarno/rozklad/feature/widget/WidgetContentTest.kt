package com.gifwarno.rozklad.feature.widget

import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.glance.appwidget.testing.unit.runGlanceAppWidgetUnitTest
import androidx.glance.testing.unit.hasText
import com.gifwarno.rozklad.core.domain.Timetable
import java.time.LocalDateTime
import kotlin.time.Duration.Companion.seconds
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** UI віджета: усі уроки потрапляють у віджет (Glance Column обрізає понад 10 елементів — тому LazyColumn). */
// Robolectric ще не має образу Android 37 (наш compileSdk) — тестуємо на 35.
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class WidgetContentTest {
    // Перший тест класу холодно запускає Robolectric (буває ~30 с при паралельній збірці) — 2 с за замовчуванням замало.
    private companion object {
        val COLD_START = 60.seconds
    }

    // Понеділок прикладу — 7 уроків, 7-й (факультатив) раніше обрізався.
    private val monday = buildWidgetState(LocalDateTime.parse("2026-11-09T09:40"), "Сашко", Timetable.EXAMPLE)

    @Test
    fun `великий віджет показує всі уроки дня`() = runGlanceAppWidgetUnitTest(timeout = COLD_START) {
        setAppWidgetSize(DpSize(300.dp, 400.dp))
        provideComposable { WidgetContent(monday, null) }
        onAllNodes(hasText("Всесв. історія")).assertCountEquals(2) // заголовок + рядок у списку
        onNode(hasText("ЗБД")).assertDoesNotExist()
        onAllNodes(hasText("Математика")).assertCountEquals(2)
        onNode(hasText("13:35")).assertExists()
        onNode(hasText("14:30")).assertExists()
    }

    @Test
    fun `малий віджет — лише що зараз, без списку`() = runGlanceAppWidgetUnitTest(timeout = COLD_START) {
        setAppWidgetSize(DpSize(180.dp, 60.dp))
        provideComposable { WidgetContent(monday, null) }
        onNode(hasText("до 10:10")).assertExists()
        onNode(hasText("8:30")).assertDoesNotExist()
    }

    @Test
    fun `широкий в один ряд — без обрізаного списку`() = runGlanceAppWidgetUnitTest(timeout = COLD_START) {
        setAppWidgetSize(DpSize(373.dp, 124.dp))
        provideComposable { WidgetContent(monday, null) }
        onNode(hasText("до 10:10")).assertExists()
        onNode(hasText("8:30")).assertDoesNotExist()
    }

    @Test
    fun `вузький високий віджет теж показує список`() = runGlanceAppWidgetUnitTest(timeout = COLD_START) {
        setAppWidgetSize(DpSize(170.dp, 380.dp))
        provideComposable { WidgetContent(monday, null) }
        onAllNodes(hasText("Математика")).assertCountEquals(2)
        onNode(hasText("13:35")).assertDoesNotExist() // колонку часу прибрано, щоб назви не обрізались
    }

    @Test
    fun `не увійшов — підказка`() = runGlanceAppWidgetUnitTest(timeout = COLD_START) {
        provideComposable { WidgetContent(WidgetState.SignedOut, null) }
        onNode(hasText("Увійди")).assertExists()
    }
}
