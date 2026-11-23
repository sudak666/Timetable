package com.gifwarno.rozklad.feature.profile

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.input.InputTransformation
import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.foundation.text.input.maxLengthTrim
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedSecureTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.gifwarno.rozklad.core.designsystem.component.GameDialog

/** Стан зміни пароля в профілі. */
sealed interface PasswordState {
    data object Idle : PasswordState

    data object Saving : PasswordState

    data object Done : PasswordState

    data class Failed(val message: String) : PasswordState
}

/** Новий пароль — від 8 символів (і не з бази злитих — перевіряє мережевий шар). */
internal const val MIN_PASSWORD = 8
private const val MAX_PASSWORD = 72

/** Що не так із новим паролем (null — можна зберігати). */
internal fun passwordProblem(p1: String, p2: String): String? = when {
    p1.length < MIN_PASSWORD -> "Від $MIN_PASSWORD символів"
    p2.isNotEmpty() && p1 != p2 -> "Паролі не збігаються"
    p1 != p2 -> ""
    else -> null
}

/** Новий пароль двічі. Старий не питаємо: акаунт уже відкритий на цьому пристрої (як у вебі). */
@Composable
internal fun PasswordDialog(state: PasswordState, onSave: (String) -> Unit, onDismiss: () -> Unit) {
    // Не rememberSaveable: збережений стан система пише на диск, а паролю там не місце.
    val f1 = remember { TextFieldState() }
    val f2 = remember { TextFieldState() }
    val p1 = f1.text.toString()
    val p2 = f2.text.toString()
    val problem = passwordProblem(p1, p2)
    val saving = state == PasswordState.Saving
    GameDialog(
        onDismissRequest = { if (!saving) onDismiss() },
        title = { Text("Змінити пароль") },
        text = {
            if (state == PasswordState.Done) {
                Text("Готово! Тепер входь із новим паролем.")
            } else {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    val field = KeyboardOptions(keyboardType = KeyboardType.Password, imeAction = ImeAction.Next)
                    val limit = InputTransformation.maxLengthTrim(MAX_PASSWORD)
                    OutlinedSecureTextField(
                        state = f1,
                        label = { Text("Новий пароль") },
                        supportingText = { Text("Від $MIN_PASSWORD символів") },
                        inputTransformation = limit,
                        keyboardOptions = field,
                    )
                    OutlinedSecureTextField(
                        state = f2,
                        label = { Text("Ще раз") },
                        isError = !problem.isNullOrEmpty() && p2.isNotEmpty(),
                        supportingText = problem?.takeIf { it.isNotEmpty() && p2.isNotEmpty() }?.let { { Text(it) } },
                        inputTransformation = limit,
                        keyboardOptions = field.copy(imeAction = ImeAction.Done),
                    )
                    (state as? PasswordState.Failed)?.let { Text(it.message, color = MaterialTheme.colorScheme.error) }
                }
            }
        },
        confirmButton = {
            if (state == PasswordState.Done) {
                TextButton(onClick = onDismiss) { Text("Готово") }
            } else {
                TextButton(onClick = { onSave(p1) }, enabled = problem == null && !saving) { Text(if (saving) "Зберігаю…" else "Зберегти") }
            }
        },
        dismissButton = if (state ==
            PasswordState.Done
        ) {
            null
        } else {
            ({ TextButton(onClick = onDismiss, enabled = !saving) { Text("Скасувати") } })
        },
    )
}
