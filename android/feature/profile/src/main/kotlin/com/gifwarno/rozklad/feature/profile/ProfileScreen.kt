package com.gifwarno.rozklad.feature.profile

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
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
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.gifwarno.rozklad.core.datastore.ThemeMode
import com.gifwarno.rozklad.core.designsystem.component.LoadingState
import com.gifwarno.rozklad.core.model.Role

@Composable
fun ProfileRoute(modifier: Modifier = Modifier, viewModel: ProfileViewModel = hiltViewModel()) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    when (val s = state) {
        ProfileUiState.Loading -> LoadingState(modifier)

        is ProfileUiState.Ready -> ProfileScreen(
            state = s,
            onShowParentCode = viewModel::loadParentCode,
            onTheme = viewModel::setTheme,
            onSignOut = viewModel::signOut,
            modifier = modifier,
        )
    }
}

@Composable
internal fun ProfileScreen(
    state: ProfileUiState.Ready,
    onShowParentCode: () -> Unit,
    onTheme: (ThemeMode) -> Unit,
    onSignOut: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var confirmSignOut by rememberSaveable { mutableStateOf(false) }
    Column(
        modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(state.avatar, style = MaterialTheme.typography.displayMedium)
            Column {
                Text(state.name, style = MaterialTheme.typography.titleLarge)
                Text(state.email, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(if (state.isParent) "Батьки · ${state.familyName}" else state.familyName, style = MaterialTheme.typography.labelLarge)
            }
        }

        if (state.isParent) {
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Запросити в сім’ю", style = MaterialTheme.typography.titleMedium)
                    CodeRow("Код для дитини", state.childInviteCode)
                    val parent = state.parentInviteCode
                    if (parent == null) {
                        TextButton(onClick = onShowParentCode) { Text("Показати код для другого батька") }
                    } else {
                        CodeRow("Код для другого батька", parent)
                    }
                }
            }
        }

        Card(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(vertical = 8.dp)) {
                Text("Сім’я", style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(horizontal = 16.dp))
                state.members.forEach { m ->
                    ListItem(
                        leadingContent = { Text(m.avatar.ifBlank { "🙂" }, style = MaterialTheme.typography.titleLarge) },
                        headlineContent = { Text(m.name) },
                        supportingContent = { Text(if (m.role == Role.PARENT) "Батьки" else "Дитина") },
                    )
                }
            }
        }

        Text("Тема", style = MaterialTheme.typography.titleMedium)
        val modes = listOf(ThemeMode.SYSTEM to "Як у системі", ThemeMode.LIGHT to "Світла", ThemeMode.DARK to "Темна")
        SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
            modes.forEachIndexed { i, (mode, label) ->
                SegmentedButton(
                    selected = state.themeMode == mode,
                    onClick = { onTheme(mode) },
                    shape = SegmentedButtonDefaults.itemShape(i, modes.size),
                ) { Text(label) }
            }
        }

        HorizontalDivider()
        OutlinedButton(onClick = { confirmSignOut = true }, modifier = Modifier.fillMaxWidth()) { Text("Вийти") }
    }

    if (confirmSignOut) {
        AlertDialog(
            onDismissRequest = { confirmSignOut = false },
            title = { Text("Вийти з акаунта?") },
            text = { Text("Зміни, які ще не надіслано з цього пристрою, буде втрачено.") },
            confirmButton = {
                TextButton(onClick = {
                    confirmSignOut = false
                    onSignOut()
                }) { Text("Вийти") }
            },
            dismissButton = { TextButton(onClick = { confirmSignOut = false }) { Text("Скасувати") } },
        )
    }
}

@Composable
private fun CodeRow(label: String, code: String) {
    @Suppress("DEPRECATION")
    val clipboard = LocalClipboardManager.current
    Row(verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(label, style = MaterialTheme.typography.labelMedium)
            Text(code, style = MaterialTheme.typography.titleLarge, fontFamily = FontFamily.Monospace)
        }
        TextButton(onClick = { clipboard.setText(AnnotatedString(code)) }) { Text("Копіювати") }
    }
}
