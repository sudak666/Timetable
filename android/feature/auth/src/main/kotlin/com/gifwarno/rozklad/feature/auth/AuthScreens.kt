package com.gifwarno.rozklad.feature.auth

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.autofill.ContentType
import androidx.compose.ui.semantics.contentType
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.gifwarno.rozklad.core.designsystem.theme.RozkladTheme
import com.gifwarno.rozklad.core.ui.PrivacyPolicyLink

@Composable
fun AuthRoute(modifier: Modifier = Modifier, viewModel: AuthViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    AuthScreen(state, viewModel::onEmail, viewModel::onPassword, viewModel::submit, viewModel::toggleMode, modifier)
}

@Composable
internal fun AuthScreen(
    state: AuthUiState,
    onEmail: (String) -> Unit,
    onPassword: (String) -> Unit,
    onSubmit: () -> Unit,
    onToggleMode: () -> Unit,
    modifier: Modifier = Modifier,
) {
    CenteredForm(modifier) {
        Text(
            "12",
            style = MaterialTheme.typography.displayLarge,
            fontWeight = FontWeight.Black,
            color = MaterialTheme.colorScheme.primary,
        )
        Text("Дванадцятка", style = MaterialTheme.typography.headlineMedium)
        Text(
            "Оцінки, кишенькові й розклад — для всієї сім’ї",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
        OutlinedTextField(
            value = state.email,
            onValueChange = onEmail,
            label = { Text("Пошта") },
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email, imeAction = ImeAction.Next),
            modifier = Modifier.fillMaxWidth().semantics { contentType = ContentType.EmailAddress },
        )
        OutlinedTextField(
            value = state.password,
            onValueChange = onPassword,
            label = { Text("Пароль") },
            supportingText = if (state.isSignUp) ({ Text("Щонайменше ${AuthUiState.MIN_PASSWORD} символів") }) else null,
            singleLine = true,
            visualTransformation = PasswordVisualTransformation(),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password, imeAction = ImeAction.Done),
            keyboardActions = KeyboardActions(onDone = { onSubmit() }),
            modifier = Modifier.fillMaxWidth().semantics {
                contentType = if (state.isSignUp) ContentType.NewPassword else ContentType.Password
            },
        )
        state.message?.let {
            Text(
                it,
                color = if (state.messageIsError) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary,
                textAlign = TextAlign.Center,
            )
        }
        Button(onClick = onSubmit, enabled = state.canSubmit, modifier = Modifier.fillMaxWidth()) {
            Text(
                when {
                    state.busy -> "Зачекай…"
                    state.isSignUp -> "Зареєструватися"
                    else -> "Увійти"
                },
            )
        }
        TextButton(onClick = onToggleMode) {
            Text(if (state.isSignUp) "Вже маю акаунт — увійти" else "Немає акаунта? Зареєструватися")
        }
        PrivacyPolicyLink()
    }
}

@Composable
fun JoinFamilyRoute(modifier: Modifier = Modifier, viewModel: JoinFamilyViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    JoinFamilyScreen(state, viewModel::onName, viewModel::onCode, viewModel::create, viewModel::join, viewModel::signOut, modifier)
}

@Composable
internal fun JoinFamilyScreen(
    state: JoinUiState,
    onName: (String) -> Unit,
    onCode: (String) -> Unit,
    onCreate: () -> Unit,
    onJoin: () -> Unit,
    onSignOut: () -> Unit,
    modifier: Modifier = Modifier,
) {
    CenteredForm(modifier) {
        Text("👨‍👩‍👧", style = MaterialTheme.typography.displayLarge)
        Text("Майже готово", style = MaterialTheme.typography.headlineMedium)
        OutlinedTextField(
            value = state.name,
            onValueChange = onName,
            label = { Text("Як тебе звати") },
            singleLine = true,
            keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words, imeAction = ImeAction.Next),
            modifier = Modifier.fillMaxWidth(),
        )
        Text("Я дитина або другий з батьків", style = MaterialTheme.typography.titleMedium)
        OutlinedTextField(
            value = state.code,
            onValueChange = onCode,
            label = { Text("Код сім’ї") },
            singleLine = true,
            keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Characters, imeAction = ImeAction.Done),
            keyboardActions = KeyboardActions(onDone = { onJoin() }),
            modifier = Modifier.fillMaxWidth(),
        )
        Button(onClick = onJoin, enabled = state.canJoin, modifier = Modifier.fillMaxWidth()) { Text("Приєднатися") }
        HorizontalDivider(Modifier.padding(vertical = 8.dp))
        Text("Я з батьків і починаю", style = MaterialTheme.typography.titleMedium)
        OutlinedButton(onClick = onCreate, enabled = state.canCreate, modifier = Modifier.fillMaxWidth()) { Text("Створити сім’ю") }
        state.error?.let { Text(it, color = MaterialTheme.colorScheme.error, textAlign = TextAlign.Center) }
        TextButton(onClick = onSignOut) { Text("Вийти з акаунта") }
    }
}

/** Помилка без мережі й кешу — пропонуємо повторити. */
@Composable
fun UnavailableScreen(message: String, onRetry: () -> Unit, onSignOut: () -> Unit, modifier: Modifier = Modifier) {
    CenteredForm(modifier) {
        Text("📡", style = MaterialTheme.typography.displayLarge)
        Text(message, style = MaterialTheme.typography.titleMedium, textAlign = TextAlign.Center)
        Button(onClick = onRetry) { Text("Спробувати ще раз") }
        TextButton(onClick = onSignOut) { Text("Вийти") }
    }
}

@Composable
private fun CenteredForm(modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    Column(
        modifier = modifier.fillMaxSize().imePadding().verticalScroll(rememberScrollState()).padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp, Alignment.CenterVertically),
    ) {
        Column(
            Modifier.widthIn(max = 420.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) { content() }
    }
}

@Preview(showBackground = true, locale = "uk")
@Composable
private fun AuthPreview() {
    RozkladTheme { AuthScreen(AuthUiState(email = "mama@example.com"), {}, {}, {}, {}) }
}
