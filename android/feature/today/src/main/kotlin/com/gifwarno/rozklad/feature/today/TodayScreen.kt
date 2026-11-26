package com.gifwarno.rozklad.feature.today

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.gifwarno.rozklad.core.designsystem.component.ButtonShape
import com.gifwarno.rozklad.core.designsystem.component.CardTitle
import com.gifwarno.rozklad.core.designsystem.component.ConfettiBurst
import com.gifwarno.rozklad.core.designsystem.component.CoverTag
import com.gifwarno.rozklad.core.designsystem.component.EmptyState
import com.gifwarno.rozklad.core.designsystem.component.GameProgressBar
import com.gifwarno.rozklad.core.designsystem.component.GameSurface
import com.gifwarno.rozklad.core.designsystem.component.LoadingState
import com.gifwarno.rozklad.core.designsystem.component.LocalChromePadding
import com.gifwarno.rozklad.core.designsystem.component.NotebookCover
import com.gifwarno.rozklad.core.designsystem.component.Pill
import com.gifwarno.rozklad.core.designsystem.component.TitleText
import com.gifwarno.rozklad.core.designsystem.component.nowHighlight
import com.gifwarno.rozklad.core.designsystem.component.outlinedBackground
import com.gifwarno.rozklad.core.designsystem.component.rememberAnimationsEnabled
import com.gifwarno.rozklad.core.designsystem.component.withChrome
import com.gifwarno.rozklad.core.designsystem.theme.GameTitleStyle
import com.gifwarno.rozklad.core.designsystem.theme.Nunito
import com.gifwarno.rozklad.core.designsystem.theme.RozkladTheme
import com.gifwarno.rozklad.core.designsystem.theme.TimerTextStyle
import com.gifwarno.rozklad.core.domain.Rewards
import com.gifwarno.rozklad.core.domain.SchoolCalendar
import com.gifwarno.rozklad.core.domain.Timetable
import com.gifwarno.rozklad.core.ui.ChildSelector
import com.gifwarno.rozklad.core.ui.Format
import com.gifwarno.rozklad.core.ui.InviteChildState
import java.time.LocalDate
import java.time.LocalDateTime

