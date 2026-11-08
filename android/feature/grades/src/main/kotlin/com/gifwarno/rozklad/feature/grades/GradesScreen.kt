package com.gifwarno.rozklad.feature.grades

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.Undo
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material3.Card
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.gifwarno.rozklad.core.designsystem.component.EmptyState
import com.gifwarno.rozklad.core.designsystem.component.LoadingState
import com.gifwarno.rozklad.core.domain.Rewards
import com.gifwarno.rozklad.core.model.Grade
import com.gifwarno.rozklad.core.model.GradeStatus
import com.gifwarno.rozklad.core.model.LedgerKind
import com.gifwarno.rozklad.core.ui.ChildSelector
import com.gifwarno.rozklad.core.ui.Format
import com.gifwarno.rozklad.core.ui.GradeBadge
import com.gifwarno.rozklad.core.ui.moneyColor
import java.time.LocalDate

@Composable
fun GradesRoute(modifier: Modifier = Modifier, viewModel: GradesViewModel = hiltViewModel()) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val message by viewModel.message.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    LaunchedEffect(message) {
        message?.let {
            snackbar.showSnackbar(it)
            viewModel.messageShown()
        }
    }
    GradesScreen(
        state = state,
        snackbar = snackbar,
        onAddGrade = viewModel::addGrade,
        onApprove = viewModel::approve,
        onUnapprove = viewModel::unapprove,
        onDelete = viewModel::delete,
        onSelectChild = viewModel::selectChild,
        onLedger = viewModel::addLedger,
        modifier = modifier,
    )
}

@Composable
internal fun GradesScreen(
    state: GradesUiState,
    snackbar: SnackbarHostState,
    onAddGrade: (String, Int, LocalDate) -> Unit,
    onApprove: (Grade) -> Unit,
    onUnapprove: (Grade) -> Unit,
    onDelete: (Grade) -> Unit,
    onSelectChild: (String) -> Unit,
    onLedger: (LedgerKind, Int, String) -> Unit,
    modifier: Modifier = Modifier,
) {
    var showAdd by rememberSaveable { mutableStateOf(false) }
    var ledgerKind by rememberSaveable { mutableStateOf<LedgerKind?>(null) }
    Scaffold(
        modifier = modifier,
        contentWindowInsets = WindowInsets(0),
        snackbarHost = { SnackbarHost(snackbar) },
        floatingActionButton = {
            if (state is GradesUiState.Ready) {
                ExtendedFloatingActionButton(
                    onClick = { showAdd = true },
                    icon = { Icon(Icons.Filled.Add, contentDescription = null) },
                    text = { Text("Оцінка") },
                )
            }
        },
    ) { padding ->
        when (state) {
            GradesUiState.Loading -> LoadingState(Modifier.padding(padding))

            is GradesUiState.NoChild -> EmptyState(
                emoji = "👨‍👩‍👧",
                title = if (state.isParent) "Додай дитину" else "Немає даних",
                message = if (state.isParent) "Дай дитині код сім’ї: ${state.inviteCode}" else null,
                modifier = Modifier.padding(padding),
            )

            is GradesUiState.Ready -> ReadyContent(
                state = state,
                padding = padding,
                onApprove = onApprove,
                onUnapprove = onUnapprove,
                onDelete = onDelete,
                onSelectChild = onSelectChild,
                onLedger = { ledgerKind = it },
            )
        }
    }
    if (showAdd) {
        AddGradeSheet(
            onDismiss = { showAdd = false },
            onAdd = { subject, value, date ->
                onAddGrade(subject, value, date)
                showAdd = false
            },
        )
    }
    ledgerKind?.let { kind ->
        LedgerDialog(
            kind = kind,
            onDismiss = { ledgerKind = null },
            onConfirm = { amount, note ->
                onLedger(kind, amount, note)
                ledgerKind = null
            },
        )
    }
}

