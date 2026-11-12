package com.gifwarno.rozklad.feature.schedule

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.DateRangePicker
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.gifwarno.rozklad.core.designsystem.component.ButtonShape
import com.gifwarno.rozklad.core.designsystem.component.GameButton
import com.gifwarno.rozklad.core.designsystem.component.GameSurface
import com.gifwarno.rozklad.core.designsystem.component.NumberTile
import com.gifwarno.rozklad.core.designsystem.component.Pill
import com.gifwarno.rozklad.core.designsystem.theme.RozkladTheme
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
    val c = RozkladTheme.colors
    LazyColumn(contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item(key = "bells") {
            GameSurface {
                Column(Modifier.fillMaxWidth().padding(12.dp)) {
                    Text(
                        "Натисни на час, щоб змінити. Перший урок кожного дня йде за першим дзвінком, другий — за другим і так далі.",
                        style = MaterialTheme.typography.bodySmall,
                        color = c.muted,
                        modifier = Modifier.padding(bottom = 8.dp),
                    )
                    draft.bells.forEachIndexed { i, b ->
                        val prev = draft.bells.getOrNull(i - 1)
                        if (prev != null) {
                            Box(Modifier.fillMaxWidth().padding(vertical = 2.dp), contentAlignment = Alignment.Center) {
                                Pill("перерва · ${Duration.between(prev.end, b.start).toMinutes()} хв")
                            }
                        }
                        Row(Modifier.padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                            NumberTile(i + 1, c.accent)
                            Text("урок", Modifier.weight(1f).padding(start = 10.dp), style = MaterialTheme.typography.bodyLarge)
                            TimeChip(HM.format(b.start)) { editing = i to true }
                            Text("–", Modifier.padding(horizontal = 6.dp))
                            TimeChip(HM.format(b.end)) { editing = i to false }
                        }
                    }
                }
            }
        }
        item(key = "actions") {
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                GameButton(
                    "Дзвінок",
                    actions.addBell,
                    Modifier.weight(1f),
                    enabled = draft.bells.size < SchoolSchedule.MAX_LESSONS,
                    leading = "＋",
                )
                GameButton(
                    "Останній",
                    actions.removeLastBell,
                    Modifier.weight(1f),
                    color = c.card,
                    contentColor = MaterialTheme.colorScheme.onSurface,
                    enabled = draft.bells.size > 1,
                    leading = "−",
                )
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

/** Час дзвінка — натискна «плитка». */
@Composable
private fun TimeChip(text: String, onClick: () -> Unit) {
    GameSurface(shape = ButtonShape, color = RozkladTheme.colors.soft, depth = 3.dp, onClick = onClick) {
        Text(
            text,
            Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
            style = MaterialTheme.typography.titleMedium.copy(fontFeatureSettings = "tnum"),
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
    val c = RozkladTheme.colors
    LazyColumn(contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item(key = "list") {
            GameSurface {
                Column(Modifier.fillMaxWidth().padding(12.dp)) {
                    Text(
                        "Під час канікул таймер і рюкзак не показуються, а «наступний урок» для домашки їх пропускає.",
                        style = MaterialTheme.typography.bodySmall,
                        color = c.muted,
                    )
                    if (draft.holidays.isEmpty()) {
                        Text("Канікул ще немає", Modifier.padding(vertical = 12.dp), fontWeight = FontWeight.Bold)
                    }
                    draft.holidays.forEachIndexed { i, h ->
                        if (i > 0) HorizontalDivider(color = c.line)
                        Row(Modifier.padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                            Text("🏖️", style = MaterialTheme.typography.headlineSmall)
                            Column(Modifier.weight(1f).padding(start = 10.dp)) {
                                Text(
                                    "${Format.dayShort(h.start)} — ${Format.dayShort(h.end)}",
                                    style = MaterialTheme.typography.bodyLarge,
                                    fontWeight = FontWeight.ExtraBold,
                                )
                                Text(
                                    "${ChronoUnit.DAYS.between(h.start, h.end) + 1} дн.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = c.muted,
                                )
                            }
                            IconButton(onClick = { actions.removeHoliday(i) }) {
                                Icon(Icons.Outlined.Delete, contentDescription = "Видалити канікули", tint = c.muted)
                            }
                        }
                    }
                }
            }
        }
        item(key = "add") { GameButton("Канікули", { adding = true }, Modifier.fillMaxWidth(), leading = "＋") }
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
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(
                    value = custom,
                    onValueChange = { custom = it.take(SchoolSchedule.MAX_SUBJECT) },
                    label = { Text("Свій предмет") },
                    singleLine = true,
                    shape = ButtonShape,
                    trailingIcon = {
                        TextButton(onClick = { onPick(custom) }, enabled = custom.isNotBlank()) { Text("OK") }
                    },
                    modifier = Modifier.fillMaxWidth(),
                )
                // Без власного фону й прямих кутів: список — частина діалогу, рядки заокруглені.
                LazyColumn(Modifier.height(SUBJECT_LIST_HEIGHT.dp).clip(ButtonShape)) {
                    items(Timetable.SUBJECTS, key = { it.name }) { s ->
                        ListItem(
                            leadingContent = { SubjectBadge(s.name) },
                            headlineContent = { Text(s.name) },
                            colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                            modifier = Modifier.clip(ButtonShape).clickable { onPick(s.name) },
                        )
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Скасувати") } },
    )
}

private const val SUBJECT_LIST_HEIGHT = 360
