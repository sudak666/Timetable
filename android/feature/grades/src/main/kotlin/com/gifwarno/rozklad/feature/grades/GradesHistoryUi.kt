package com.gifwarno.rozklad.feature.grades

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.gifwarno.rozklad.core.designsystem.component.GameSurface
import com.gifwarno.rozklad.core.designsystem.theme.GameFont
import com.gifwarno.rozklad.core.designsystem.theme.RozkladTheme
import com.gifwarno.rozklad.core.domain.Timetable
import com.gifwarno.rozklad.core.model.LedgerKind
import com.gifwarno.rozklad.core.ui.Format
import com.gifwarno.rozklad.core.ui.gradeColor
import com.gifwarno.rozklad.core.ui.moneyColor
import kotlin.math.roundToInt

/** Історія: оцінки, бонуси, штрафи, виплати й автонагороди разом. */
@Composable
internal fun HistoryCard(items: List<HistoryEntry>, actions: RowActions, onDeleteLedger: (String) -> Unit) {
    val c = RozkladTheme.colors
    GameSurface {
        Column(Modifier.fillMaxWidth().padding(horizontal = 6.dp, vertical = 4.dp)) {
            if (items.isEmpty()) {
                Text(
                    "Ще немає оцінок — додай першу кнопкою «Оцінка».",
                    Modifier.padding(12.dp),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            items.forEachIndexed { i, e ->
                if (i > 0) HorizontalDivider(Modifier.padding(horizontal = 8.dp), color = c.line)
                when (e) {
                    is HistoryEntry.GradeRow -> GradeRow(e.item, actions)

                    is HistoryEntry.Money -> MoneyRow(e, onDeleteLedger)

                    is HistoryEntry.Auto -> SimpleRow(
                        icon = e.reward.icon,
                        iconBg = Brush.linearGradient(listOf(Color(0xFFFD79A8), c.accent)),
                        title = e.reward.note,
                        subtitle = "${Format.dayShort(e.date)} · нараховано автоматично",
                        amount = Format.money(e.reward.amount),
                        amountColor = c.positive,
                    )
                }
            }
        }
    }
}

@Composable
private fun MoneyRow(e: HistoryEntry.Money, onDelete: (String) -> Unit) {
    val c = RozkladTheme.colors
    val l = e.entry
    val bonus = l.kind == LedgerKind.BONUS
    SimpleRow(
        icon = if (!bonus) {
            "💸"
        } else if (l.amount >= 0) {
            "🎁"
        } else {
            "⚠️"
        },
        iconBg = if (!bonus) {
            Brush.linearGradient(listOf(Color(0xFF4B5563), Color(0xFF4B5563)))
        } else {
            Brush.linearGradient(listOf(c.gold, c.goldEnd))
        },
        title = l.note.ifBlank { if (bonus) "Бонус" else "Виплачено" },
        subtitle = Format.dayShort(l.date),
        amount = if (bonus) Format.money(l.amount) else "−${l.amount}\u00A0₴",
        amountColor = if (bonus) moneyColor(l.amount) else c.muted,
        onDelete = if (e.canDelete) ({ onDelete(l.id) }) else null,
    )
}

@Composable
private fun SimpleRow(
    icon: String,
    iconBg: Brush,
    title: String,
    subtitle: String,
    amount: String,
    amountColor: Color,
    onDelete: (() -> Unit)? = null,
) {
    val c = RozkladTheme.colors
    Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(48.dp).clip(RoundedCornerShape(14.dp)).background(iconBg), contentAlignment = Alignment.Center) {
            Text(icon, fontSize = 22.sp)
        }
        Column(Modifier.weight(1f).padding(start = 12.dp)) {
            Text(title, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.ExtraBold, maxLines = 2)
            Text(subtitle, style = MaterialTheme.typography.bodySmall, color = c.muted)
        }
        Text(amount, style = MaterialTheme.typography.titleSmall, color = amountColor, modifier = Modifier.padding(start = 8.dp))
        if (onDelete != null) {
            IconButton(onClick = onDelete) { Icon(Icons.Outlined.Delete, contentDescription = "Видалити «$title»", tint = c.muted) }
        }
    }
}

