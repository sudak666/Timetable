package com.gifwarno.rozklad.feature.grades

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.gifwarno.rozklad.core.domain.Rewards
import com.gifwarno.rozklad.core.domain.Timetable
import com.gifwarno.rozklad.core.model.Grade
import com.gifwarno.rozklad.core.model.LedgerKind
import com.gifwarno.rozklad.core.ui.gradeColor
import java.time.LocalDate

/** Предмет → дата → натиснув оцінку = збережено. Три дотики, як у вебі. */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
internal fun AddGradeSheet(onDismiss: () -> Unit, onAdd: (String, Int, LocalDate) -> Unit) {
    var subject by rememberSaveable { mutableStateOf(Timetable.SUBJECTS.first().name) }
    var daysAgo by rememberSaveable { mutableLongStateOf(0L) }
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            Modifier.padding(horizontal = 16.dp).padding(bottom = 16.dp).navigationBarsPadding(),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text("Нова оцінка", style = MaterialTheme.typography.titleLarge)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Timetable.SUBJECTS.forEach { s ->
                    FilterChip(
                        selected = s.name == subject,
                        onClick = { subject = s.name },
                        label = { Text("${s.emoji} ${s.name}") },
                    )
                }
            }
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf(0L to "Сьогодні", 1L to "Вчора", 2L to "Позавчора").forEach { (d, label) ->
                    FilterChip(selected = daysAgo == d, onClick = { daysAgo = d }, label = { Text(label) })
                }
            }
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Grade.VALUES.forEach { v ->
                    FilledTonalButton(
                        onClick = { onAdd(subject, v, LocalDate.now().minusDays(daysAgo)) },
                        modifier = Modifier.size(width = 64.dp, height = 48.dp),
                        colors = ButtonDefaults.filledTonalButtonColors(
                            containerColor = gradeColor(v).copy(alpha = 0.18f),
                        ),
                    ) { Text(Rewards.gradeLabel(v)) }
                }
            }
        }
    }
}

@Composable
internal fun LedgerDialog(kind: LedgerKind, onDismiss: () -> Unit, onConfirm: (Int, String) -> Unit) {
    var amount by rememberSaveable { mutableStateOf("") }
    var note by rememberSaveable { mutableStateOf("") }
    val value = amount.toIntOrNull()?.takeIf { it in 1..MAX_AMOUNT }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (kind == LedgerKind.PAYOUT) "Виплатити" else "Бонус") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = amount,
                    onValueChange = { amount = it.filter(Char::isDigit).take(MAX_DIGITS) },
                    label = { Text("Сума, ₴") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    singleLine = true,
                )
                if (kind == LedgerKind.BONUS) {
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

private const val MAX_AMOUNT = 100_000
private const val MAX_DIGITS = 6
private const val MAX_NOTE = 80
