package com.gifwarno.rozklad.feature.schedule

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CheckBox
import androidx.compose.material.icons.outlined.CheckBoxOutlineBlank
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.KeyboardArrowDown
import androidx.compose.material.icons.outlined.KeyboardArrowUp
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.gifwarno.rozklad.core.designsystem.component.EmptyState
import com.gifwarno.rozklad.core.designsystem.component.GameButton
import com.gifwarno.rozklad.core.designsystem.component.GameSegmented
import com.gifwarno.rozklad.core.designsystem.component.GameSurface
import com.gifwarno.rozklad.core.designsystem.component.GameTopBar
import com.gifwarno.rozklad.core.designsystem.component.LoadingState
import com.gifwarno.rozklad.core.designsystem.component.NumberTile
import com.gifwarno.rozklad.core.designsystem.component.Pill
import com.gifwarno.rozklad.core.designsystem.theme.RozkladTheme
import com.gifwarno.rozklad.core.domain.Timetable
import com.gifwarno.rozklad.core.model.SchoolSchedule
import com.gifwarno.rozklad.core.ui.Format
import java.time.format.DateTimeFormatter

private val HM = DateTimeFormatter.ofPattern("H:mm")

@Composable
fun ScheduleEditorRoute(onClose: () -> Unit, modifier: Modifier = Modifier, viewModel: ScheduleEditorViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    when (val s = state) {
        ScheduleEditorState.Loading -> LoadingState(modifier)

        ScheduleEditorState.NotAllowed -> EmptyState(
            emoji = "🔒",
            title = "Розклад може змінювати дитина або батьки",
            modifier = modifier,
            actionLabel = "Назад",
            onAction = onClose,
        )

        is ScheduleEditorState.Editing -> {
            LaunchedEffect(s.saved) { if (s.saved) onClose() }
            ScheduleEditorScreen(s, remember(viewModel) { viewModel.actions() }, onClose, modifier)
        }
    }
}

@Composable
private fun ScheduleEditorScreen(
    state: ScheduleEditorState.Editing,
    actions: EditorActions,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var tab by rememberSaveable { mutableIntStateOf(0) }
    var confirmExit by rememberSaveable { mutableStateOf(false) }
    val tryClose = { if (state.dirty) confirmExit = true else onClose() }
    BackHandler(onBack = tryClose)

    Column(modifier.fillMaxSize()) {
        GameTopBar(title = "Розклад · ${state.childName}", onBack = tryClose) {
            GameButton("Зберегти", onClick = actions.save, enabled = state.dirty, color = RozkladTheme.colors.positive)
        }
        GameSegmented(
            options = listOf(0 to "📚 Уроки", 1 to "🔔 Дзвінки", 2 to "🏖️ Канікули"),
            selected = tab,
            onSelect = { tab = it },
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
        )
        if (state.errors.isNotEmpty()) {
            Column(
                Modifier
                    .padding(horizontal = 16.dp)
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(14.dp))
                    .background(MaterialTheme.colorScheme.errorContainer)
                    .padding(12.dp),
            ) {
                state.errors.forEach {
                    Text("• $it", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onErrorContainer)
                }
            }
        }
        when (tab) {
            0 -> LessonsTab(state, actions)
            1 -> BellsTab(state.draft, actions)
            else -> HolidaysTab(state.draft, actions)
        }
    }

    if (confirmExit) {
        AlertDialog(
            onDismissRequest = { confirmExit = false },
            title = { Text("Вийти без збереження?") },
            text = { Text("Зміни в розкладі буде втрачено.") },
            confirmButton = {
                TextButton(onClick = {
                    confirmExit = false
                    onClose()
                }) { Text("Вийти") }
            },
            dismissButton = { TextButton(onClick = { confirmExit = false }) { Text("Лишитися") } },
        )
    }
}