@Composable
private fun ReadyContent(
    state: GradesUiState.Ready,
    padding: PaddingValues,
    onApprove: (Grade) -> Unit,
    onUnapprove: (Grade) -> Unit,
    onDelete: (Grade) -> Unit,
    onSelectChild: (String) -> Unit,
    onLedger: (LedgerKind) -> Unit,
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize().padding(padding),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 96.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        item(key = "kids") { ChildSelector(state.children, state.child.userId, onSelectChild) }
        item(key = "summary") { Summary(state, onLedger) }
        if (state.pending.isNotEmpty()) {
            item(key = "pending-title") { SectionTitle("Чекають підтвердження") }
            items(state.pending, key = { "p-" + it.grade.id }) { GradeRow(it, onApprove, onUnapprove, onDelete) }
        }
        item(key = "recent-title") { SectionTitle("Оцінки") }
        if (state.recent.isEmpty()) {
            item(key = "empty") {
                Text("Ще немає оцінок — додай першу кнопкою «Оцінка».", color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        items(state.recent, key = { "r-" + it.grade.id }) { GradeRow(it, onApprove, onUnapprove, onDelete) }
    }
}

@Composable
private fun SectionTitle(text: String) {
    Text(text, style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(top = 12.dp))
}

@Composable
private fun Summary(state: GradesUiState.Ready, onLedger: (LedgerKind) -> Unit) {
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("${state.child.avatar.ifBlank { "🙂" }} ${state.child.name}", style = MaterialTheme.typography.titleLarge)
                Text(
                    Format.balance(state.totals.balance),
                    modifier = Modifier.weight(1f),
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.End,
                )
            }
            Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                Stat("Сьогодні", Format.money(state.totals.today), moneyColor(state.totals.today))
                Stat("Тиждень", Format.money(state.totals.week), moneyColor(state.totals.week))
                Stat("Місяць", Format.money(state.totals.month), moneyColor(state.totals.month))
            }
            Text("Рівень ${state.level} · ${state.xpInLevel}/${Rewards.XP_PER_LEVEL} XP", style = MaterialTheme.typography.labelLarge)
            LinearProgressIndicator(
                progress = { state.xpInLevel / Rewards.XP_PER_LEVEL.toFloat() },
                modifier = Modifier.fillMaxWidth(),
            )
            Text(
                buildString {
                    append("🔥 Серія ${state.run}/${state.runTarget}")
                    state.average?.let { append(" · середній бал ${"%.1f".format(it)}") }
                },
                style = MaterialTheme.typography.bodyMedium,
            )
            if (state.child.goalTitle.isNotBlank() && state.child.goalAmount > 0) {
                Text("🎯 Ціль: ${state.child.goalTitle} — ${state.totals.balance.coerceAtLeast(0)} з ${state.child.goalAmount} ₴")
                LinearProgressIndicator(
                    progress = { (state.totals.balance.toFloat() / state.child.goalAmount).coerceIn(0f, 1f) },
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            if (state.isParent) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick = { onLedger(LedgerKind.BONUS) }) { Text("🎁 Бонус") }
                    OutlinedButton(onClick = { onLedger(LedgerKind.PAYOUT) }) { Text("💸 Виплатити") }
                }
            }
        }
    }
}

@Composable
private fun Stat(label: String, value: String, color: Color) {
    Column {
        Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, style = MaterialTheme.typography.titleMedium, color = color)
    }
}

@Composable
private fun GradeRow(item: GradeItem, onApprove: (Grade) -> Unit, onUnapprove: (Grade) -> Unit, onDelete: (Grade) -> Unit) {
    val g = item.grade
    ListItem(
        leadingContent = { GradeBadge(g.value) },
        headlineContent = { Text(g.subject) },
        supportingContent = {
            Text("${Format.dayShort(g.date)} · ${Format.money(item.money)}", color = moneyColor(item.money))
        },
        trailingContent = {
            Row {
                if (item.canApprove) {
                    IconButton(onClick = { onApprove(g) }) { Icon(Icons.Outlined.Check, contentDescription = "Підтвердити") }
                } else if (item.canDelete && g.status == GradeStatus.APPROVED) {
                    IconButton(onClick = { onUnapprove(g) }) {
                        Icon(Icons.AutoMirrored.Outlined.Undo, contentDescription = "Скасувати підтвердження")
                    }
                }
                if (item.canDelete) {
                    IconButton(onClick = { onDelete(g) }) { Icon(Icons.Outlined.Delete, contentDescription = "Видалити") }
                }
            }
        },
    )
}
