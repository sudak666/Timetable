package com.gifwarno.rozklad.feature.grades

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyHorizontalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.gifwarno.rozklad.core.designsystem.component.ButtonShape
import com.gifwarno.rozklad.core.designsystem.component.GameButton
import com.gifwarno.rozklad.core.designsystem.component.GameProgressBar
import com.gifwarno.rozklad.core.designsystem.component.GameSurface
import com.gifwarno.rozklad.core.designsystem.component.Pill
import com.gifwarno.rozklad.core.designsystem.theme.GameFont
import com.gifwarno.rozklad.core.designsystem.theme.RozkladTheme
import com.gifwarno.rozklad.core.domain.Timetable
import com.gifwarno.rozklad.core.ui.Format

/** Що робимо з гаманцем дитини. Штраф — від'ємний бонус (як «мінус = штраф» у вебі). */
enum class Money { BONUS, FINE, PAYOUT }

/** Челенджі дитини з прогресом; батьки створюють і видаляють. */
@Composable
internal fun ChallengesCard(items: List<ChallengeItem>, isParent: Boolean, onNew: () -> Unit, onDelete: (String) -> Unit) {
    val c = RozkladTheme.colors
    GameSurface {
        Column(Modifier.fillMaxWidth().padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            if (items.isEmpty()) {
                Text(
                    "Челенджів ще немає. Наприклад: «3 десятки з математики за тиждень → +100 ₴».",
                    style = MaterialTheme.typography.bodyMedium,
                    color = c.muted,
                )
            }
            items.forEachIndexed { i, ch ->
                if (i > 0) HorizontalDivider(color = c.line)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(
                            ch.title,
                            style = MaterialTheme.typography.bodyLarge,
                            fontWeight = FontWeight.ExtraBold,
                            color = if (ch.done || ch.over) c.muted else MaterialTheme.colorScheme.onSurface,
                        )
                        val subject = ch.subject?.let { "${Timetable.subject(it).emoji} $it · " }.orEmpty()
                        val status = when {
                            ch.done -> "✅ виконано!"
                            ch.over -> "⌛ час вийшов"
                            else -> "до ${Format.dayShort(ch.endDate)}"
                        }
                        Text(
                            "${subject}оцінка ≥ ${ch.minGrade} · ${ch.hits}/${ch.need} · $status",
                            style = MaterialTheme.typography.bodySmall,
                            color = c.muted,
                        )
                        GameProgressBar(
                            ch.hits / ch.need.toFloat(),
                            Modifier.padding(top = 6.dp),
                            fill = if (ch.done) {
                                Brush.horizontalGradient(listOf(Color(0xFF55EFC4), Color(0xFF00B894)))
                            } else {
                                Brush.horizontalGradient(listOf(c.accent, c.accentEnd))
                            },
                            height = 10.dp,
                        )
                    }
                    Pill("+${ch.reward} ₴", Modifier.padding(start = 10.dp), color = c.positive, contentColor = Color.White)
                    if (isParent) {
                        IconButton(onClick = { onDelete(ch.id) }) {
                            Icon(Icons.Outlined.Delete, contentDescription = "Видалити челендж «${ch.title}»", tint = c.muted)
                        }
                    }
                }
            }
            if (isParent) GameButton("Новий челендж", onNew, Modifier.fillMaxWidth(), leading = "🏅")
        }
    }
}