/** Порівняння з минулим місяцем або рейтинг, і середній бал за предметами. */
@Composable
internal fun StatsCard(state: GradesUiState.Ready) {
    val c = RozkladTheme.colors
    GameSurface {
        Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            state.compare?.let { m ->
                Text("🏆 Я проти минулого місяця", style = MaterialTheme.typography.titleSmall)
                // Без оцінок цього місяця порівнювати нема з чим — без стрілки.
                CompareRow(
                    "Середній бал",
                    m.avgNow?.let { "%.1f".format(it) } ?: "—",
                    m.avgPrev?.let { "%.1f".format(it) } ?: "—",
                    if (m.avgNow == null || m.avgPrev == null) 0 else m.avgNow.compareTo(m.avgPrev),
                )
                // Минулий місяць — просто сума («було 1910 ₴»), без «+», щоб не сперечалася зі стрілкою.
                // Місяць ще триває — порівнюємо з тими самими днями («за 1–10»), а не з усім минулим місяцем.
                CompareRow(
                    if (m.upToDay != null) "Заробив за 1–${m.upToDay}" else "Заробив",
                    Format.money(m.earnedNow),
                    Format.balance(m.earnedPrev),
                    m.earnedNow.compareTo(m.earnedPrev),
                )
            }
            if (state.rank.isNotEmpty()) {
                Text("🏆 Рейтинг місяця", style = MaterialTheme.typography.titleSmall)
                state.rank.forEachIndexed { i, r ->
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(listOf("🥇", "🥈", "🥉").getOrElse(i) { "·" }, fontSize = 18.sp)
                        Text(
                            "${r.avatar} ${r.name}",
                            Modifier.weight(1f).padding(start = 8.dp),
                            fontWeight = if (r.userId == state.child.userId) FontWeight.Black else FontWeight.SemiBold,
                        )
                        Text(Format.money(r.earned), fontWeight = FontWeight.ExtraBold)
                    }
                }
            }
            if (state.subjectAvgs.isNotEmpty()) {
                if (state.compare != null || state.rank.isNotEmpty()) HorizontalDivider(color = c.line)
                Text("📈 Середній бал за предметами", style = MaterialTheme.typography.titleSmall)
                state.subjectAvgs.forEach { a ->
                    val label = "${Timetable.subject(a.subject).emoji} ${a.subject}"
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.semantics(mergeDescendants = true) {
                            contentDescription = "${a.subject}: ${"%.1f".format(a.avg)}, оцінок ${a.count}"
                        },
                    ) {
                        Text(label, Modifier.width(150.dp), style = MaterialTheme.typography.bodyMedium, maxLines = 1)
                        Box(Modifier.weight(1f).height(14.dp).clip(RoundedCornerShape(50)).background(c.soft)) {
                            Box(
                                Modifier
                                    .fillMaxHeight()
                                    .fillMaxWidth((a.avg / MAX_GRADE).toFloat().coerceIn(0f, 1f))
                                    .clip(RoundedCornerShape(50))
                                    .background(gradeColor(a.avg.roundToInt())),
                            )
                        }
                        Text("%.1f".format(a.avg), Modifier.padding(start = 8.dp), fontFamily = GameFont)
                    }
                }
            }
        }
    }
}

@Composable
private fun CompareRow(label: String, now: String, prev: String, trend: Int) {
    val c = RozkladTheme.colors
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(label, Modifier.weight(1f), style = MaterialTheme.typography.bodyLarge)
        Text(now, fontWeight = FontWeight.Black, style = MaterialTheme.typography.bodyLarge)
        Text(
            when {
                trend > 0 -> " ▲"
                trend < 0 -> " ▼"
                else -> ""
            },
            color = if (trend > 0) c.positive else c.negative,
            modifier = Modifier.semantics {
                contentDescription = if (trend > 0) {
                    "краще"
                } else if (trend < 0) {
                    "гірше"
                } else {
                    ""
                }
            },
        )
        Text(" (було $prev)", style = MaterialTheme.typography.bodySmall, color = c.muted)
    }
}

/** Ціль: на що збирає дитина і скільки коштує. */
@Composable
internal fun GoalDialog(title: String, amount: Int, onDismiss: () -> Unit, onSave: (String, Int) -> Unit) {
    var t by rememberSaveable { mutableStateOf(title) }
    var a by rememberSaveable { mutableStateOf(if (amount > 0) amount.toString() else "") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("🎯 Ціль") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(value = t, onValueChange = {
                    t = it.take(GOAL_MAX)
                }, label = { Text("На що збираєш?") }, singleLine = true)
                OutlinedTextField(
                    value = a,
                    onValueChange = { a = it.filter(Char::isDigit).take(AMOUNT_DIGITS) },
                    label = { Text("Скільки коштує, ₴") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                )
            }
        },
        confirmButton = { TextButton(onClick = { onSave(t, a.toIntOrNull() ?: 0) }) { Text("Зберегти") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Скасувати") } },
    )
}

private const val MAX_GRADE = 12.0
private const val GOAL_MAX = 80
private const val AMOUNT_DIGITS = 7
