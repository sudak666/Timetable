package com.gifwarno.rozklad.feature.today

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.gifwarno.rozklad.core.designsystem.component.ButtonShape
import com.gifwarno.rozklad.core.designsystem.component.EmptyState
import com.gifwarno.rozklad.core.designsystem.component.GameProgressBar
import com.gifwarno.rozklad.core.designsystem.component.GameSurface
import com.gifwarno.rozklad.core.designsystem.component.LoadingState
import com.gifwarno.rozklad.core.designsystem.component.NumberTile
import com.gifwarno.rozklad.core.designsystem.component.Pill
import com.gifwarno.rozklad.core.designsystem.component.StrokeText
import com.gifwarno.rozklad.core.designsystem.theme.GameFont
import com.gifwarno.rozklad.core.designsystem.theme.GameTitleStyle
import com.gifwarno.rozklad.core.designsystem.theme.RozkladTheme
import com.gifwarno.rozklad.core.designsystem.theme.TimerTextStyle
import com.gifwarno.rozklad.core.domain.SchoolCalendar
import com.gifwarno.rozklad.core.domain.Timetable
import com.gifwarno.rozklad.core.ui.ChildSelector
import com.gifwarno.rozklad.core.ui.Format
import java.time.LocalDateTime

@Composable
fun TodayRoute(onEditSchedule: () -> Unit, modifier: Modifier = Modifier, viewModel: TodayViewModel = hiltViewModel()) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    when (val s = state) {
        TodayScreenState.Loading -> LoadingState(modifier)

        TodayScreenState.NoChild -> EmptyState(
            emoji = "👨‍👩‍👧",
            title = "Додай дитину в сім’ю",
            message = "Код для дитини — у «Профілі».",
            modifier = modifier,
        )

        is TodayScreenState.NoSchedule -> Column(modifier) {
            ChildSelector(s.children, s.childId, viewModel::selectChild, Modifier.padding(horizontal = 16.dp, vertical = 8.dp))
            EmptyState(
                emoji = "🗓️",
                title = "Розклад ще не заповнено",
                message = if (s.canEdit) {
                    "Додай уроки й дзвінки — і тут з’явиться таймер уроку, рюкзак на завтра та автотерміни домашки."
                } else {
                    "Попроси батьків або ${s.childName} заповнити розклад."
                },
                actionLabel = if (s.canEdit) "Заповнити розклад" else null,
                onAction = if (s.canEdit) onEditSchedule else null,
            )
        }

        is TodayScreenState.Ready -> TodayScreen(
            state = s.today,
            header = { ChildSelector(s.children, s.childId, viewModel::selectChild) },
            onEditSchedule = if (s.canEdit) onEditSchedule else null,
            modifier = modifier,
        )
    }
}

@Composable
internal fun TodayScreen(
    state: TodayUiState,
    modifier: Modifier = Modifier,
    header: @Composable () -> Unit = {},
    onEditSchedule: (() -> Unit)? = null,
) {
    LazyColumn(
        modifier = modifier,
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        item(key = "header") { header() }
        item(key = "now") { NowCardView(state.now) }
        if (state.dayProgress != null) item(key = "progress") { DayProgress(state.dayProgress) }
        item(key = "title") { DayTitle(state.dayTitle, onEditSchedule) }
        item(key = "lessons") { LessonsCard(state.lessons) }
        if (state.backpack.isNotEmpty()) {
            item(key = "bag") { Backpack(state.backpack, state.isToday) }
        }
    }
}

/** Вигляд головної картки: градієнт і великий емодзі. */
private data class NowLook(val colors: List<Color>, val emoji: String)

