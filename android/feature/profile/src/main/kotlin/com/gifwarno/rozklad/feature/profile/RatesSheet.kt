package com.gifwarno.rozklad.feature.profile

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.gifwarno.rozklad.core.designsystem.component.ButtonShape
import com.gifwarno.rozklad.core.designsystem.component.GameButton
import com.gifwarno.rozklad.core.designsystem.component.GradeTile
import com.gifwarno.rozklad.core.model.RewardSettings
import com.gifwarno.rozklad.core.model.Streak
import com.gifwarno.rozklad.core.ui.gradeColor

/** Курс за оцінки 12…1 (можна мінус — штраф) і правила серії; зберігається для всієї сім'ї. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun RatesSheet(settings: RewardSettings, onDismiss: () -> Unit, onSave: (Map<Int, Int>, Streak) -> Unit) {
    // Текстові поля: дозволяємо тимчасово порожнє чи «-» під час набору.
    val rates = remember { mutableStateListOf(*GRADES.map { (settings.rates[it] ?: 0).toString() }.toTypedArray()) }
    val streak = remember {
        mutableStateListOf(settings.streak.len.toString(), settings.streak.min.toString(), settings.streak.bonus.toString())
    }
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
        Column(
            Modifier.verticalScroll(rememberScrollState()).padding(horizontal = 16.dp).padding(bottom = 16.dp).navigationBarsPadding(),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Text("⚙️ Курс оцінок", style = MaterialTheme.typography.titleLarge)
            Text(
                "Скільки гривень дає кожна оцінка (мінус — штраф). Новий курс діє для нових підтверджень.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            GRADES.chunked(2).forEach { pair ->
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    pair.forEach { g ->
                        val i = GRADES.indexOf(g)
                        Row(Modifier.weight(1f), verticalAlignment = Alignment.CenterVertically) {
                            GradeTile("$g", gradeColor(g))
                            MoneyField(rates[i], { rates[i] = it }, "Оцінка $g, ₴", Modifier.padding(start = 8.dp))
                        }
                    }
                }
            }
            Text("🔥 Серія", style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(top = 8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                MoneyField(streak[0], { streak[0] = it }, "Оцінок поспіль", Modifier.weight(1f), allowMinus = false)
                MoneyField(streak[1], { streak[1] = it }, "Від балу", Modifier.weight(1f), allowMinus = false)
                MoneyField(streak[2], { streak[2] = it }, "Бонус, ₴", Modifier.weight(1f), allowMinus = false)
            }
            GameButton(
                "Зберегти курс",
                onClick = {
                    val r = GRADES.mapIndexed { i, g -> g to (rates[i].toIntOrNull() ?: 0) }.toMap()
                    // Межі — як у вебі й домені (Rewards.parseSettings).
                    val st = Streak(
                        len = (streak[0].toIntOrNull() ?: DEFAULT_LEN).coerceIn(MIN_LEN, MAX_LEN),
                        min = (streak[1].toIntOrNull() ?: DEFAULT_MIN).coerceIn(1, MAX_GRADE),
                        bonus = (streak[2].toIntOrNull() ?: 0).coerceAtLeast(0),
                    )
                    onSave(r, st)
                },
                modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                color = androidx.compose.ui.graphics.Color(0xFF00A383),
                leading = "💾",
            )
        }
    }
}

@Composable
private fun MoneyField(
    value: String,
    onChange: (String) -> Unit,
    label: String,
    modifier: Modifier = Modifier,
    allowMinus: Boolean = true,
) {
    OutlinedTextField(
        value = value,
        onValueChange = { raw ->
            val minus = allowMinus && raw.startsWith("-")
            onChange((if (minus) "-" else "") + raw.filter(Char::isDigit).take(MAX_DIGITS))
        },
        label = { Text(label, maxLines = 1) },
        singleLine = true,
        shape = ButtonShape,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
        modifier = modifier.fillMaxWidth(),
    )
}

private val GRADES = (12 downTo 1).toList()
private const val MAX_DIGITS = 6
private const val MAX_GRADE = 12
private const val MIN_LEN = 2
private const val MAX_LEN = 30
private const val DEFAULT_LEN = 5
private const val DEFAULT_MIN = 7
