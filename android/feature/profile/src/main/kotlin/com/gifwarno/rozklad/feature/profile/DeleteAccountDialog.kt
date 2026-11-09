package com.gifwarno.rozklad.feature.profile

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.DialogProperties

/** Безповоротна дія: показуємо всі наслідки й просимо ввести слово-підтвердження. */
@Composable
internal fun DeleteAccountDialog(
    warnings: List<String>,
    pendingChanges: Int,
    deleteState: DeleteAccountState,
    onConfirm: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    var typed by rememberSaveable { mutableStateOf("") }
    val busy = deleteState == DeleteAccountState.InProgress
    val matches = typed.trim().uppercase() == DELETE_CONFIRM_WORD
    AlertDialog(
        onDismissRequest = { if (!busy) onDismiss() },
        // Під час видалення не даємо закрити діалог випадковим дотиком повз нього.
        properties = DialogProperties(dismissOnBackPress = !busy, dismissOnClickOutside = !busy),
        title = { Text("Видалити акаунт?") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                warnings.forEach { Text("• $it", style = MaterialTheme.typography.bodyMedium) }
                if (pendingChanges > 0) {
                    Text(
                        "• Ще не надіслані зміни з цього пристрою ($pendingChanges) теж буде втрачено.",
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
                Text("Щоб підтвердити, введи слово $DELETE_CONFIRM_WORD:", style = MaterialTheme.typography.bodyMedium)
                OutlinedTextField(
                    value = typed,
                    onValueChange = { typed = it.take(MAX_INPUT) },
                    singleLine = true,
                    enabled = !busy,
                    keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Characters),
                    modifier = Modifier.fillMaxWidth(),
                )
                if (deleteState is DeleteAccountState.Failed) {
                    Text(deleteState.message, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onConfirm(typed) },
                enabled = matches && !busy,
                colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error),
            ) { Text(if (busy) "Видаляємо…" else "Видалити назавжди") }
        },
        dismissButton = { TextButton(onClick = onDismiss, enabled = !busy) { Text("Скасувати") } },
    )
}

private const val MAX_INPUT = 20
