package com.gifwarno.rozklad.feature.homework

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.gifwarno.rozklad.core.designsystem.component.ArenaTitle
import com.gifwarno.rozklad.core.designsystem.component.EmptyState
import com.gifwarno.rozklad.core.designsystem.component.GameButton
import com.gifwarno.rozklad.core.designsystem.component.GameFab
import com.gifwarno.rozklad.core.designsystem.component.GameSurface
import com.gifwarno.rozklad.core.designsystem.component.LoadingState
import com.gifwarno.rozklad.core.designsystem.component.Pill
import com.gifwarno.rozklad.core.designsystem.component.RoundCheck
import com.gifwarno.rozklad.core.designsystem.theme.RozkladTheme
import com.gifwarno.rozklad.core.domain.Timetable
import com.gifwarno.rozklad.core.model.Homework
import com.gifwarno.rozklad.core.ui.ChildSelector
import com.gifwarno.rozklad.core.ui.Format
import java.time.LocalDate

@Composable
fun HomeworkRoute(modifier: Modifier = Modifier, viewModel: HomeworkViewModel = hiltViewModel()) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    HomeworkScreen(
        state = state,
        suggestedDue = viewModel::suggestedDue,
        onAdd = viewModel::add,
        onToggle = viewModel::toggle,
        onDelete = viewModel::delete,
        onSelectChild = viewModel::selectChild,
        modifier = modifier,
    )
}

@Composable
internal fun HomeworkScreen(
    state: HomeworkUiState,
    suggestedDue: (String) -> LocalDate,
    onAdd: (String, LocalDate, String) -> Unit,
    onToggle: (Homework) -> Unit,
    onDelete: (Homework) -> Unit,
    onSelectChild: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    var showAdd by rememberSaveable { mutableStateOf(false) }
    Scaffold(
        modifier = modifier,
        containerColor = Color.Transparent,
        contentWindowInsets = WindowInsets(0),
        floatingActionButton = {
            if (state is HomeworkUiState.Ready) GameFab("Завдання", onClick = { showAdd = true })
        },
    ) { padding ->
        when (state) {
            HomeworkUiState.Loading -> LoadingState(Modifier.padding(padding))

            HomeworkUiState.NoChild -> EmptyState("📒", "Немає дитини в сім’ї", Modifier.padding(padding))

            is HomeworkUiState.Ready -> LazyColumn(
                modifier = Modifier.fillMaxSize().padding(padding),
                contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 96.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                item(key = "kids") { ChildSelector(state.children, state.childId, onSelectChild) }
                if (state.overdue.isEmpty() && state.upcoming.isEmpty()) {
                    item(key = "empty") { AllDone() }
                }
                section("🔥 Прострочено", state.overdue, overdue = true, onToggle, onDelete)
                section("📌 Треба зробити", state.upcoming, overdue = false, onToggle, onDelete)
                section("✅ Зроблено", state.done, overdue = false, onToggle, onDelete)
            }
        }
    }
    if (showAdd) {
        AddHomeworkSheet(
            suggestedDue = suggestedDue,
            onDismiss = { showAdd = false },
            onAdd = { s, d, t ->
                onAdd(s, d, t)
                showAdd = false
            },
        )
    }
}

@Composable
private fun AllDone() {
    GameSurface {
        Column(Modifier.fillMaxWidth().padding(20.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Text("🎉", fontSize = 44.sp)
            Text("Усе зроблено!", style = MaterialTheme.typography.titleLarge)
            Text("Можна відпочивати.", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

private fun LazyListScope.section(
    title: String,
    items: List<Homework>,
    overdue: Boolean,
    onToggle: (Homework) -> Unit,
    onDelete: (Homework) -> Unit,
) {
    if (items.isEmpty()) return
    item(key = "t-$title") { ArenaTitle("$title · ${items.size}") }
    item(key = "l-$title") {
        GameSurface {
            Column(Modifier.fillMaxWidth().padding(4.dp)) {
                items.forEachIndexed { i, hw ->
                    if (i > 0) HorizontalDivider(Modifier.padding(horizontal = 12.dp), color = RozkladTheme.colors.line)
                    HomeworkRow(hw, overdue, onToggle, onDelete)
                }
            }
        }
    }
}

@Composable
private fun HomeworkRow(hw: Homework, overdue: Boolean, onToggle: (Homework) -> Unit, onDelete: (Homework) -> Unit) {
    val c = RozkladTheme.colors
    val info = Timetable.subject(hw.subject)
    Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        RoundCheck(hw.done, onToggle = { onToggle(hw) }, label = if (hw.done) "Зроблено: ${hw.text}" else "Позначити зробленим: ${hw.text}")
        Column(Modifier.weight(1f)) {
            Text(
                hw.text,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = if (hw.done) FontWeight.SemiBold else FontWeight.ExtraBold,
                color = if (hw.done) c.muted else MaterialTheme.colorScheme.onSurface,
                textDecoration = if (hw.done) TextDecoration.LineThrough else null,
            )
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 4.dp)) {
                Pill("${info.emoji} ${hw.subject}", color = Color(info.color), contentColor = Color.White)
                Text(
                    "до ${Format.dayShort(hw.due)}",
                    Modifier.padding(start = 8.dp),
                    style = MaterialTheme.typography.bodySmall,
                    fontWeight = if (overdue) FontWeight.ExtraBold else null,
                    color = if (overdue) c.negative else c.muted,
                )
            }
        }
        IconButton(onClick = { onDelete(hw) }) { Icon(Icons.Outlined.Delete, contentDescription = "Видалити", tint = c.muted) }
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
private fun AddHomeworkSheet(suggestedDue: (String) -> LocalDate, onDismiss: () -> Unit, onAdd: (String, LocalDate, String) -> Unit) {
    var subject by rememberSaveable { mutableStateOf(Timetable.SUBJECTS.first().name) }
    var text by rememberSaveable { mutableStateOf("") }
    val due = suggestedDue(subject)
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            Modifier.padding(horizontal = 16.dp).padding(bottom = 16.dp).navigationBarsPadding(),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text("Нове завдання", style = MaterialTheme.typography.titleLarge)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Timetable.SUBJECTS.forEach { s ->
                    val on = s.name == subject
                    GameSurface(
                        shape = RoundedCornerShape(50),
                        color = if (on) Color(s.color) else RozkladTheme.colors.soft,
                        contentColor = if (on) Color.White else MaterialTheme.colorScheme.onSurface,
                        depth = if (on) 3.dp else 0.dp,
                        borderWidth = if (on) 2.dp else 0.dp,
                        onClick = { subject = s.name },
                        modifier = Modifier.padding(bottom = 6.dp),
                    ) {
                        Text(
                            "${s.emoji} ${s.name}",
                            Modifier.padding(horizontal = 12.dp, vertical = 7.dp),
                            style = MaterialTheme.typography.labelLarge,
                        )
                    }
                }
            }
            OutlinedTextField(
                value = text,
                onValueChange = { text = it.take(MAX_TEXT) },
                label = { Text("Що задали") },
                modifier = Modifier.fillMaxWidth(),
                minLines = 2,
            )
            Text("Здати до: ${Format.dayLong(due)} (наступний урок)", style = MaterialTheme.typography.bodyMedium)
            GameButton(
                text = "Додати",
                onClick = { if (text.isNotBlank()) onAdd(subject, due, text) },
                modifier = Modifier.fillMaxWidth(),
                color = if (text.isNotBlank()) RozkladTheme.colors.accent else RozkladTheme.colors.muted,
            )
        }
    }
}

private const val MAX_TEXT = 500
