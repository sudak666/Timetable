package com.gifwarno.rozklad.feature.schedule

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.DateRangePicker
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.rememberDateRangePickerState
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.gifwarno.rozklad.core.domain.Timetable
import com.gifwarno.rozklad.core.model.SchoolSchedule
import com.gifwarno.rozklad.core.ui.Format
import com.gifwarno.rozklad.core.ui.SubjectBadge
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit

private val HM = DateTimeFormatter.ofPattern("H:mm")

@Composable
internal fun BellsTab(draft: SchoolSchedule, actions: EditorActions) {
    // (індекс дзвінка, true — початок / false — кінець)
    var editing by rememberSaveable { mutableStateOf<Pair<Int, Boolean>?>(null) }
    LazyColumn(contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        item(key = "hint") {
            Text(
                "Натисни на час, щоб змінити. i-й урок кожного дня йде за i-м дзвінком.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        itemsIndexed(draft.bells) { i, b ->
            ListItem(
                headlineContent = { Text("${i + 1} урок") },
                supportingContent = {
                    val prev = draft.bells.getOrNull(i - 1)
                    if (prev != null) Text("перерва ${Duration.between(prev.end, b.start).toMinutes()} хв")
                },
                trailingContent = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        TextButton(onClick = { editing = i to true }) { Text(HM.format(b.start)) }
                        Text("–")
                        TextButton(onClick = { editing = i to false }) { Text(HM.format(b.end)) }
                    }
                },
            )
        }
        item(key = "actions") {
            Row(Modifier.padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = actions.addBell, enabled = draft.bells.size < SchoolSchedule.MAX_LESSONS) { Text("+ Дзвінок") }
                OutlinedButton(onClick = actions.removeLastBell, enabled = draft.bells.size > 1) { Text("− Останній") }
            }
        }
    }
    editing?.let { (i, isStart) ->
        val bell = draft.bells[i]
        TimeDialog(
            title = "${i + 1} урок: ${if (isStart) "початок" else "кінець"}",
            initial = if (isStart) bell.start else bell.end,
            onDismiss = { editing = null },
            onConfirm = { t ->
                if (isStart) {
                    // Зсуваємо кінець разом із початком — тривалість уроку зберігається.
                    actions.setBell(i, t, t.plus(Duration.between(bell.start, bell.end)))
                } else {
                    actions.setBell(i, bell.start, t)
                }
                editing = null
            },
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TimeDialog(title: String, initial: LocalTime, onDismiss: () -> Unit, onConfirm: (LocalTime) -> Unit) {
    val state = rememberTimePickerState(initialHour = initial.hour, initialMinute = initial.minute, is24Hour = true)
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = { TimePicker(state) },
        confirmButton = { TextButton(onClick = { onConfirm(LocalTime.of(state.hour, state.minute)) }) { Text("OK") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Скасувати") } },
    )
}

@Composable
internal fun HolidaysTab(draft: SchoolSchedule, actions: EditorActions) {
    var adding by rememberSaveable { mutableStateOf(false) }
    LazyColumn(contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        item(key = "hint") {
            Text(
                "У канікули таймер і рюкзак не показуються, а «наступний урок» для домашки їх пропускає.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        if (draft.holidays.isEmpty()) {
            item(key = "empty") { Text("Канікул ще немає", modifier = Modifier.padding(vertical = 16.dp)) }
        }
        itemsIndexed(draft.holidays) { i, h ->
            ListItem(
                leadingContent = { Text("🏖️", style = MaterialTheme.typography.titleLarge) },
                headlineContent = { Text("${Format.dayShort(h.start)} — ${Format.dayShort(h.end)}") },
                supportingContent = { Text("${ChronoUnit.DAYS.between(h.start, h.end) + 1} дн.") },
                trailingContent = {
                    IconButton(onClick = {
                        actions.removeHoliday(i)
                    }) { Icon(Icons.Outlined.Delete, contentDescription = "Видалити канікули") }
                },
            )
        }
        item(key = "add") {
            OutlinedButton(onClick = { adding = true }, modifier = Modifier.fillMaxWidth().padding(top = 8.dp)) { Text("+ Канікули") }
        }
    }
    if (adding) {
        HolidayDialog(
            onDismiss = { adding = false },
            onConfirm = { a, b ->
                actions.addHoliday(a, b)
                adding = false
            },
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun HolidayDialog(onDismiss: () -> Unit, onConfirm: (LocalDate, LocalDate) -> Unit) {
    val state = rememberDateRangePickerState()
    fun toDate(ms: Long?) = ms?.let { Instant.ofEpochMilli(it).atZone(ZoneOffset.UTC).toLocalDate() }
    val start = toDate(state.selectedStartDateMillis)
    val end = toDate(state.selectedEndDateMillis) ?: start
    DatePickerDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(onClick = { if (start != null && end != null) onConfirm(start, end) }, enabled = start != null) { Text("Додати") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Скасувати") } },
    ) {
        DateRangePicker(state = state, modifier = Modifier.height(DATE_PICKER_HEIGHT.dp), title = {
            Text("Канікули", Modifier.padding(16.dp))
        })
    }
}

private const val DATE_PICKER_HEIGHT = 520

/** Вибір предмета: довідник + свій варіант. */
@Composable
internal fun SubjectPickerDialog(onDismiss: () -> Unit, onPick: (String) -> Unit) {
    var custom by rememberSaveable { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Предмет") },
        text = {
            Column {
                OutlinedTextField(
                    value = custom,
                    onValueChange = { custom = it.take(SchoolSchedule.MAX_SUBJECT) },
                    label = { Text("Свій предмет") },
                    singleLine = true,
                    trailingIcon = {
                        TextButton(onClick = { onPick(custom) }, enabled = custom.isNotBlank()) { Text("OK") }
                    },
                    modifier = Modifier.fillMaxWidth(),
                )
                LazyColumn(Modifier.height(SUBJECT_LIST_HEIGHT.dp)) {
                    items(Timetable.SUBJECTS, key = { it.name }) { s ->
                        ListItem(
                            leadingContent = { SubjectBadge(s.name) },
                            headlineContent = { Text(s.name) },
                            modifier = Modifier.clickable { onPick(s.name) },
                        )
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Скасувати") } },
    )
}

private const val SUBJECT_LIST_HEIGHT = 360
