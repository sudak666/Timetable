package com.gifwarno.rozklad.feature.widget

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.GlanceTheme
import androidx.glance.ImageProvider
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
import androidx.glance.layout.Box
import androidx.glance.layout.Column
import androidx.glance.layout.ColumnScope
import androidx.glance.layout.Row
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.padding
import androidx.glance.layout.size
import androidx.glance.layout.width
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import androidx.glance.unit.ColorProvider
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
        val state = resolveWidgetState(session, family) { f -> buildWidgetState(now, checkNotNull(f.child).name, f.schedule) }
        val clock = deps.clock()
        val alert = widgetAlertLine(family?.data?.airAlert, clock.instant(), clock.zone)
        // Не дочекались даних — пробуємо знову скоро, а не на наступному дзвінку чи опівночі.
        val refreshAt = if (state == WidgetState.Loading) now.plusSeconds(RETRY_SECONDS) else nextRefreshAt(now, family?.schedule)
        WidgetRefreshScheduler.scheduleAt(context, now, refreshAt)

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
        const val SESSION_WAIT_MS = 10_000L
        const val RETRY_SECONDS = 30L
    }
}

/**
 * Ігровий вигляд, як у застосунку: темна «нічна» картка, градієнтна шапка з тим, що зараз,
 * і кольорові номерки уроків. Кольори сталі (не з теми системи) — віджет завжди впізнаваний.
 */
@Composable
internal fun WidgetContent(state: WidgetState, open: Action?, alert: String? = null) {
    val base = GlanceModifier
        .fillMaxSize()
        .background(Card)
        .cornerRadius(20.dp)
    Column(modifier = if (open != null) base.clickable(open) else base) {
        val showAlert = alert != null && state != WidgetState.SignedOut && state != WidgetState.Loading
        when (state) {
            WidgetState.SignedOut -> Header("Дванадцятка", "Увійди в застосунок, щоб бачити розклад", null)
            WidgetState.Loading -> Header("Дванадцятка", "Завантажую розклад…", null)
            is WidgetState.NoSchedule -> Header("🗓️ Розклад не заповнено", "Відкрий «Сьогодні» → «Розклад»", alert.takeIf { showAlert })
            is WidgetState.Ready -> Ready(state, alert.takeIf { showAlert })
        }
    }
}

/**
 * Градієнтна шапка. [fill] — коли під нею нічого показувати (малий віджет, вхід, порожній розклад):
 * тоді вона займає весь віджет, щоб не лишалося порожньої темної області.
 */
@Composable
private fun ColumnScope.Header(title: String, detail: String, alert: String?, fill: Boolean = true) {
    val m = GlanceModifier
        .fillMaxWidth()
        .background(ImageProvider(R.drawable.widget_header))
        .cornerRadius(20.dp)
        .padding(horizontal = 14.dp, vertical = 10.dp)
    Column(
        if (fill) m.defaultWeight() else m,
        verticalAlignment = if (fill) Alignment.CenterVertically else Alignment.Top,
    ) {
        if (alert != null) {
            Text(alert, style = TextStyle(fontWeight = FontWeight.Bold, fontSize = 13.sp, color = AlertYellow), maxLines = 1)
        }
        Text(title, style = TextStyle(fontWeight = FontWeight.Bold, fontSize = 17.sp, color = White), maxLines = 1)
        Text(detail, style = TextStyle(fontWeight = FontWeight.Medium, fontSize = 13.sp, color = WhiteSoft), maxLines = 2)
    }
}

@Composable
private fun ColumnScope.Ready(state: WidgetState.Ready, alert: String?) {
    // Список уроків — лише якщо віджет достатньо високий.
    val showList = LocalSize.current.height >= LIST_MIN_HEIGHT.dp && state.lessons.isNotEmpty()
    Header("${state.emoji} ${state.headline}", state.detail, alert, fill = !showList)
    if (showList) {
        Column(GlanceModifier.fillMaxWidth().defaultWeight().padding(start = 10.dp, end = 10.dp, top = 8.dp, bottom = 6.dp)) {
            Text(
                state.dayTitle + if (state.childName.isNotBlank()) " · ${state.childName}" else "",
                style = TextStyle(fontWeight = FontWeight.Bold, fontSize = 12.sp, color = Muted),
                maxLines = 1,
                modifier = GlanceModifier.padding(start = 4.dp, bottom = 4.dp),
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
    val current = l.state == WidgetLessonState.CURRENT
    val past = l.state == WidgetLessonState.PAST
    val row = GlanceModifier.fillMaxWidth().padding(horizontal = 4.dp, vertical = 3.dp)
    Row(
        if (current) row.background(Highlight).cornerRadius(10.dp) else row,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            GlanceModifier
                .size(22.dp)
                .background(if (past) Line else ColorProvider(Color(l.color)))
                .cornerRadius(7.dp),
            contentAlignment = Alignment.Center,
        ) {
            Text("${l.number}", style = TextStyle(fontWeight = FontWeight.Bold, fontSize = 12.sp, color = if (past) Muted else White))
        }
        Text(
            l.start,
            style = TextStyle(fontSize = 12.sp, color = Muted),
            modifier = GlanceModifier.width(46.dp).padding(start = 8.dp),
        )
        Text(
            "${l.emoji} ${l.subject}",
            style = TextStyle(
                fontSize = 13.sp,
                fontWeight = if (current) FontWeight.Bold else FontWeight.Medium,
                color = if (past) Muted else White,
            ),
            maxLines = 1,
        )
    }
}

// Кольори 1:1 з темною темою застосунку (designsystem Theme.kt).
private val Card = ColorProvider(Color(0xFF1A1B36))
private val Line = ColorProvider(Color(0xFF2A2C4F))
private val Highlight = ColorProvider(Color(0xFF2E2A5C))
private val Muted = ColorProvider(Color(0xFFA3A6CC))
private val White = ColorProvider(Color.White)
private val WhiteSoft = ColorProvider(Color(0xE6FFFFFF))
private val AlertYellow = ColorProvider(Color(0xFFFFE08A))

private const val LIST_MIN_HEIGHT = 120
