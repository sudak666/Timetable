package com.gifwarno.rozklad.feature.profile.alerts

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.gifwarno.rozklad.core.designsystem.component.GameButton
import com.gifwarno.rozklad.core.designsystem.component.GameSurface

@Immutable
data class AlertsSectionActions(val onPick: () -> Unit, val onClear: () -> Unit, val onPushToggle: (Boolean) -> Unit, val error: String?)

/**
 * Повітряні тривоги в профілі: місце (обирають батьки для всієї сім'ї) і push на цей телефон.
 * [alertsPush] == null — збірка без Firebase, перемикача немає.
 */
@Composable
fun AlertsSection(region: String?, isParent: Boolean, alertsPush: Boolean?, actions: AlertsSectionActions, modifier: Modifier = Modifier) {
    GameSurface(modifier.fillMaxWidth()) {
        Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text("🚨 Повітряні тривоги", style = MaterialTheme.typography.titleMedium)
            Text(
                region?.let(::shortName) ?: if (isParent) "Місце не обрано" else "Батьки ще не обрали місце",
                style = MaterialTheme.typography.bodyLarge,
            )
            if (isParent) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    GameButton(if (region == null) "Обрати місце" else "Змінити", onClick = actions.onPick, leading = "📍")
                    if (region != null) TextButton(onClick = actions.onClear) { Text("Вимкнути") }
                }
            }
            if (region != null && alertsPush != null) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("Сповіщати на цей телефон", style = MaterialTheme.typography.bodyLarge)
                        Text(
                            "Початок і відбій тривоги",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Switch(checked = alertsPush, onCheckedChange = actions.onPushToggle)
                }
            }
            actions.error?.let { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall) }
            Text(
                "Дані alerts.in.ua з затримкою до хвилини. Не замінює офіційні застосунки та сирени.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
