package com.gifwarno.rozklad.feature.schedule

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.gifwarno.rozklad.core.designsystem.component.ButtonShape
import com.gifwarno.rozklad.core.designsystem.component.GameButton
import com.gifwarno.rozklad.core.designsystem.component.GameDialog
import com.gifwarno.rozklad.core.designsystem.component.GameSegmented
import com.gifwarno.rozklad.core.designsystem.component.GameSurface
import com.gifwarno.rozklad.core.designsystem.theme.RozkladTheme
import com.gifwarno.rozklad.core.domain.Clubs
import com.gifwarno.rozklad.core.domain.Timetable
import com.gifwarno.rozklad.core.model.Club
import com.gifwarno.rozklad.core.model.SchoolSchedule
import java.time.DayOfWeek
import java.time.LocalTime
import java.time.format.DateTimeFormatter

private val HM = DateTimeFormatter.ofPattern("H:mm")

/** Вкладка «Заняття»: тренування, гуртки, курси — у свої дні й свій час, не за дзвінками. */
@Composable
internal fun ClubsTab(draft: SchoolSchedule, actions: EditorActions, reminderMinutes: Int) {
    // null — діалог закрито, -1 — нове заняття, інакше індекс.
    var editing by rememberSaveable { mutableStateOf<Int?>(null) }
    val c = RozkladTheme.colors
    LazyColumn(contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item(key = "hint") {
            GameSurface {
                Text(
                    "Тренування, гуртки, курси. Показуються на «Сьогодні» в картці «Після школи» — і на канікулах теж. " +
                        "«Що взяти» потрапить у рюкзак.",
                    Modifier.fillMaxWidth().padding(12.dp),
                    style = MaterialTheme.typography.bodySmall,
                    color = c.muted,
                )
            }
        }
        if (draft.clubs.isEmpty()) {
            item(key = "empty") {
                GameSurface {
                    Text("Занять ще немає — додай перше.", Modifier.fillMaxWidth().padding(16.dp), fontWeight = FontWeight.Bold)
                }
            }
        }
        itemsIndexed(draft.clubs, key = { i, club -> "club-$i-${club.title}" }) { i, club ->
            ClubRow(club, onClick = { editing = i })
        }
        item(key = "reminder") { ReminderCard(reminderMinutes, actions.setReminder) }
        item(key = "add") {
            GameButton(
                "Заняття",
                { editing = -1 },
                Modifier.fillMaxWidth(),
                enabled = draft.clubs.size < Club.MAX,
                leading = "＋",
            )
        }
    }
    editing?.let { index ->
        ClubDialog(
            initial = draft.clubs.getOrNull(index),
            onDismiss = { editing = null },
            onSave = { club ->
                actions.putClub(index.takeIf { it >= 0 }, club)
                editing = null
            },
            onDelete = if (index >= 0) {
                {
                    actions.removeClub(index)
                    editing = null
                }
            } else {
                null
            },
        )
    }
}

/** Нагадування — налаштування цього телефона, не сім'ї; зберігається одразу, без «Зберегти». */
@Composable
private fun ReminderCard(minutes: Int, onChange: (Int) -> Unit) {
    val context = LocalContext.current
    var granted by remember { mutableStateOf(notificationsAllowed(context)) }
    var asked by rememberSaveable { mutableStateOf<Int?>(null) }
    val permission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { ok ->
        granted = ok
        asked?.let { if (ok) onChange(it) }
        asked = null
    }
    val pick: (Int) -> Unit = { m ->
        if (m > 0 && !granted && Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            asked = m
            permission.launch(Manifest.permission.POST_NOTIFICATIONS)
        } else {
            onChange(m)
        }
    }
    GameSurface {
        Column(Modifier.fillMaxWidth().padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Нагадувати на цьому телефоні", style = MaterialTheme.typography.titleSmall)
            GameSegmented(
                options = REMINDER_OPTIONS.map { it to if (it == 0) "Ні" else "за $it хв" },
                selected = if (minutes > 0 && !granted) 0 else minutes,
                onSelect = pick,
            )
            if (minutes > 0 && !granted) {
                Text(
                    "Сповіщення вимкнені в Android — натисни час ще раз, щоб дозволити.",
                    style = MaterialTheme.typography.bodySmall,
                    color = RozkladTheme.colors.muted,
                )
            }
        }
    }
}

private val REMINDER_OPTIONS = listOf(0, 15, 30, 60)

private fun notificationsAllowed(context: Context): Boolean = Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
    ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED

@Composable
private fun ClubRow(club: Club, onClick: () -> Unit) {
    val c = RozkladTheme.colors
    GameSurface(onClick = onClick, onClickLabel = "Змінити заняття") {
        Row(Modifier.fillMaxWidth().padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(club.emoji, style = MaterialTheme.typography.headlineSmall)
            Column(Modifier.weight(1f).padding(start = 12.dp)) {
                Text(club.title, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.ExtraBold)
                Text(
                    "${Clubs.daysText(club.days)} · ${Clubs.timeText(club)}",
                    style = MaterialTheme.typography.bodyMedium.copy(fontFeatureSettings = "tnum"),
                )
                if (club.place.isNotBlank()) {
                    Text(
                        "📍 ${club.place}",
                        style = MaterialTheme.typography.bodySmall,
                        color = c.muted,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                if (club.bring.isNotBlank()) {
                    Text(
                        "🎒 ${club.bring}",
                        style = MaterialTheme.typography.bodySmall,
                        color = c.muted,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }
    }
}

/** Додати або змінити заняття. Стан — простими значеннями, щоб пережити поворот екрана. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ClubDialog(initial: Club?, onDismiss: () -> Unit, onSave: (Club) -> Unit, onDelete: (() -> Unit)?) {
    var title by rememberSaveable { mutableStateOf(initial?.title.orEmpty()) }
    var emoji by rememberSaveable { mutableStateOf(initial?.emoji ?: Clubs.DEFAULT_EMOJI) }
    var days by rememberSaveable { mutableStateOf(initial?.days?.map { it.value }?.sorted() ?: emptyList()) }
    var start by rememberSaveable { mutableIntStateOf(initial?.start?.toSecondOfDay()?.div(SECONDS) ?: DEFAULT_START) }
    var end by rememberSaveable { mutableIntStateOf(initial?.end?.toSecondOfDay()?.div(SECONDS) ?: (DEFAULT_START + DEFAULT_LENGTH)) }
    var place by rememberSaveable { mutableStateOf(initial?.place.orEmpty()) }
    var bring by rememberSaveable { mutableStateOf(initial?.bring.orEmpty()) }
    var timePicker by rememberSaveable { mutableStateOf<Boolean?>(null) } // true — початок, false — кінець

    fun time(min: Int): LocalTime = LocalTime.of(min / MINUTES, min % MINUTES)
    val club = Club(title, emoji, days.map(DayOfWeek::of).toSet(), time(start), time(end), place, bring)
    val errors = Clubs.validate(listOf(club))
    val c = RozkladTheme.colors

    GameDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (initial == null) "Нове заняття" else "Заняття") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                // Шаблони — лише для нового заняття: при зміні наявного вони відсували б поля вниз.
                if (initial == null) {
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Clubs.KINDS.forEach { k ->
                            val on = emoji == k.emoji && title == k.title
                            Chip("${k.emoji} ${k.title}", on) {
                                // Свою назву не затираємо — лише підставляємо емодзі.
                                if (title.isBlank() || Clubs.KINDS.any { it.title == title }) title = k.title
                                emoji = k.emoji
                            }
                        }
                    }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = emoji,
                        onValueChange = { emoji = it.take(Club.MAX_EMOJI) },
                        label = { Text("Емодзі") },
                        singleLine = true,
                        shape = ButtonShape,
                        modifier = Modifier.width(EMOJI_FIELD.dp),
                    )
                    OutlinedTextField(
                        value = title,
                        onValueChange = { title = it.take(Club.MAX_TITLE) },
                        label = { Text("Назва") },
                        singleLine = true,
                        shape = ButtonShape,
                        modifier = Modifier.weight(1f),
                    )
                }
                Text("Дні", style = MaterialTheme.typography.labelLarge)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Timetable.DAY_SHORT.forEachIndexed { i, name ->
                        val d = i + 1
                        Chip(name, d in days) { days = if (d in days) days - d else (days + d).sorted() }
                    }
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Час", Modifier.weight(1f), style = MaterialTheme.typography.labelLarge)
                    TimeChip(HM.format(time(start))) { timePicker = true }
                    Text("–", Modifier.padding(horizontal = 6.dp))
                    TimeChip(HM.format(time(end))) { timePicker = false }
                }
                OutlinedTextField(
                    value = place,
                    onValueChange = { place = it.take(Club.MAX_PLACE) },
                    label = { Text("Де (необов’язково)") },
                    singleLine = true,
                    shape = ButtonShape,
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = bring,
                    onValueChange = { bring = it.take(Club.MAX_BRING) },
                    label = { Text("Що взяти (необов’язково)") },
                    placeholder = { Text("форма, вода, ноутбук") },
                    shape = ButtonShape,
                    modifier = Modifier.fillMaxWidth(),
                )
                errors.forEach { Text("• ${it.substringAfter(": ")}", style = MaterialTheme.typography.bodySmall, color = c.muted) }
                if (onDelete != null) {
                    TextButton(onClick = onDelete) { Text("Видалити заняття", color = MaterialTheme.colorScheme.error) }
                }
            }
        },
        confirmButton = { TextButton(onClick = { onSave(club) }, enabled = errors.isEmpty()) { Text("Готово") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Скасувати") } },
    )

    timePicker?.let { isStart ->
        TimeDialog(
            title = if (isStart) "Початок" else "Кінець",
            initial = time(if (isStart) start else end),
            onDismiss = { timePicker = null },
            onConfirm = { t ->
                val m = t.hour * MINUTES + t.minute
                if (isStart) {
                    // Тривалість зберігається, як і для дзвінків; не переходимо через північ.
                    end = (m + (end - start)).coerceAtMost(LAST_MINUTE)
                    start = m
                } else {
                    end = m
                }
                timePicker = null
            },
        )
    }
}

@Composable
private fun Chip(text: String, on: Boolean, onClick: () -> Unit) {
    val c = RozkladTheme.colors
    GameSurface(
        shape = RoundedCornerShape(50),
        color = if (on) c.accent else c.soft,
        contentColor = if (on) Color.White else MaterialTheme.colorScheme.onSurface,
        depth = 2.dp,
        onClick = onClick,
    ) {
        Text(text, Modifier.padding(horizontal = 12.dp, vertical = 6.dp), style = MaterialTheme.typography.labelLarge)
    }
}

private const val SECONDS = 60
private const val MINUTES = 60
private const val DEFAULT_START = 16 * MINUTES
private const val DEFAULT_LENGTH = 90
private const val LAST_MINUTE = 23 * MINUTES + 59
private const val EMOJI_FIELD = 96
