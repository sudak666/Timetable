package com.gifwarno.rozklad.feature.auth

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.autofill.ContentType
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.contentType
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.gifwarno.rozklad.core.designsystem.component.ButtonShape
import com.gifwarno.rozklad.core.designsystem.component.GameButton
import com.gifwarno.rozklad.core.designsystem.component.GameSurface
import com.gifwarno.rozklad.core.designsystem.component.StrokeText
import com.gifwarno.rozklad.core.designsystem.theme.GameFont
import com.gifwarno.rozklad.core.designsystem.theme.GameTitleStyle
import com.gifwarno.rozklad.core.designsystem.theme.RozkladTheme
import com.gifwarno.rozklad.core.ui.PrivacyPolicyLink

@Composable
fun AuthRoute(modifier: Modifier = Modifier, viewModel: AuthViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    AuthScreen(
        state,
        viewModel::onEmail,
        viewModel::onPassword,
        viewModel::submit,
        viewModel::toggleMode,
        viewModel::signInWithGoogle,
        modifier,
        onForgot = viewModel::forgotPassword,
    )
}

@Composable
internal fun AuthScreen(
    state: AuthUiState,
    onEmail: (String) -> Unit,
    onPassword: (String) -> Unit,
    onSubmit: () -> Unit,
    onToggleMode: () -> Unit,
    onGoogle: () -> Unit,
    modifier: Modifier = Modifier,
    onForgot: () -> Unit = {},
) {
    CenteredForm(header = { Logo() }, modifier = modifier) {
        Text(
            "Оцінки, кишенькові й розклад — для всієї сім’ї",
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
        // Як на сайті: більшість сімей входить через Google.
        GameSurface(
            modifier = Modifier.fillMaxWidth(),
            shape = ButtonShape,
            color = Color.White,
            contentColor = Color(GOOGLE_TEXT),
            onClick = if (state.busy) null else onGoogle,
        ) {
            Row(
                Modifier.fillMaxWidth().padding(vertical = 12.dp),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                // Фірмова кольорова «G» — так вимагають правила бренду Google для кнопок входу.
                Image(painterResource(R.drawable.ic_google_g), contentDescription = null, Modifier.size(20.dp))
                Spacer(Modifier.width(12.dp))
                Text("Увійти через Google", style = MaterialTheme.typography.labelLarge)
            }
        }
        Text(
            "або поштою",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(vertical = 6.dp),
        )
        OutlinedTextField(
            value = state.email,
            onValueChange = onEmail,
            label = { Text("Пошта") },
            singleLine = true,
            shape = ButtonShape,
            colors = fieldColors(),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email, imeAction = ImeAction.Next),
            modifier = Modifier.fillMaxWidth().semantics { contentType = ContentType.EmailAddress },
        )
        OutlinedTextField(
            value = state.password,
            onValueChange = onPassword,
            label = { Text("Пароль") },
            supportingText = if (state.isSignUp) ({ Text("Щонайменше ${AuthUiState.MIN_PASSWORD} символів") }) else null,
            singleLine = true,
            shape = ButtonShape,
            colors = fieldColors(),
            visualTransformation = PasswordVisualTransformation(),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password, imeAction = ImeAction.Done),
            keyboardActions = KeyboardActions(onDone = { onSubmit() }),
            modifier = Modifier.fillMaxWidth().semantics {
                contentType = if (state.isSignUp) ContentType.NewPassword else ContentType.Password
            },
        )
        // Одразу під паролем — там, де його шукають, коли пароль не підходить.
        if (!state.isSignUp) {
            TextButton(
                onClick = onForgot,
                enabled = !state.busy,
                modifier = Modifier.align(Alignment.End).offset(y = (-8).dp),
            ) { Text("Забули пароль?") }
        }
        state.message?.let {
            Text(
                it,
                color = if (state.messageIsError) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary,
                textAlign = TextAlign.Center,
            )
        }
        GameButton(
            text = when {
                state.busy -> "Зачекай…"
                state.isSignUp -> "Зареєструватися"
                else -> "Увійти"
            },
            onClick = onSubmit,
            enabled = state.canSubmit,
            modifier = Modifier.fillMaxWidth(),
        )
        TextButton(onClick = onToggleMode) {
            Text(if (state.isSignUp) "Вже маю акаунт — увійти" else "Немає акаунта? Зареєструватися")
        }
        PrivacyPolicyLink()
    }
}

