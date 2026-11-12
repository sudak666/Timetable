package com.gifwarno.rozklad.feature.widget

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
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
import androidx.glance.layout.fillMaxHeight
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.height
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
    // Точний розмір: рядки уроків розтягуються під висоту віджета, а не лишають порожнечу.
    override val sizeMode: SizeMode = SizeMode.Exact

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val deps = EntryPointAccessors.fromApplication(context.applicationContext, WidgetEntryPoint::class.java)
        val clock = deps.clock()
        val families = deps.activeFamily().context
        // Коротко чекаємо дані до першого кадру — щоб не блимало «Завантажую».
        // Не встигли — не страшно: стан нижче живий і оновиться, щойно дані прийдуть.
        val initialFamily = withTimeoutOrNull(PREFETCH_MS) {
            val s = deps.session().session.first { it != SessionState.Loading }
            if (s is SessionState.Ready) families.filterNotNull().first() else null
        }

        val open = context.packageManager.getLaunchIntentForPackage(context.packageName)?.let { actionStartActivity(it) }
        provideContent {
            val session by deps.session().session.collectAsState()
            val family by families.collectAsState(initial = initialFamily)
            val now = LocalDateTime.now(clock)
            val state = resolveWidgetState(session, family) { f -> buildWidgetState(now, checkNotNull(f.child).name, f.schedule) }
            val alert = widgetAlertLine(family?.data?.airAlert, clock.instant(), clock.zone)
            // Наступне оновлення — на дзвінку; без даних — повторна спроба скоро, а не опівночі.
            LaunchedEffect(state) {
                val at = if (state == WidgetState.Loading) now.plusSeconds(RETRY_SECONDS) else nextRefreshAt(now, family?.schedule)
                WidgetRefreshScheduler.scheduleAt(context, now, at)
            }
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
        val EXPANDED = DpSize(250.dp, 260.dp)
        const val PREFETCH_MS = 3_000L
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
 * Над списком — окрема плашка з відступом від країв картки (вкладені заокруглення 20 → 14).
 */
@Composable
private fun ColumnScope.Header(title: String, detail: String, alert: String?, fill: Boolean = true) {
    val size = LocalSize.current
    val narrow = size.width < NARROW_WIDTH.dp
    val plate = GlanceModifier
        .fillMaxWidth()
        .background(ImageProvider(R.drawable.widget_header))
        .cornerRadius(if (fill) 20.dp else 14.dp)
        .padding(horizontal = 14.dp, vertical = 10.dp)
    val content: @Composable () -> Unit = {
        Column(
            if (fill) plate.fillMaxHeight() else plate,
            verticalAlignment = if (fill) Alignment.CenterVertically else Alignment.Top,
        ) {
            if (alert != null) {
                Text(alert, style = TextStyle(fontWeight = FontWeight.Bold, fontSize = 13.sp, color = AlertYellow), maxLines = 1)
            }
            Text(
                title,
                style = TextStyle(fontWeight = FontWeight.Bold, fontSize = if (narrow) 15.sp else 17.sp, color = White),
                // Вузький, але високий віджет: довгий заголовок переноситься, а не обрізається «…».
                maxLines = if (narrow && size.height >= TWO_LINE_TITLE_HEIGHT.dp) 2 else 1,
            )
            Text(
                detail,
                style = TextStyle(fontWeight = FontWeight.Medium, fontSize = if (narrow) 12.sp else 13.sp, color = WhiteSoft),
                maxLines = 2,
            )
        }
    }
    if (fill) {
        Box(GlanceModifier.fillMaxWidth().defaultWeight()) { content() }
    } else {
        Box(GlanceModifier.fillMaxWidth().padding(start = INSET.dp, top = INSET.dp, end = INSET.dp)) { content() }
    }
}

@Composable
private fun ColumnScope.Ready(state: WidgetState.Ready, alert: String?) {
    val size = LocalSize.current
    // Список уроків — лише якщо віджет достатньо високий і не надто вузький.
    val showList = size.height >= LIST_MIN_HEIGHT.dp && size.width >= LIST_MIN_WIDTH.dp && state.lessons.isNotEmpty()
    Header("${state.emoji} ${state.headline}", state.detail, alert, fill = !showList)
    if (showList) {
        // Висота рядка — щоб уроки заповнили віджет (без порожнечі знизу), але в розумних межах.
        val narrow = size.width < NARROW_WIDTH.dp
        val headerHeight = HEADER_EST + (if (alert != null) ALERT_EST else 0f) + (if (narrow) NARROW_HEADER_EXTRA else 0f)
        val rowHeight = ((size.height.value - headerHeight - DAY_TITLE_EST) / state.lessons.size).coerceIn(ROW_MIN, ROW_MAX)
        Column(GlanceModifier.fillMaxWidth().defaultWeight().padding(start = 10.dp, end = 10.dp, top = 12.dp, bottom = 6.dp)) {
            Text(
                state.dayTitle + if (state.childName.isNotBlank()) " · ${state.childName}" else "",
                style = TextStyle(fontWeight = FontWeight.Bold, fontSize = 12.sp, color = Muted),
                maxLines = 1,
                modifier = GlanceModifier.padding(start = 4.dp, bottom = 4.dp),
            )
            // Glance Column вміщає не більше 10 дочірніх елементів — уроків може бути до 12, тож лише LazyColumn.
            LazyColumn(GlanceModifier.fillMaxWidth().defaultWeight()) {
                items(state.lessons, itemId = { it.number.toLong() }) { l -> LessonRow(l, rowHeight, showTime = !narrow) }
            }
        }
    }
}

/** [showTime] — колонка часу; у вузькому віджеті її немає, щоб назви предметів не обрізались. */
@Composable
private fun LessonRow(l: WidgetLesson, height: Float, showTime: Boolean) {
    val current = l.state == WidgetLessonState.CURRENT
    val past = l.state == WidgetLessonState.PAST
    // Вузький віджет (без колонки часу): розмір фіксований, щоб назви предметів не обрізались.
    val big = height >= ROW_BIG || !showTime
    val huge = height >= ROW_HUGE && showTime
    val row = GlanceModifier.fillMaxWidth().height(height.dp).padding(horizontal = 4.dp)
    Row(
        if (current) row.background(Highlight).cornerRadius(10.dp) else row,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            GlanceModifier
                .size(if (huge) 30.dp else if (big) 26.dp else 22.dp)
                .background(if (past) Line else ColorProvider(Color(l.color)))
                .cornerRadius(if (huge) 9.dp else if (big) 8.dp else 7.dp),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                "${l.number}",
                style = TextStyle(fontWeight = FontWeight.Bold, fontSize = if (huge) 16.sp else if (big) 14.sp else 12.sp, color = if (past) Muted else White),
            )
        }
        if (showTime) {
            Text(
                l.start,
                style = TextStyle(fontSize = if (huge) 16.sp else if (big) 14.sp else 12.sp, color = Muted),
                modifier = GlanceModifier.width(if (huge) 60.dp else if (big) 54.dp else 46.dp).padding(start = 8.dp),
            )
        }
        Text(
            "${l.emoji} ${l.subject}",
            modifier = if (showTime) GlanceModifier else GlanceModifier.padding(start = 8.dp),
            style = TextStyle(
                fontSize = if (!showTime) 14.sp else if (huge) 17.sp else if (big) 15.sp else 13.sp,
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

// Шапка (~74) + «Завтра · …» (~40) + щонайменше два уроки; нижче — лише шапка на весь віджет.
private const val LIST_MIN_HEIGHT = 170
private const val LIST_MIN_WIDTH = 150
private const val NARROW_WIDTH = 220
private const val TWO_LINE_TITLE_HEIGHT = 90
private const val INSET = 6

// Оцінки висот (dp) для розрахунку рядка; з запасом, щоб останній урок не обрізався.
private const val HEADER_EST = 74f
private const val ALERT_EST = 18f
private const val NARROW_HEADER_EXTRA = 36f // заголовок і деталь можуть зайняти по два рядки
private const val DAY_TITLE_EST = 40f
private const val ROW_MIN = 26f
private const val ROW_MAX = 60f
private const val ROW_BIG = 36f
private const val ROW_HUGE = 50f
