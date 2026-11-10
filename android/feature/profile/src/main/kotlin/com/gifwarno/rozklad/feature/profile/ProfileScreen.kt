package com.gifwarno.rozklad.feature.profile

import android.Manifest
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
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
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.gifwarno.rozklad.core.datastore.ThemeMode
import com.gifwarno.rozklad.core.designsystem.component.ButtonShape
import com.gifwarno.rozklad.core.designsystem.component.GameButton
import com.gifwarno.rozklad.core.designsystem.component.GameSegmented
import com.gifwarno.rozklad.core.designsystem.component.GameSurface
import com.gifwarno.rozklad.core.designsystem.component.LoadingState
import com.gifwarno.rozklad.core.designsystem.component.Pill
import com.gifwarno.rozklad.core.designsystem.component.StrokeText
import com.gifwarno.rozklad.core.designsystem.theme.GameFont
import com.gifwarno.rozklad.core.designsystem.theme.GameTitleStyle
import com.gifwarno.rozklad.core.designsystem.theme.RozkladTheme
import com.gifwarno.rozklad.core.model.Role
import com.gifwarno.rozklad.core.ui.PrivacyPolicyLink
import com.gifwarno.rozklad.feature.profile.alerts.AlertsSection
import com.gifwarno.rozklad.feature.profile.alerts.AlertsSectionActions

