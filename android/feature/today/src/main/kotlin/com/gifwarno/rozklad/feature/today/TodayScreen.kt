package com.gifwarno.rozklad.feature.today

import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.gifwarno.rozklad.core.designsystem.component.EmptyState
import com.gifwarno.rozklad.core.designsystem.component.LoadingState
import com.gifwarno.rozklad.core.designsystem.theme.RozkladTheme
import com.gifwarno.rozklad.core.designsystem.theme.TimerTextStyle
import com.gifwarno.rozklad.core.domain.SchoolCalendar
import com.gifwarno.rozklad.core.domain.Timetable
import com.gifwarno.rozklad.core.ui.ChildSelector
import com.gifwarno.rozklad.core.ui.Format
import com.gifwarno.rozklad.core.ui.SubjectBadge
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
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item(key = "header") { header() }
        item(key = "now") { NowCardView(state.now) }
        item(key = "progress") { DayProgress(state.dayProgress) }
        item(key = "title") {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 8.dp)) {
                Text(state.dayTitle, style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f))
                if (onEditSchedule != null) {
                    TextButton(onClick = onEditSchedule) {
                        Icon(Icons.Outlined.Edit, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(4.dp))
                        Text("Розклад")
                    }
                }
            }
        }
        items(state.lessons, key = { "lesson-${it.number}" }) { LessonItem(it) }
        if (state.backpack.isNotEmpty()) {
            item(key = "bag") { Backpack(state.backpack, state.isToday) }
        }
    }
}

@Composable
private fun NowCardView(card: NowCard) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = if (card is NowCard.Break) {
                MaterialTheme.colorScheme.secondaryContainer
            } else {
                MaterialTheme.colorScheme.primaryContainer
            },
        ),
    ) {
        Column(
            Modifier.padding(20.dp).semantics { liveRegion = LiveRegionMode.Polite },
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            when (card) {
                is NowCard.Lesson -> {
                    Caption("Зараз · ${card.number} урок з ${card.total}")
                    Headline("${Timetable.subject(card.subject).emoji} ${card.subject}")
                    Timer(card.secondsLeft)
                    Text(if (card.next != null) "до дзвінка · далі ${card.next}" else "до дзвінка · останній урок")
                    Progress(card.progress)
                }

                is NowCard.Break -> {
                    Caption("☕ Перерва ${card.breakMinutes} хв")
                    Headline("Далі: ${card.nextSubject}")
                    Timer(card.secondsLeft)
                    Text("до ${card.nextNumber} уроку о ${card.startsAt}")
                    Progress(card.progress)
                }

                is NowCard.Before -> {
                    Caption("Сьогодні ${Format.lessons(card.lessonsCount)}")
                    Headline("Перший: ${card.firstSubject}")
                    Timer(card.secondsLeft)
                    Text("до початку о ${card.startsAt}")
                }

                is NowCard.Done -> {
                    Caption(
                        card.holidayUntil?.let { "Канікули до ${Format.dayShort(it)} 🏖️" } ?: "Уроки закінчились 🎉",
                    )
                    Headline("Далі: ${card.nextDayName}, ${Format.dayShort(card.nextDate)}")
                    Text("Перший урок — ${card.firstSubject} о ${card.startsAt}")
                }
            }
        }
    }
}

@Composable
private fun Caption(text: String) = Text(text, style = MaterialTheme.typography.labelLarge)

@Composable
private fun Headline(text: String) = Text(text, style = MaterialTheme.typography.headlineMedium)

@Composable
private fun Timer(seconds: Long) = Text(Format.timer(seconds), style = TimerTextStyle)

@Composable
private fun Progress(value: Float) {
    LinearProgressIndicator(progress = { value }, modifier = Modifier.fillMaxWidth().padding(top = 8.dp))
}

@Composable
private fun DayProgress(progress: Float?) {
    if (progress == null) return
    Column {
        Text("Навчальний день: ${(progress * 100).toInt()}%", style = MaterialTheme.typography.labelMedium)
        Spacer(Modifier.height(4.dp))
        LinearProgressIndicator(progress = { progress }, modifier = Modifier.fillMaxWidth())
    }
}

@Composable
private fun LessonItem(row: LessonRow) {
    val current = row.state == LessonState.CURRENT
    ListItem(
        modifier = Modifier.fillMaxWidth(),
        colors = ListItemDefaults.colors(
            containerColor = if (current) MaterialTheme.colorScheme.surfaceContainerHighest else MaterialTheme.colorScheme.surface,
        ),
        leadingContent = { SubjectBadge(row.subject) },
        headlineContent = {
            Text(
                text = row.subject + if (row.optional) " (факультатив)" else "",
                fontWeight = if (current) FontWeight.Bold else null,
                textDecoration = if (row.state == LessonState.PAST) TextDecoration.LineThrough else null,
                color = if (row.state ==
                    LessonState.PAST
                ) {
                    MaterialTheme.colorScheme.onSurfaceVariant
                } else {
                    MaterialTheme.colorScheme.onSurface
                },
            )
        },
        supportingContent = { Text("${row.number} урок · ${row.time}") },
        trailingContent = if (row.inShelter) {
            { Text("🛡️ укриття", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.error) }
        } else {
            null
        },
    )
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun Backpack(subjects: List<String>, isToday: Boolean) {
    Column(Modifier.padding(top = 8.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("🎒", style = MaterialTheme.typography.titleLarge)
            Spacer(Modifier.width(8.dp))
            Text(if (isToday) "Рюкзак на сьогодні" else "Що зібрати", style = MaterialTheme.typography.titleMedium)
        }
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            subjects.forEach { s -> AssistChip(onClick = {}, label = { Text("${Timetable.subject(s).emoji} $s") }) }
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