@Composable
fun TodayRoute(
    onEditSchedule: () -> Unit,
    onOpenPlayer: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: TodayViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    when (val s = state) {
        TodayScreenState.Loading -> LoadingState(modifier.padding(LocalChromePadding.current))

        is TodayScreenState.NoChild -> InviteChildState("🏡", s.isParent, s.inviteCode, modifier.padding(LocalChromePadding.current))

        is TodayScreenState.NoSchedule -> Column(modifier.padding(LocalChromePadding.current)) {
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

        is TodayScreenState.Ready -> {
            // Салют — раз на день після останнього уроку (пам'ятаємо і при переході між вкладками) і за натисканням.
            var burst by remember { mutableIntStateOf(0) }
            var celebratedOn by rememberSaveable { mutableStateOf<String?>(null) }
            val day = s.date.toString()
            LaunchedEffect(s.today.finishedToday, day) {
                if (s.today.finishedToday && celebratedOn != day) {
                    celebratedOn = day
                    burst++
                }
            }
            Box(modifier) {
                TodayScreen(
                    state = s.today,
                    date = s.date,
                    header = { ChildSelector(s.children, s.childId, viewModel::selectChild) },
                    onEditSchedule = if (s.canEdit) onEditSchedule else null,
                    player = s.player,
                    quests = s.quests,
                    week = s.week,
                    weekend = s.weekend,
                    onOpenPlayer = onOpenPlayer,
                    onCelebrate = { burst++ },
                    stars = s.stars,
                    onGiveStar = {
                        viewModel.giveStar()
                        burst++
                    },
                    onTakeStar = viewModel::takeStar,
                    quiz = { onCorrect -> QuizSlot(onCorrect) },
                )
                ConfettiBurst(burst, Modifier.matchParentSize())
            }
        }
    }
}

@Composable
internal fun TodayScreen(
    state: TodayUiState,
    date: LocalDate,
    modifier: Modifier = Modifier,
    header: @Composable () -> Unit = {},
    onEditSchedule: (() -> Unit)? = null,
    player: PlayerStrip? = null,
    quests: List<Quest> = emptyList(),
    onOpenPlayer: () -> Unit = {},
    onCelebrate: () -> Unit = {},
    week: WeekSchedule? = null,
    weekend: String = "",
    stars: StarsStrip = StarsStrip(),
    onGiveStar: () -> Unit = {},
    onTakeStar: (String) -> Unit = {},
    /** Вікторина — окремим слотом: їй потрібна мережа й своя ViewModel (у прев'ю її немає). */
    quiz: @Composable (onCorrect: () -> Unit) -> Unit = {},
) {
    LazyColumn(
        modifier = modifier,
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp).withChrome(),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        item(key = "header") { header() }
        if (player != null) item(key = "player") { PlayerStripView(player, onOpenPlayer) }
        item(key = "now") {
            // Урок → перерва → урок: картка м'яко перетікає в новий вигляд, а не перемикається стрибком.
            // Щосекундний таймер того самого уроку анімацію не запускає (той самий ключ).
            AnimatedContent(
                targetState = state.now,
                transitionSpec = { fadeIn(tween(NOW_IN_MS, delayMillis = NOW_OUT_MS)) togetherWith fadeOut(tween(NOW_OUT_MS)) },
                contentKey = { it.phaseKey() },
                label = "now",
            ) { now -> NowCardView(now, onDoneTap = if (state.finishedToday) onCelebrate else null) }
        }
        if (state.dayProgress != null) item(key = "progress") { DayProgress(state.dayProgress) }
        if (quests.isNotEmpty()) item(key = "quests") { QuestsCard(quests) }
        item(key = "title") { DayTitle(state.dayTitle, onEditSchedule) }
        item(key = "lessons") { LessonsCard(state.lessons) }
        state.afterSchool?.let { after ->
            item(key = "after-title") {
                TitleText(after.title, GameTitleStyle.copy(fontSize = 20.sp, lineHeight = 26.sp))
            }
            item(key = "after") { AfterSchoolCard(after.rows) }
        }
        if (state.backpack.isNotEmpty() || state.clubBag.isNotEmpty()) {
            item(key = "bag") { Backpack(state.backpack, state.isToday, state.clubBag) }
        }
        if (weekend.isNotEmpty()) {
            item(key = "fun-title") {
                TitleText("На перерві", GameTitleStyle.copy(fontSize = 20.sp, lineHeight = 26.sp))
            }
            item(key = "quiz") { quiz(onCelebrate) }
            item(key = "fact") { FactCard() }
            item(key = "weekend") { WeekendStarsCard(weekend, stars, onGive = onGiveStar, onTake = onTakeStar) }
        }
        if (week != null && week.days.isNotEmpty()) {
            item(key = "week-title") {
                TitleText("Розклад на тиждень", GameTitleStyle.copy(fontSize = 20.sp, lineHeight = 26.sp))
            }
            item(key = "week") { WeekCard(week) }
        }
    }
}

/**
 * Вигляд головної картки «Зараз»: градієнт і великий емодзі. [content] — колір тексту, підібраний до градієнта
 * (контраст ≥ 4,5:1 по всій картці; аудит 14.11.2026, В1).
 */
private data class NowLook(val colors: List<Color>, val emoji: String, val content: Color = Color.White)

/** Хто грає: аватар, рівень, досвід і гаманець. Натискання відкриває «Оцінки». */
@Composable
private fun PlayerStripView(p: PlayerStrip, onOpen: () -> Unit) {
    val c = RozkladTheme.colors
    GameSurface(onClick = onOpen, onClickLabel = "Відкрити оцінки") {
        Row(Modifier.fillMaxWidth().padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier
                    .size(48.dp)
                    .background(Brush.linearGradient(listOf(c.gold, c.goldEnd)), CircleShape),
                contentAlignment = Alignment.Center,
            ) { Text(p.avatar, fontSize = 26.sp) }
            Column(Modifier.weight(1f).padding(horizontal = 12.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(p.name, style = MaterialTheme.typography.titleMedium, maxLines = 1, modifier = Modifier.weight(1f, fill = false))
                    Pill("Рів. ${p.level}", Modifier.padding(start = 6.dp), color = c.gold, contentColor = c.ink)
                }
                GameProgressBar(p.xpInLevel / Rewards.XP_PER_LEVEL.toFloat(), Modifier.padding(top = 6.dp), height = 10.dp)
                Text(
                    "${p.xpInLevel}/${Rewards.XP_PER_LEVEL} XP до рівня ${p.level + 1}",
                    style = MaterialTheme.typography.labelSmall,
                    color = c.muted,
                )
            }
            Column(horizontalAlignment = Alignment.End) {
                Text("💰", fontSize = 18.sp)
                Text(Format.balance(p.balance), style = MaterialTheme.typography.titleMedium, fontFamily = Nunito, maxLines = 1)
            }
        }
    }
}