/** Форма челенджу: назва, предмет (або будь-який), оцінка ≥, скільки разів, за скільки днів, нагорода. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun ChallengeSheet(onDismiss: () -> Unit, onCreate: (ChallengeDraft) -> Unit) {
    val d = ChallengeDraft()
    var title by rememberSaveable { mutableStateOf("") }
    var subject by rememberSaveable { mutableStateOf<String?>(null) }
    var minGrade by rememberSaveable { mutableIntStateOf(d.minGrade) }
    var need by rememberSaveable { mutableIntStateOf(d.need) }
    var days by rememberSaveable { mutableIntStateOf(d.days) }
    var reward by rememberSaveable { mutableStateOf(d.reward.toString()) }
    val c = RozkladTheme.colors
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
        Column(
            Modifier.verticalScroll(rememberScrollState()).padding(horizontal = 16.dp).padding(bottom = 16.dp).navigationBarsPadding(),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text("🏅 Новий челендж", style = MaterialTheme.typography.titleLarge)
            OutlinedTextField(
                value = title,
                onValueChange = { title = it.take(MAX_TITLE) },
                label = { Text("Назва, напр.: 3 десятки з математики") },
                singleLine = true,
                shape = ButtonShape,
                modifier = Modifier.fillMaxWidth(),
            )
            Text("Предмет", style = MaterialTheme.typography.labelLarge, color = c.muted)
            val options =
                listOf<Pair<String?, String>>(null to "🌐 Будь-який") + Timetable.SUBJECTS.map { it.name to "${it.emoji} ${it.name}" }
            LazyHorizontalGrid(
                rows = GridCells.Fixed(2),
                modifier = Modifier.fillMaxWidth().height(86.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                items(options, key = { it.first ?: "*" }) { (value, label) ->
                    val on = value == subject
                    GameSurface(
                        shape = RoundedCornerShape(50),
                        color = if (on) c.accent else c.soft,
                        contentColor = if (on) Color.White else MaterialTheme.colorScheme.onSurface,
                        depth = if (on) 3.dp else 0.dp,
                        borderWidth = if (on) 2.dp else 0.dp,
                        onClick = { subject = value },
                        modifier = Modifier.semantics { selected = on },
                    ) {
                        Text(
                            label,
                            Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                            style = MaterialTheme.typography.labelLarge,
                            maxLines = 1,
                        )
                    }
                }
            }
            Stepper("Оцінка не менше", minGrade, 1..MAX_GRADE) { minGrade = it }
            Stepper("Скільки разів", need, 1..MAX_NEED) { need = it }
            Stepper("За скільки днів", days, 1..MAX_DAYS) { days = it }
            OutlinedTextField(
                value = reward,
                onValueChange = { reward = it.filter(Char::isDigit).take(MAX_DIGITS) },
                label = { Text("Нагорода, ₴") },
                singleLine = true,
                shape = ButtonShape,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                modifier = Modifier.fillMaxWidth(),
            )
            GameButton(
                "Створити",
                onClick = {
                    onCreate(ChallengeDraft(title.trim(), subject, minGrade, need, days, reward.toIntOrNull() ?: 0))
                },
                modifier = Modifier.fillMaxWidth(),
                enabled = title.isNotBlank(),
                leading = "🏅",
            )
        }
    }
}

/** Число з кнопками − / +. */
@Composable
private fun Stepper(label: String, value: Int, range: IntRange, onChange: (Int) -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(label, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
        GameButton("−", { onChange((value - 1).coerceIn(range)) }, enabled = value > range.first)
        Text(
            "$value",
            Modifier.width(56.dp).semantics { contentDescription = "$label: $value" },
            fontFamily = GameFont,
            style = MaterialTheme.typography.titleLarge,
            textAlign = TextAlign.Center,
        )
        GameButton("+", { onChange((value + 1).coerceIn(range)) }, enabled = value < range.last)
    }
}

/** Бонус, штраф або виплата: сума й (для бонусу й штрафу) за що. */
@Composable
internal fun LedgerDialog(money: Money, onDismiss: () -> Unit, onConfirm: (Int, String) -> Unit) {
    var amount by rememberSaveable { mutableStateOf("") }
    var note by rememberSaveable { mutableStateOf("") }
    val value = amount.toIntOrNull()?.takeIf { it in 1..MAX_AMOUNT }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                when (money) {
                    Money.BONUS -> "🎁 Бонус"
                    Money.FINE -> "⚠️ Штраф"
                    Money.PAYOUT -> "💸 Виплатити"
                },
            )
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                if (money == Money.FINE) {
                    Text("Сума спишеться з гаманця дитини.", style = MaterialTheme.typography.bodySmall)
                }
                OutlinedTextField(
                    value = amount,
                    onValueChange = { amount = it.filter(Char::isDigit).take(MAX_DIGITS) },
                    label = { Text("Сума, ₴") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    singleLine = true,
                )
                if (money != Money.PAYOUT) {
                    OutlinedTextField(
                        value = note,
                        onValueChange = { note = it.take(MAX_NOTE) },
                        label = { Text("За що") },
                        singleLine = true,
                    )
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { value?.let { onConfirm(it, note) } }, enabled = value != null) { Text("Зберегти") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Скасувати") } },
    )
}

private const val MAX_TITLE = 120
private const val MAX_GRADE = 12
private const val MAX_NEED = 50
private const val MAX_DAYS = 90
private const val MAX_AMOUNT = 100_000
private const val MAX_DIGITS = 6
private const val MAX_NOTE = 200
