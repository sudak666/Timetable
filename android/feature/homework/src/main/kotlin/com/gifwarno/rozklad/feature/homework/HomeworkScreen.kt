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
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
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
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.gifwarno.rozklad.core.designsystem.component.EmptyState
import com.gifwarno.rozklad.core.designsystem.component.LoadingState
import com.gifwarno.rozklad.core.domain.Timetable
import com.gifwarno.rozklad.core.model.Homework
import com.gifwarno.rozklad.core.ui.ChildSelector
import com.gifwarno.rozklad.core.ui.Format
import com.gifwarno.rozklad.core.ui.SubjectBadge
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
        contentWindowInsets = WindowInsets(0),
        floatingActionButton = {
            if (state is HomeworkUiState.Ready) {
                ExtendedFloatingActionButton(
                    onClick = { showAdd = true },
                    icon = { Icon(Icons.Filled.Add, contentDescription = null) },
                    text = { Text("Завдання") },
                )
            }
        },
    ) { padding ->
        when (state) {
            HomeworkUiState.Loading -> LoadingState(Modifier.padding(padding))

            HomeworkUiState.NoChild -> EmptyState("📒", "Немає дитини в сім’ї", Modifier.padding(padding))

            is HomeworkUiState.Ready -> LazyColumn(
                modifier = Modifier.fillMaxSize().padding(padding),
                contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 96.dp),
            ) {
                item(key = "kids") { ChildSelector(state.children, state.childId, onSelectChild) }
                if (state.overdue.isEmpty() && state.upcoming.isEmpty()) {
                    item(key = "empty") {
                        Text(
                            "Усе зроблено 🎉",
                            style = MaterialTheme.typography.titleMedium,
                            modifier = Modifier.padding(vertical = 24.dp),
                        )
                    }
                }
                section("Прострочено", state.overdue, onToggle, onDelete)
                section("Треба зробити", state.upcoming, onToggle, onDelete)
                section("Зроблено", state.done, onToggle, onDelete)
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

private fun LazyListScope.section(title: String, items: List<Homework>, onToggle: (Homework) -> Unit, onDelete: (Homework) -> Unit) {
    if (items.isEmpty()) return
    item(key = "t-$title") {
        Text(title, style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(top = 16.dp, bottom = 4.dp))
    }
    items(items, key = { it.id }) { hw ->
        ListItem(
            modifier = Modifier.fillMaxWidth(),
            leadingContent = { SubjectBadge(hw.subject) },
            headlineContent = {
                Text(hw.text, textDecoration = if (hw.done) TextDecoration.LineThrough else null)
            },
            supportingContent = { Text("${hw.subject} · до ${Format.dayShort(hw.due)}") },
            trailingContent = {
                Row {
                    Checkbox(checked = hw.done, onCheckedChange = { onToggle(hw) })
                    IconButton(onClick = { onDelete(hw) }) { Icon(Icons.Outlined.Delete, contentDescription = "Видалити") }
                }
            },
        )
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
                    FilterChip(selected = s.name == subject, onClick = { subject = s.name }, label = { Text("${s.emoji} ${s.name}") })
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
            Button(onClick = { onAdd(subject, due, text) }, enabled = text.isNotBlank(), modifier = Modifier.fillMaxWidth()) {
                Text("Додати")
            }
        }
    }
}

private const val MAX_TEXT = 500