/** Завдання дня з прогресом; серія (на кілька днів) — окремо, поза лічильником дня. */
@Composable
private fun QuestsCard(quests: List<Quest>) {
    val c = RozkladTheme.colors
    val daily = quests.filter { it.daily }
    val ongoing = quests.filterNot { it.daily }
    val allDone = daily.isNotEmpty() && daily.all { it.done }
    GameSurface {
        Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            if (daily.isNotEmpty()) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    CardTitle("Завдання дня", Modifier.weight(1f))
                    Pill(
                        "${daily.count { it.done }}/${daily.size}",
                        color = if (allDone) c.positive else c.soft,
                        contentColor = if (allDone) c.onPositive else c.muted,
                    )
                }
                daily.forEach { QuestRow(it) }
                if (allDone) {
                    Text(
                        "Усі завдання дня виконано — завтра будуть нові",
                        style = MaterialTheme.typography.labelLarge,
                        color = c.positive,
                    )
                }
            }
            if (ongoing.isNotEmpty()) {
                if (daily.isNotEmpty()) Box(Modifier.fillMaxWidth().padding(vertical = 2.dp).height(1.dp).background(c.soft))
                Text("На кілька днів", style = MaterialTheme.typography.labelLarge, color = c.muted)
                ongoing.forEach { QuestRow(it) }
            }
        }
    }
}

@Composable
private fun QuestRow(q: Quest) {
    val c = RozkladTheme.colors
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.semantics(mergeDescendants = true) { }) {
        Text(q.emoji, fontSize = 22.sp)
        Column(Modifier.weight(1f).padding(horizontal = 10.dp)) {
            Text(
                q.title,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.ExtraBold,
                color = if (q.done) c.muted else MaterialTheme.colorScheme.onSurface,
            )
            if (!q.done && q.target > 1) {
                GameProgressBar(
                    q.progress / q.target.toFloat(),
                    Modifier.padding(top = 4.dp),
                    fill = SolidColor(c.positive),
                    height = 10.dp,
                )
            }
        }
        if (q.done) {
            Pill("✓ готово", color = c.positive, contentColor = c.onPositive)
        } else {
            Text("${q.progress}/${q.target}", style = MaterialTheme.typography.labelLarge, color = c.muted)
        }
    }
}