@Composable
private fun NowCardView(card: NowCard) {
    val c = RozkladTheme.colors
    val look = when (card) {
        is NowCard.Lesson -> NowLook(listOf(Color(0xFFFFC93C), Color(0xFFFF8A1F), Color(0xFFFF4F5E)), subjectEmoji(card.subject))
        is NowCard.Break -> NowLook(listOf(Color(0xFF00C49A), Color(0xFF00705A)), "☕")
        is NowCard.Before -> NowLook(listOf(c.accent, Color(0xFFA29BFE)), "⏰")
        is NowCard.Done -> NowLook(listOf(Color(0xFF4834D4), c.accentEnd), if (card.holidayUntil != null) "🏖️" else "🎉")
    }
    GameSurface(brush = Brush.linearGradient(look.colors), contentColor = Color.White, depth = 6.dp) {
        FloatingEmoji(look.emoji, Modifier.align(Alignment.TopEnd).padding(top = 14.dp, end = 16.dp))
        Column(
            Modifier.fillMaxWidth().padding(20.dp).semantics(mergeDescendants = true) { liveRegion = LiveRegionMode.Polite },
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            when (card) {
                is NowCard.Lesson -> {
                    Caption("Зараз · ${card.number} урок з ${card.total}")
                    Headline(card.subject)
                    Timer(card.secondsLeft)
                    Subline(if (card.next != null) "до дзвінка · далі ${card.next}" else "до дзвінка · останній урок")
                    NowProgress(card.progress)
                }

                is NowCard.Break -> {
                    Caption("Перерва ${card.breakMinutes} хв")
                    Headline("Далі: ${card.nextSubject}")
                    Timer(card.secondsLeft)
                    Subline("до ${card.nextNumber} уроку о ${card.startsAt}")
                    NowProgress(card.progress)
                }

                is NowCard.Before -> {
                    Caption("Сьогодні ${Format.lessons(card.lessonsCount)}")
                    Headline("Перший: ${card.firstSubject}")
                    Timer(card.secondsLeft)
                    Subline("до початку о ${card.startsAt}")
                }

                is NowCard.Done -> {
                    Caption(card.holidayUntil?.let { "Канікули до ${Format.dayShort(it)}" } ?: "Уроки закінчились!")
                    Headline("Далі: ${card.nextDayName}, ${Format.dayShort(card.nextDate)}")
                    Subline("Перший урок — ${card.firstSubject} о ${card.startsAt}")
                }
            }
        }
    }
}

private fun subjectEmoji(subject: String) = Timetable.subject(subject).emoji

/** Емодзі, що повільно «плаває» (анімується лише шар — без перекомпозиції). */
@Composable
private fun FloatingEmoji(emoji: String, modifier: Modifier = Modifier) {
    val t = rememberInfiniteTransition(label = "float")
    val phase = t.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(2000, easing = FastOutSlowInEasing), RepeatMode.Reverse),
        label = "phase",
    )
    Text(
        emoji,
        fontSize = 46.sp,
        modifier = modifier
            .clearAndSetSemantics { }
            .graphicsLayer {
                translationY = (phase.value - 0.5f) * 16.dp.toPx()
                rotationZ = (phase.value - 0.5f) * 10f
            },
    )
}

@Composable
private fun Caption(text: String) = Text(
    text.uppercase(),
    style = MaterialTheme.typography.labelLarge,
    letterSpacing = 1.sp,
    modifier = Modifier.padding(end = 64.dp),
)

@Composable
private fun Headline(text: String) = StrokeText(text, GameTitleStyle, Modifier.padding(end = 56.dp), strokeWidth = 5.dp, maxLines = 2)

@Composable
private fun Timer(seconds: Long) = StrokeText(Format.timer(seconds), TimerTextStyle, strokeWidth = 6.dp)

@Composable
private fun Subline(text: String) = Text(text, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.ExtraBold)

@Composable
private fun NowProgress(value: Float) = GameProgressBar(
    progress = value,
    modifier = Modifier.padding(top = 12.dp),
    fill = SolidColor(Color.White),
    track = Color.Black.copy(alpha = 0.2f),
)

@Composable
private fun DayProgress(progress: Float) {
    GameSurface {
        Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("⚡ Навчальний день", style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f))
                Text("${(progress * 100).toInt()}%", style = MaterialTheme.typography.titleMedium, fontFamily = GameFont)
            }
            GameProgressBar(progress, Modifier.padding(top = 8.dp))
        }
    }
}

