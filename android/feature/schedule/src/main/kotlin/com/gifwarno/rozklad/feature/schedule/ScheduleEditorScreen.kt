package com.gifwarno.rozklad.feature.schedule

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.KeyboardArrowDown
import androidx.compose.material.icons.outlined.KeyboardArrowUp
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.PrimaryTabRow
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
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
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.gifwarno.rozklad.core.designsystem.component.EmptyState
import com.gifwarno.rozklad.core.designsystem.component.LoadingState
import com.gifwarno.rozklad.core.domain.Timetable
import com.gifwarno.rozklad.core.model.SchoolSchedule
import com.gifwarno.rozklad.core.ui.Format
import com.gifwarno.rozklad.core.ui.SubjectBadge
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

@OptIn(ExperimentalMaterial3Api::class)
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

    Scaffold(
        modifier = modifier,
        contentWindowInsets = WindowInsets(0),
        topBar = {
            TopAppBar(
                title = { Text("Розклад · ${state.childName}") },
                navigationIcon = {
                    IconButton(onClick = tryClose) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Назад") }
                },
                actions = { TextButton(onClick = actions.save, enabled = state.dirty) { Text("Зберегти") } },
                windowInsets = WindowInsets(0),
            )
        },
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            PrimaryTabRow(selectedTabIndex = tab) {
                listOf("Уроки", "Дзвінки", "Канікули").forEachIndexed { i, title ->
                    Tab(selected = tab == i, onClick = { tab = i }, text = { Text(title) })
                }
            }
            if (state.errors.isNotEmpty()) {
                Surface(color = MaterialTheme.colorScheme.errorContainer, modifier = Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(12.dp)) { state.errors.forEach { Text("• $it", style = MaterialTheme.typography.bodySmall) } }
                }
            }
            when (tab) {
                0 -> LessonsTab(state, actions)
                1 -> BellsTab(state.draft, actions)
                else -> HolidaysTab(state.draft, actions)
            }
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
    LazyColumn(contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        item(key = "days") {
            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                itemsIndexed(state.draft.week) { i, lessons ->
                    FilterChip(
                        selected = state.day == i,
                        onClick = { actions.selectDay(i) },
                        label = { Text("${Timetable.DAY_SHORT[i]} · ${lessons.size}") },
                    )
                }
            }
        }
        item(key = "saturday") {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(vertical = 4.dp)) {
                Text("Навчаємось у суботу", modifier = Modifier.weight(1f))
                Switch(checked = state.draft.week.size > SchoolSchedule.MIN_DAYS, onCheckedChange = actions.setSaturday)
            }
        }
        item(key = "title") { Text(Timetable.DAY_NAMES[state.day], style = MaterialTheme.typography.titleLarge) }
        itemsIndexed(state.lessons, key = { i, l -> "${state.day}-$i-${l.subject}" }) { i, lesson ->
            ListItem(
                leadingContent = { SubjectBadge(lesson.subject) },
                headlineContent = {
                    Text(
                        lesson.subject + if (lesson.optional) " (факультатив)" else "",
                    )
                },
                supportingContent = {
                    val b = bells.getOrNull(i)
                    Text("${i + 1} урок" + (b?.let { " · ${HM.format(it.start)}–${HM.format(it.end)}" } ?: ""))
                },
                trailingContent = {
                    Row {
                        IconButton(onClick = { actions.moveLesson(i, -1) }, enabled = i > 0) {
                            Icon(Icons.Outlined.KeyboardArrowUp, contentDescription = "Вище")
                        }
                        IconButton(onClick = { actions.moveLesson(i, 1) }, enabled = i < state.lessons.lastIndex) {
                            Icon(Icons.Outlined.KeyboardArrowDown, contentDescription = "Нижче")
                        }
                        IconButton(onClick = {
                            actions.removeLesson(i)
                        }) { Icon(Icons.Outlined.Delete, contentDescription = "Видалити урок") }
                    }
                },
                modifier = Modifier.fillMaxWidth(),
            )
            Row(Modifier.padding(start = 72.dp)) {
                TextButton(onClick = { picker = i }) { Text("Змінити предмет") }
                TextButton(onClick = { actions.toggleOptional(i) }) { Text(if (lesson.optional) "Обов’язковий" else "Факультатив") }
            }
        }
        item(key = "add") {
            Column(Modifier.padding(top = 8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = { picker = -1 }, enabled = state.canAddLesson, modifier = Modifier.fillMaxWidth()) {
                    Icon(Icons.Filled.Add, contentDescription = null)
                    Text("Додати урок")
                }
                if (!state.canAddLesson) {
                    Text(
                        "Уроків стільки ж, скільки дзвінків (${bells.size}). Щоб додати ще, додай дзвінок на вкладці «Дзвінки».",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                OutlinedButton(onClick = { copyMenu = true }, modifier = Modifier.fillMaxWidth()) { Text("Скопіювати з іншого дня") }
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