@Composable
private fun NowCardView(card: NowCard, onDoneTap: (() -> Unit)? = null) {
    val c = RozkladTheme.colors
    val inkDeep = lerp(c.accent, Color.Black, INK_SHADE)
    // Урок і «до уроків» — чорнилом, перерва — зелена, «уроки закінчились» — жовтий маркер.
    val look = when (card) {
        is NowCard.Lesson -> NowLook(listOf(c.accent, inkDeep), subjectEmoji(card.subject))
        is NowCard.Break -> NowLook(listOf(Color(0xFF00866B), Color(0xFF00604D)), "☕")
        is NowCard.Before -> NowLook(listOf(c.accent, inkDeep), "⏰")
        is NowCard.Done -> NowLook(listOf(c.gold, c.gold), if (card.holidayUntil != null) "🏖️" else "🎉", MARKER_INK)
    }
    GameSurface(
        brush = Brush.linearGradient(look.colors),
        contentColor = look.content,
        depth = 6.dp,
        onClick = onDoneTap,
        onClickLabel = if (onDoneTap != null) "Ще салют" else null,
    ) {
        FloatingEmoji(look.emoji, Modifier.align(Alignment.TopEnd).padding(top = 14.dp, end = 16.dp))
        // TalkBack: «що зараз» (рядок і назва) — одним вузлом, який сам озвучується, коли змінюється стан
        // (урок → перерва); таймер — окремо й по хвилинах, щоб не зачитувався щосекунди (аудит 21.11.2026, Н2).
        Column(Modifier.fillMaxWidth().padding(20.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            when (card) {
                is NowCard.Lesson -> {
                    Phase("Зараз · ${card.number} урок з ${card.total}", card.subject)
                    Timer(card.secondsLeft, "до дзвінка")
                    Subline(if (card.next != null) "до дзвінка · далі ${card.next}" else "до дзвінка · останній урок")
                    NowProgress(card.progress)
                }

                is NowCard.Break -> {
                    Phase("Перерва ${card.breakMinutes} хв", "Далі: ${card.nextSubject}")
                    Timer(card.secondsLeft, "до уроку")
                    Subline("до ${card.nextNumber} уроку о ${card.startsAt}")
                    NowProgress(card.progress)
                }

                is NowCard.Before -> {
                    Phase("Сьогодні ${Format.lessons(card.lessonsCount)}", "Перший: ${card.firstSubject}")
                    Timer(card.secondsLeft, "до початку уроків")
                    Subline("до початку о ${card.startsAt}")
                }

                is NowCard.Done -> {
                    Phase(
                        card.holidayUntil?.let { "Канікули до ${Format.dayShort(it)}" } ?: "Уроки закінчились!",
                        "Далі: ${card.nextDayName}, ${Format.date(card.nextDate)}",
                    )
                    Subline("Перший урок — ${card.firstSubject} о ${card.startsAt}")
                }
            }
        }
    }
}

/** Рядок і назва стану картки — один вузол для TalkBack, що сам озвучується при зміні. */
@Composable
private fun Phase(caption: String, headline: String) {
    Column(Modifier.semantics(mergeDescendants = true) { liveRegion = LiveRegionMode.Polite }) {
        Caption(caption)
        Headline(headline)
    }
}

private fun subjectEmoji(subject: String) = Timetable.subject(subject).emoji

/** Що саме зараз (урок №, перерва перед уроком №, до уроків, після) — зміна цього ключа і є «новий стан» картки. */
private fun NowCard.phaseKey(): String = when (this) {
    is NowCard.Lesson -> "lesson-$number"
    is NowCard.Break -> "break-$nextNumber"
    is NowCard.Before -> "before"
    is NowCard.Done -> "done"
}

private const val NOW_IN_MS = 320
private const val NOW_OUT_MS = 140

/** Емодзі, що повільно «плаває» (анімується лише шар — без перекомпозиції). */
@Composable
private fun FloatingEmoji(emoji: String, modifier: Modifier = Modifier) {
    // Вимкнені системні анімації (доступність, вестибулярні розлади) — емодзі стоїть на місці, як і салют.
    if (!rememberAnimationsEnabled()) {
        Text(emoji, fontSize = 46.sp, modifier = modifier.clearAndSetSemantics { })
        return
    }
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

/** Рядок над назвою — звичайним текстом (без «надзаголовка» великими літерами). */
@Composable
private fun Caption(text: String) = NowText(text, MaterialTheme.typography.titleSmall, Modifier.padding(end = 64.dp))

@Composable
private fun Headline(text: String) =
    TitleText(text, GameTitleStyle, Modifier.padding(end = 56.dp), color = LocalContentColor.current, maxLines = 2)

/** Таймер: на екрані — секунди, для TalkBack — лише хвилини (і лише коли на нього наведуть). */
@Composable
private fun Timer(seconds: Long, what: String) {
    val minutes = (seconds + SECONDS_IN_MINUTE - 1) / SECONDS_IN_MINUTE
    TitleText(
        Format.timer(seconds),
        TimerTextStyle,
        Modifier.clearAndSetSemantics { contentDescription = "$what залишилось $minutes хв" },
        color = LocalContentColor.current,
    )
}

private const val SECONDS_IN_MINUTE = 60L

@Composable
private fun Subline(text: String) = NowText(text, MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.ExtraBold))

/** Дрібний текст картки «Зараз» — кольором картки. */
@Composable
private fun NowText(text: String, style: TextStyle, modifier: Modifier = Modifier) = Text(text, modifier, style = style)

/** Смужка «замальовується» жовтим маркером (картки уроку й перерви — темні, маркер на них видно). */
@Composable
private fun NowProgress(value: Float) = GameProgressBar(
    progress = value,
    modifier = Modifier.padding(top = 12.dp),
    fill = SolidColor(RozkladTheme.colors.gold),
    track = Color.Black.copy(alpha = 0.2f),
)

@Composable
private fun DayProgress(progress: Float) {
    GameSurface {
        Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                CardTitle("Навчальний день", Modifier.weight(1f))
                Text("${(progress * 100).toInt()}%", style = MaterialTheme.typography.titleMedium, fontFamily = Nunito)
            }
            GameProgressBar(progress, Modifier.padding(top = 8.dp))
        }
    }
}