/** «12» великими ігровими цифрами й назва. */
@Composable
private fun Logo() {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        StrokeText(
            "12",
            TextStyle(fontFamily = GameFont, fontSize = 96.sp, lineHeight = 100.sp),
            color = RozkladTheme.colors.gold,
            strokeWidth = 8.dp,
        )
        StrokeText("Дванадцятка", GameTitleStyle.copy(fontSize = 30.sp, lineHeight = 36.sp), strokeWidth = 5.dp)
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
    CenteredForm(
        modifier = modifier,
        header = {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text("👨‍👩‍👧", fontSize = 64.sp)
                StrokeText("Майже готово!", GameTitleStyle.copy(fontSize = 28.sp, lineHeight = 34.sp), strokeWidth = 5.dp)
            }
        },
    ) {
        // Спершу роль, потім одна дія — замість двох головних кнопок одночасно.
        var joining by rememberSaveable { mutableStateOf(true) }
        OutlinedTextField(
            value = state.name,
            onValueChange = onName,
            label = { Text("Як тебе звати") },
            singleLine = true,
            shape = ButtonShape,
            colors = fieldColors(),
            keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words, imeAction = ImeAction.Next),
            modifier = Modifier.fillMaxWidth(),
        )
        SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
            SegmentedButton(
                selected = joining,
                onClick = { joining = true },
                shape = SegmentedButtonDefaults.itemShape(index = 0, count = 2),
                colors = roleColors(),
                icon = {},
            ) { Text("🧒 Маю код") }
            SegmentedButton(
                selected = !joining,
                onClick = { joining = false },
                shape = SegmentedButtonDefaults.itemShape(index = 1, count = 2),
                colors = roleColors(),
                icon = {},
            ) { Text("👑 Нова сім’я") }
        }
        if (joining) {
            Text(
                "Для дитини або другого з батьків: код дає той, хто створив сім’ю.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
            OutlinedTextField(
                value = state.code,
                onValueChange = onCode,
                label = { Text("Код сім’ї") },
                singleLine = true,
                shape = ButtonShape,
                colors = fieldColors(),
                textStyle = MaterialTheme.typography.titleLarge.copy(fontFamily = GameFont, letterSpacing = 3.sp),
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Characters, imeAction = ImeAction.Done),
                keyboardActions = KeyboardActions(onDone = { onJoin() }),
                modifier = Modifier.fillMaxWidth(),
            )
            GameButton("Приєднатися", onJoin, Modifier.fillMaxWidth(), enabled = state.canJoin, leading = "🚀")
        } else {
            Text(
                "Ти з батьків і починаєш: створиш сім’ю й отримаєш код для дитини.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
            GameButton(
                "Створити сім’ю",
                onCreate,
                Modifier.fillMaxWidth(),
                color = RozkladTheme.colors.positive,
                contentColor = RozkladTheme.colors.onPositive,
                enabled = state.canCreate,
                leading = "✨",
            )
        }
        state.error?.let { Text(it, color = MaterialTheme.colorScheme.error, textAlign = TextAlign.Center) }
        TextButton(onClick = onSignOut) { Text("Вийти з акаунта") }
    }
}

/** Помилка без мережі й кешу — пропонуємо повторити. */
@Composable
fun UnavailableScreen(message: String, onRetry: () -> Unit, onSignOut: () -> Unit, modifier: Modifier = Modifier) {
    CenteredForm(header = { Text("📡", fontSize = 64.sp) }, modifier = modifier) {
        Text(message, style = MaterialTheme.typography.titleMedium, textAlign = TextAlign.Center)
        GameButton("Спробувати ще раз", onRetry, Modifier.fillMaxWidth(), leading = "🔄")
        TextButton(onClick = onSignOut) { Text("Вийти") }
    }
}

/** Заголовок на тлі «арени», під ним — картка з формою. */
@Composable
private fun CenteredForm(header: @Composable () -> Unit, modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    Column(
        modifier = modifier.fillMaxSize().imePadding().verticalScroll(rememberScrollState()).padding(20.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterVertically),
    ) {
        header()
        GameSurface(Modifier.widthIn(max = 440.dp).fillMaxWidth(), depth = 6.dp) {
            Column(
                Modifier.fillMaxWidth().padding(20.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(12.dp),
                content = content,
            )
        }
    }
}

/** Вибрана роль — фірмовим фіолетовим, як активна вкладка внизу. */
@Composable
private fun roleColors() = SegmentedButtonDefaults.colors(
    activeContainerColor = RozkladTheme.colors.accent,
    activeContentColor = Color.White,
    activeBorderColor = RozkladTheme.colors.accent,
)

/** Поля з виразнішою рамкою: тонка стандартна майже зливається з темною карткою. */
@Composable
private fun fieldColors() = OutlinedTextFieldDefaults.colors(
    unfocusedBorderColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = FIELD_BORDER_ALPHA),
)

@Preview(showBackground = true, locale = "uk")
@Composable
private fun AuthPreview() {
    RozkladTheme { AuthScreen(AuthUiState(email = "mama@example.com"), {}, {}, {}, {}, {}) }
}

private const val FIELD_BORDER_ALPHA = 0.75f
private const val GOOGLE_TEXT = 0xFF1F1F1F