@Composable
private fun DayTitle(title: String, onEditSchedule: (() -> Unit)?) {
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 4.dp)) {
        StrokeText(title, GameTitleStyle.copy(fontSize = 22.sp, lineHeight = 28.sp), Modifier.weight(1f), strokeWidth = 4.dp)
        if (onEditSchedule != null) {
            GameSurface(shape = ButtonShape, onClick = onEditSchedule, onClickLabel = "Редагувати розклад") {
                Row(Modifier.padding(horizontal = 12.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Outlined.Edit, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("Розклад", style = MaterialTheme.typography.labelLarge)
                }
            }
        }
    }
}

@Composable
private fun LessonsCard(lessons: List<LessonRow>) {
    GameSurface {
        Column(Modifier.fillMaxWidth().padding(10.dp)) {
            lessons.forEach { row ->
                LessonItem(row)
                row.breakAfter?.let { BreakItem(it, now = row.breakNow, past = row.state == LessonState.PAST && !row.breakNow) }
            }
        }
    }
}

@Composable
private fun LessonItem(row: LessonRow) {
    val c = RozkladTheme.colors
    val current = row.state == LessonState.CURRENT
    val past = row.state == LessonState.PAST
    val info = Timetable.subject(row.subject)
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(if (current) c.glow else Color.Transparent)
            .padding(horizontal = 8.dp, vertical = 9.dp)
            .semantics(mergeDescendants = true) { },
        verticalAlignment = Alignment.CenterVertically,
    ) {
        NumberTile(row.number, if (past) c.muted else Color(info.color))
        Text(
            row.time,
            modifier = Modifier.padding(start = 10.dp).width(92.dp),
            style = MaterialTheme.typography.bodyMedium.copy(fontFeatureSettings = "tnum"),
            color = if (current) MaterialTheme.colorScheme.onSurface else c.muted,
        )
        Column(Modifier.weight(1f)) {
            Text(
                "${info.emoji} ${row.subject}",
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = if (past) FontWeight.SemiBold else FontWeight.ExtraBold,
                color = if (past) c.muted else MaterialTheme.colorScheme.onSurface,
            )
            if (row.optional) Text("факультатив", style = MaterialTheme.typography.labelSmall, color = c.muted)
        }
        when {
            row.inShelter -> Pill(
                "🛡️ укриття",
                color = MaterialTheme.colorScheme.errorContainer,
                contentColor = MaterialTheme.colorScheme.onErrorContainer,
            )

            current -> Pill("ЗАРАЗ", color = c.accent, contentColor = Color.White)

            past -> Text("✓", color = c.positive, fontWeight = FontWeight.Black)
        }
    }
}

/** Велика перерва — від 15 хвилин (час поїсти). */
private const val LONG_BREAK_MIN = 15

@Composable
private fun BreakItem(minutes: Long, now: Boolean, past: Boolean) {
    val c = RozkladTheme.colors
    val long = minutes >= LONG_BREAK_MIN
    val kind = if (long) "велика перерва" else "перерва"
    val text = when {
        now -> "☕ зараз $kind · $minutes хв"
        long -> "🍎 $kind · $minutes хв"
        else -> "$kind · $minutes хв"
    }
    Box(Modifier.fillMaxWidth().padding(vertical = 2.dp), contentAlignment = Alignment.Center) {
        Pill(
            text = text,
            color = if (now) c.positive else c.soft,
            contentColor = when {
                now -> Color.White
                long && !past -> c.positive
                else -> c.muted
            },
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun Backpack(subjects: List<String>, isToday: Boolean) {
    GameSurface {
        Column(Modifier.fillMaxWidth().padding(16.dp)) {
            Text(
                if (isToday) "🎒 Рюкзак на сьогодні" else "🎒 Що зібрати",
                style = MaterialTheme.typography.titleMedium,
            )
            FlowRow(
                Modifier.padding(top = 10.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                subjects.forEach { s ->
                    val info = Timetable.subject(s)
                    Pill("${info.emoji} $s", color = Color(info.color), contentColor = Color.White)
                }
            }
        }
    }
}

@Preview(showBackground = true, locale = "uk")
@Composable
private fun TodayPreview() {
    RozkladTheme {
        TodayScreen(checkNotNull(buildTodayState(LocalDateTime.parse("2026-11-09T09:40"), SchoolCalendar(Timetable.EXAMPLE))))
    }
}