@Composable
private fun DayTitle(title: String, onEditSchedule: (() -> Unit)?) {
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 4.dp)) {
        TitleText(title, GameTitleStyle.copy(fontSize = 22.sp, lineHeight = 28.sp), Modifier.weight(1f))
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
            .nowHighlight(current, c)
            .padding(horizontal = 8.dp, vertical = 9.dp)
            .semantics(mergeDescendants = true) { },
        verticalAlignment = Alignment.CenterVertically,
    ) {
        NotebookCover(row.number, Color(info.color), faded = past)
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

/** Позашкільні заняття: час, назва, місце; поточне — підсвічене, минулі — сірі з галочкою. */
@Composable
private fun AfterSchoolCard(rows: List<ClubRow>) {
    val c = RozkladTheme.colors
    GameSurface {
        Column(Modifier.fillMaxWidth().padding(10.dp)) {
            rows.forEach { row ->
                val current = row.state == LessonState.CURRENT
                val past = row.state == LessonState.PAST
                Row(
                    Modifier
                        .fillMaxWidth()
                        .nowHighlight(current, c)
                        .padding(horizontal = 8.dp, vertical = 9.dp)
                        .semantics(mergeDescendants = true) { },
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(row.emoji, style = MaterialTheme.typography.titleLarge)
                    Text(
                        row.time,
                        modifier = Modifier.padding(start = 10.dp).width(92.dp),
                        style = MaterialTheme.typography.bodyMedium.copy(fontFeatureSettings = "tnum"),
                        color = if (current) MaterialTheme.colorScheme.onSurface else c.muted,
                    )
                    Column(Modifier.weight(1f)) {
                        Text(
                            row.title,
                            style = MaterialTheme.typography.bodyLarge,
                            fontWeight = if (past) FontWeight.SemiBold else FontWeight.ExtraBold,
                            color = if (past) c.muted else MaterialTheme.colorScheme.onSurface,
                        )
                        if (row.place.isNotBlank()) {
                            Text("📍 ${row.place}", style = MaterialTheme.typography.labelSmall, color = c.muted, maxLines = 1)
                        }
                    }
                    when {
                        current -> Pill("ЗАРАЗ", color = c.accent, contentColor = Color.White)
                        past -> Text("✓", color = c.positive, fontWeight = FontWeight.Black)
                    }
                }
            }
        }
    }
}

/** Наскільки темніший кінець «чорнильного» градієнта. */
private const val INK_SHADE = 0.3f

/** Текст на жовтому маркері. */
private val MARKER_INK = Color(0xFF23262F)

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
                now -> c.onPositive
                long && !past -> c.positive
                else -> c.muted
            },
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun Backpack(subjects: List<String>, isToday: Boolean, clubs: List<ClubBagItem>) {
    GameSurface {
        Column(Modifier.fillMaxWidth().padding(16.dp)) {
            CardTitle(if (isToday) "Рюкзак на сьогодні" else "Що зібрати")
            FlowRow(
                Modifier.padding(top = 10.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                subjects.forEach { s ->
                    val info = Timetable.subject(s)
                    CoverTag("${info.emoji} $s", Color(info.color))
                }
            }
            // Речі для занять — окремими рядками: «що взяти» буває довгим.
            clubs.forEach { c ->
                Text(
                    buildString {
                        append("${c.emoji} ${c.title}")
                        c.day?.let { append(" ($it)") }
                        append(": ${c.bring}")
                    },
                    Modifier.padding(top = 8.dp),
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold,
                )
            }
        }
    }
}

@Preview(showBackground = true, locale = "uk")
@Composable
private fun TodayPreview() {
    RozkladTheme {
        val now = LocalDateTime.parse("2026-11-09T09:40")
        TodayScreen(checkNotNull(buildTodayState(now, SchoolCalendar(Timetable.EXAMPLE))), now.toLocalDate())
    }
}
