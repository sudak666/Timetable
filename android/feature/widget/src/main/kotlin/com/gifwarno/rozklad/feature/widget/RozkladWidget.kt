package com.gifwarno.rozklad.feature.widget

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.GlanceTheme
import androidx.glance.LocalSize
import androidx.glance.action.Action
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.SizeMode
import androidx.glance.appwidget.action.actionStartActivity
import androidx.glance.appwidget.cornerRadius
import androidx.glance.appwidget.lazy.LazyColumn
import androidx.glance.appwidget.lazy.items
import androidx.glance.appwidget.provideContent
import androidx.glance.background
import androidx.glance.layout.Alignment
import androidx.glance.layout.Column
import androidx.glance.layout.Row
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.height
import androidx.glance.layout.padding
import androidx.glance.layout.width
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import com.gifwarno.rozklad.core.data.repository.ActiveFamily
import com.gifwarno.rozklad.core.data.repository.FamilyRepository
import com.gifwarno.rozklad.core.data.repository.SessionRepository
import com.gifwarno.rozklad.core.domain.Timetable
import com.gifwarno.rozklad.core.model.SessionState
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent
import java.time.Clock
import java.time.LocalDateTime
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withTimeoutOrNull

/** Віджет створюється системою, не Hilt — залежності беремо через EntryPoint. */
@EntryPoint
@InstallIn(SingletonComponent::class)
internal interface WidgetEntryPoint {
    fun activeFamily(): ActiveFamily

    fun session(): SessionRepository

    fun clock(): Clock

    fun families(): FamilyRepository
}

/** Віджет «що зараз у школі»: маленький — лише поточне, більший — ще й уроки дня. */
class RozkladWidget : GlanceAppWidget() {
    override val sizeMode: SizeMode = SizeMode.Responsive(setOf(COMPACT, EXPANDED))

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val deps = EntryPointAccessors.fromApplication(context.applicationContext, WidgetEntryPoint::class.java)
        // Сесія піднімається з диска асинхронно: чекаємо, доки стане відомо, хто увійшов.
        // Не увійшов — відповідаємо одразу, без очікування даних сім'ї.
        val session = withTimeoutOrNull(SESSION_WAIT_MS) { deps.session().session.first { it != SessionState.Loading } }
        val family = if (session is SessionState.Ready) {
            withTimeoutOrNull(SESSION_WAIT_MS) { deps.activeFamily().context.filterNotNull().first() }
        } else {
            null
        }
        val now = LocalDateTime.now(deps.clock())
        val child = family?.child
        val state = when {
            family == null -> WidgetState.SignedOut
            child == null -> WidgetState.NoSchedule("")
            else -> buildWidgetState(now, child.name, family.schedule)
        }
        val clock = deps.clock()
        val alert = widgetAlertLine(family?.data?.airAlert, clock.instant(), clock.zone)
        WidgetRefreshScheduler.scheduleAt(context, now, nextRefreshAt(now, family?.schedule))

        val open = context.packageManager.getLaunchIntentForPackage(context.packageName)?.let { actionStartActivity(it) }
        provideContent {
            GlanceTheme { WidgetContent(state, open, alert) }
        }
    }

    /** Живе прев'ю в каталозі віджетів (Android 15+): приклад розкладу, без даних користувача. */
    override val previewSizeMode = SizeMode.Responsive(setOf(EXPANDED))

    override suspend fun providePreview(context: Context, widgetCategory: Int) {
        val example = buildWidgetState(PREVIEW_TIME, "Сашко", Timetable.EXAMPLE)
        provideContent { GlanceTheme { WidgetContent(example, null) } }
    }

    private companion object {
        val PREVIEW_TIME: LocalDateTime = LocalDateTime.parse("2026-11-09T09:40")
        val COMPACT = DpSize(110.dp, 48.dp)
        val EXPANDED = DpSize(220.dp, 140.dp)
        const val SESSION_WAIT_MS = 5_000L
    }
}

@Composable
internal fun WidgetContent(state: WidgetState, open: Action?, alert: String? = null) {
    val base = GlanceModifier
        .fillMaxSize()
        .background(GlanceTheme.colors.widgetBackground)
        .cornerRadius(16.dp)
        .padding(12.dp)
    Column(modifier = if (open != null) base.clickable(open) else base) {
        if (alert != null && state != WidgetState.SignedOut) {
            Text(
                alert,
                style = TextStyle(fontWeight = FontWeight.Bold, fontSize = 13.sp, color = GlanceTheme.colors.error),
                maxLines = 1,
            )
        }
        when (state) {
            WidgetState.SignedOut -> Message("Дванадцятка", "Увійди в застосунок, щоб бачити розклад")
            is WidgetState.NoSchedule -> Message("🗓️ Розклад не заповнено", "Відкрий «Сьогодні» → «Розклад»")
            is WidgetState.Ready -> Ready(state)
        }
    }
}

@Composable
private fun Message(title: String, text: String) {
    Column {
        Text(title, style = TextStyle(fontWeight = FontWeight.Bold, fontSize = 15.sp, color = GlanceTheme.colors.onSurface), maxLines = 1)
        Text(text, style = TextStyle(fontSize = 13.sp, color = GlanceTheme.colors.onSurfaceVariant), maxLines = 2)
    }
}

@Composable
private fun Ready(state: WidgetState.Ready) {
    // Список уроків — лише якщо віджет достатньо високий.
    val showList = LocalSize.current.height >= LIST_MIN_HEIGHT.dp && state.lessons.isNotEmpty()
    Column {
        Text(
            "${state.emoji} ${state.headline}",
            style = TextStyle(fontWeight = FontWeight.Bold, fontSize = 16.sp, color = GlanceTheme.colors.onSurface),
            maxLines = 1,
        )
        Text(state.detail, style = TextStyle(fontSize = 13.sp, color = GlanceTheme.colors.onSurfaceVariant), maxLines = 1)
        if (showList) {
            Spacer(GlanceModifier.height(8.dp))
            Text(
                state.dayTitle + if (state.childName.isNotBlank()) " · ${state.childName}" else "",
                style = TextStyle(fontWeight = FontWeight.Medium, fontSize = 12.sp, color = GlanceTheme.colors.primary),
                maxLines = 1,
            )
            // Glance Column вміщає не більше 10 дочірніх елементів — уроків може бути до 12, тож лише LazyColumn.
            LazyColumn(GlanceModifier.fillMaxWidth().defaultWeight()) {
                items(state.lessons, itemId = { it.number.toLong() }) { l -> LessonRow(l) }
            }
        }
    }
}

@Composable
private fun LessonRow(l: WidgetLesson) {
    val color = when (l.state) {
        WidgetLessonState.CURRENT -> GlanceTheme.colors.primary
        WidgetLessonState.PAST -> GlanceTheme.colors.outline
        WidgetLessonState.UPCOMING -> GlanceTheme.colors.onSurface
    }
    val weight = if (l.state == WidgetLessonState.CURRENT) FontWeight.Bold else FontWeight.Normal
    Row(GlanceModifier.fillMaxWidth().padding(top = 2.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(
            l.start,
            style = TextStyle(fontSize = 12.sp, color = GlanceTheme.colors.onSurfaceVariant),
            modifier = GlanceModifier.width(40.dp),
        )
        Text("${l.emoji} ${l.subject}", style = TextStyle(fontSize = 13.sp, fontWeight = weight, color = color), maxLines = 1)
    }
}

private const val LIST_MIN_HEIGHT = 120