@Composable
fun ProfileRoute(onPickAlertRegion: () -> Unit, modifier: Modifier = Modifier, viewModel: ProfileViewModel = hiltViewModel()) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val alertError by viewModel.alertError.collectAsStateWithLifecycle()
    val deleteState by viewModel.deleteState.collectAsStateWithLifecycle()
    val pushState by viewModel.pushState.collectAsStateWithLifecycle()
    // Android 13+: системний запит дозволу; вмикаємо лише якщо людина дозволила.
    // Що ввімкнути після дозволу: загальні сповіщення чи тривоги (тривоги вмикають і загальні).
    var pendingAlerts by rememberSaveable { mutableStateOf(false) }
    val permission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) {
            if (pendingAlerts) viewModel.setAlertsPushEnabled(true) else viewModel.setPushEnabled(true)
        }
    }
    val onPushToggle: (Boolean) -> Unit = { on ->
        if (on && viewModel.needsNotificationPermission() && Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            pendingAlerts = false
            permission.launch(Manifest.permission.POST_NOTIFICATIONS)
        } else {
            viewModel.setPushEnabled(on)
        }
    }
    val onAlertsToggle: (Boolean) -> Unit = { on ->
        if (on && viewModel.needsNotificationPermission() && Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            pendingAlerts = true
            permission.launch(Manifest.permission.POST_NOTIFICATIONS)
        } else {
            viewModel.setAlertsPushEnabled(on)
        }
    }
    val alerts = AlertsSectionActions(
        onPick = onPickAlertRegion,
        onClear = viewModel::clearAlertRegion,
        onPushToggle = onAlertsToggle,
        error = alertError,
    )
    when (val s = state) {
        ProfileUiState.Loading -> LoadingState(modifier)

        is ProfileUiState.Ready -> ProfileScreen(
            state = s,
            onShowParentCode = viewModel::loadParentCode,
            onTheme = viewModel::setTheme,
            onSignOut = viewModel::signOut,
            deleteState = deleteState,
            onDeleteAccount = viewModel::deleteAccount,
            onDeleteDialogClosed = viewModel::resetDeleteState,
            pushState = pushState,
            onPushToggle = onPushToggle,
            alerts = alerts,
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
    deleteState: DeleteAccountState,
    onDeleteAccount: (String) -> Unit,
    onDeleteDialogClosed: () -> Unit,
    pushState: PushUiState?,
    onPushToggle: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    alerts: AlertsSectionActions? = null,
) {
    var confirmSignOut by rememberSaveable { mutableStateOf(false) }
    var confirmDelete by rememberSaveable { mutableStateOf(false) }
    val c = RozkladTheme.colors
    Column(
        modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        GameSurface(
            brush = Brush.linearGradient(listOf(c.accent, Color(0xFFB062E8), c.accentEnd)),
            contentColor = Color.White,
            depth = 6.dp,
        ) {
            Row(Modifier.fillMaxWidth().padding(18.dp), verticalAlignment = Alignment.CenterVertically) {
                Box(
                    Modifier
                        .size(76.dp)
                        .clip(CircleShape)
                        .background(Brush.linearGradient(listOf(c.gold, c.goldEnd)))
                        .border(3.dp, c.ink, CircleShape),
                    contentAlignment = Alignment.Center,
                ) { Text(state.avatar.ifBlank { "🙂" }, fontSize = 42.sp) }
                Column(Modifier.weight(1f).padding(start = 14.dp)) {
                    StrokeText(state.name, GameTitleStyle.copy(fontSize = 22.sp, lineHeight = 26.sp), strokeWidth = 4.dp, maxLines = 2)
                    Text(state.email, style = MaterialTheme.typography.bodySmall, color = Color.White.copy(alpha = 0.85f), maxLines = 1)
                    Pill(
                        if (state.isParent) "👑 Батьки · ${state.familyName}" else "🏠 ${state.familyName}",
                        Modifier.padding(top = 6.dp),
                        color = Color.White.copy(alpha = 0.2f),
                        contentColor = Color.White,
                    )
                }
            }
        }

        if (state.isParent) {
            GameCard("🎟️ Запросити в сім’ю") {
                CodeRow("Код для дитини", state.childInviteCode)
                val parent = state.parentInviteCode
                if (parent == null) {
                    TextButton(onClick = onShowParentCode) { Text("Показати код для другого батька") }
                } else {
                    CodeRow("Код для другого батька", parent)
                }
            }
        }

        GameCard("👨‍👩‍👧 Сім’я") {
            state.members.forEach { m ->
                Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.size(44.dp).clip(CircleShape).background(c.soft), contentAlignment = Alignment.Center) {
                        Text(m.avatar.ifBlank { "🙂" }, fontSize = 24.sp)
                    }
                    Text(
                        m.name,
                        Modifier.weight(1f).padding(start = 12.dp),
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = FontWeight.ExtraBold,
                    )
                    Pill(if (m.role == Role.PARENT) "батьки" else "дитина")
                }
            }
        }

        if (pushState != null) {
            GameCard("🔔 Сповіщення") {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("На цей телефон", style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.ExtraBold)
                        Text(
                            "Нові оцінки, підтвердження, бонуси й домашка на завтра",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Switch(checked = pushState.enabled, onCheckedChange = onPushToggle)
                }
                pushState.error?.let { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall) }
            }
        }

        if (alerts != null) {
            AlertsSection(
                region = state.alertRegion,
                isParent = state.isParent,
                alertsPush = pushState?.let { it.enabled && it.alerts },
                actions = alerts,
            )
        }

        GameCard("🎨 Тема") {
            GameSegmented(
                options = listOf(ThemeMode.SYSTEM to "Як у системі", ThemeMode.LIGHT to "Світла", ThemeMode.DARK to "Темна"),
                selected = state.themeMode,
                onSelect = onTheme,
            )
        }

        GameCard("⚙️ Акаунт") {
            GameButton(
                "Вийти",
                onClick = { confirmSignOut = true },
                modifier = Modifier.fillMaxWidth(),
                color = c.soft,
                contentColor = MaterialTheme.colorScheme.onSurface,
            )
            TextButton(
                onClick = { confirmDelete = true },
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error),
            ) { Text("Видалити акаунт") }
            PrivacyPolicyLink(Modifier.fillMaxWidth())
        }
    }

    if (confirmDelete) {
        DeleteAccountDialog(
            warnings = state.deletionWarnings,
            pendingChanges = state.pendingChanges,
            deleteState = deleteState,
            onConfirm = onDeleteAccount,
            onDismiss = {
                confirmDelete = false
                onDeleteDialogClosed()
            },
        )
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

/** Картка розділу профілю із заголовком. */
@Composable
internal fun GameCard(title: String, modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    GameSurface(modifier.fillMaxWidth()) {
        Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium)
            content()
        }
    }
}

@Composable
private fun CodeRow(label: String, code: String) {
    @Suppress("DEPRECATION")
    val clipboard = LocalClipboardManager.current
    Row(
        Modifier.fillMaxWidth().clip(
            ButtonShape,
        ).background(RozkladTheme.colors.soft).padding(start = 14.dp, end = 6.dp, top = 8.dp, bottom = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(label, style = MaterialTheme.typography.labelMedium, color = RozkladTheme.colors.muted)
            Text(code, style = MaterialTheme.typography.titleLarge, fontFamily = GameFont, letterSpacing = 3.sp)
        }
        GameButton("Копіювати", onClick = { clipboard.setText(AnnotatedString(code)) })
    }
}