@Composable
private fun LessonsTab(state: ScheduleEditorState.Editing, actions: EditorActions) {
    var picker by rememberSaveable { mutableStateOf<Int?>(null) } // -1 — новий урок
    var copyMenu by rememberSaveable { mutableStateOf(false) }
    val bells = state.draft.bells
    val c = RozkladTheme.colors
    LazyColumn(contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item(key = "days") {
            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                itemsIndexed(state.draft.week) { i, lessons ->
                    val on = state.day == i
                    GameSurface(
                        shape = RoundedCornerShape(50),
                        color = if (on) c.accent else c.card,
                        contentColor = if (on) Color.White else MaterialTheme.colorScheme.onSurface,
                        depth = 3.dp,
                        onClick = { actions.selectDay(i) },
                    ) {
                        Text(
                            "${Timetable.DAY_SHORT[i]} · ${lessons.size}",
                            Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
                            style = MaterialTheme.typography.labelLarge,
                        )
                    }
                }
            }
        }
        item(key = "saturday") {
            // Налаштування всього тижня, а не вибраного дня — тому окремо від картки дня.
            // На картці: сірий текст просто на фіолетовому тлі читався як вимкнений.
            GameSurface {
                Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text("Навчання в суботу", style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
                    Switch(checked = state.draft.week.size > SchoolSchedule.MIN_DAYS, onCheckedChange = actions.setSaturday)
                }
            }
        }
        item(key = "lessons") {
            GameSurface {
                Column(Modifier.fillMaxWidth().padding(12.dp)) {
                    Text(Timetable.DAY_NAMES[state.day], style = MaterialTheme.typography.titleLarge)
                    if (state.lessons.isEmpty()) {
                        Text("Уроків ще немає — додай перший.", Modifier.padding(vertical = 12.dp), color = c.muted)
                    }
                    state.lessons.forEachIndexed { i, lesson ->
                        if (i > 0) HorizontalDivider(color = c.line)
                        LessonEditRow(
                            index = i,
                            subject = lesson.subject,
                            optional = lesson.optional,
                            time = bells.getOrNull(i)?.let { "${HM.format(it.start)}–${HM.format(it.end)}" },
                            isLast = i == state.lessons.lastIndex,
                            actions = actions,
                            onPick = { picker = i },
                        )
                    }
                }
            }
        }
        item(key = "add") {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                GameButton("Додати урок", { picker = -1 }, Modifier.fillMaxWidth(), enabled = state.canAddLesson, leading = "＋")
                if (!state.canAddLesson) {
                    GameSurface {
                        Text(
                            "Уроків стільки ж, скільки дзвінків (${bells.size}). Щоб додати ще, додай дзвінок на вкладці «Дзвінки».",
                            Modifier.padding(12.dp),
                            style = MaterialTheme.typography.bodySmall,
                            color = c.muted,
                        )
                    }
                }
                GameButton(
                    "Скопіювати з іншого дня",
                    { copyMenu = true },
                    Modifier.fillMaxWidth(),
                    color = c.card,
                    contentColor = MaterialTheme.colorScheme.onSurface,
                    leading = "📋",
                )
            }
        }
    }

    picker?.let { index ->
        SubjectPickerDialog(
            onDismiss = { picker = null },
            onPick = { subject ->
                if (index < 0) actions.addLesson(subject) else actions.setLesson(index, subject)
                picker = null
            },
        )
    }
    if (copyMenu) {
        AlertDialog(
            onDismissRequest = { copyMenu = false },
            title = { Text("Скопіювати уроки з…") },
            text = {
                Column {
                    state.draft.week.forEachIndexed { i, lessons ->
                        if (i != state.day && lessons.isNotEmpty()) {
                            TextButton(onClick = {
                                actions.copyFrom(i)
                                copyMenu = false
                            }) { Text("${Timetable.DAY_NAMES[i]} (${Format.lessons(lessons.size)})") }
                        }
                    }
                }
            },
            confirmButton = { TextButton(onClick = { copyMenu = false }) { Text("Скасувати") } },
        )
    }
}

@Composable
private fun LessonEditRow(
    index: Int,
    subject: String,
    optional: Boolean,
    time: String?,
    isLast: Boolean,
    actions: EditorActions,
    onPick: () -> Unit,
) {
    val c = RozkladTheme.colors
    val info = Timetable.subject(subject)
    Column(Modifier.padding(vertical = 6.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            NumberTile(index + 1, Color(info.color))
            Column(Modifier.weight(1f).padding(start = 10.dp)) {
                Text("${info.emoji} $subject", style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.ExtraBold)
                val meta = listOfNotNull(time, if (optional) "факультатив" else null).joinToString(" · ")
                if (meta.isNotEmpty()) Text(meta, style = MaterialTheme.typography.bodySmall, color = c.muted, maxLines = 1)
            }
            IconButton(onClick = { actions.moveLesson(index, -1) }, enabled = index > 0) {
                Icon(Icons.Outlined.KeyboardArrowUp, contentDescription = "Вище")
            }
            IconButton(onClick = { actions.moveLesson(index, 1) }, enabled = !isLast) {
                Icon(Icons.Outlined.KeyboardArrowDown, contentDescription = "Нижче")
            }
            IconButton(onClick = { actions.removeLesson(index) }) {
                Icon(Icons.Outlined.Delete, contentDescription = "Видалити урок", tint = c.muted)
            }
        }
        Row(Modifier.padding(start = 34.dp), verticalAlignment = Alignment.CenterVertically) {
            TextButton(onClick = onPick) { Text("Змінити предмет") }
            TextButton(onClick = { actions.toggleOptional(index) }) {
                Icon(
                    if (optional) Icons.Outlined.CheckBox else Icons.Outlined.CheckBoxOutlineBlank,
                    contentDescription = null,
                    modifier = Modifier.padding(end = 6.dp).size(18.dp),
                )
                Text("Факультатив", maxLines = 1)
            }
        }
    }
}
