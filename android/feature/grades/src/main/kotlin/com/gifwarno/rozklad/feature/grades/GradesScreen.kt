package com.gifwarno.rozklad.feature.grades

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.Undo
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.gifwarno.rozklad.core.designsystem.component.ArenaTitle
import com.gifwarno.rozklad.core.designsystem.component.ButtonShape
import com.gifwarno.rozklad.core.designsystem.component.EmptyState
import com.gifwarno.rozklad.core.designsystem.component.GameButton
import com.gifwarno.rozklad.core.designsystem.component.GameFab
import com.gifwarno.rozklad.core.designsystem.component.GameProgressBar
import com.gifwarno.rozklad.core.designsystem.component.GameSurface
import com.gifwarno.rozklad.core.designsystem.component.GradeTile
import com.gifwarno.rozklad.core.designsystem.component.LoadingState
import com.gifwarno.rozklad.core.designsystem.component.Pill
import com.gifwarno.rozklad.core.designsystem.component.StatTile
import com.gifwarno.rozklad.core.designsystem.component.StrokeText
import com.gifwarno.rozklad.core.designsystem.theme.GameFont
import com.gifwarno.rozklad.core.designsystem.theme.GameTitleStyle
import com.gifwarno.rozklad.core.designsystem.theme.RozkladTheme
import com.gifwarno.rozklad.core.domain.Rewards
import com.gifwarno.rozklad.core.domain.Timetable
import com.gifwarno.rozklad.core.model.Grade
import com.gifwarno.rozklad.core.model.GradeStatus
import com.gifwarno.rozklad.core.model.LedgerKind
import com.gifwarno.rozklad.core.ui.BadgesCard
import com.gifwarno.rozklad.core.ui.ChildSelector
import com.gifwarno.rozklad.core.ui.Format
import com.gifwarno.rozklad.core.ui.TrophyRoad
import com.gifwarno.rozklad.core.ui.gradeColor
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
        onFine = viewModel::addFine,
        onAddChallenge = viewModel::addChallenge,
        onDeleteChallenge = viewModel::deleteChallenge,
        onDeleteLedger = viewModel::deleteLedger,
        onSetGoal = viewModel::setGoal,
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
    onFine: (Int, String) -> Unit = { _, _ -> },
    onAddChallenge: (ChallengeDraft) -> Unit = {},
    onDeleteChallenge: (String) -> Unit = {},
    onDeleteLedger: (String) -> Unit = {},
    onSetGoal: (String, Int) -> Unit = { _, _ -> },
) {
    var goalOpen by rememberSaveable { mutableStateOf(false) }
    var showAdd by rememberSaveable { mutableStateOf(false) }
    var money by rememberSaveable { mutableStateOf<Money?>(null) }
    var newChallenge by rememberSaveable { mutableStateOf(false) }
    Scaffold(
        modifier = modifier,
        containerColor = Color.Transparent,
        contentWindowInsets = WindowInsets(0),
        snackbarHost = { SnackbarHost(snackbar) },
        floatingActionButton = {
            if (state is GradesUiState.Ready) GameFab("Оцінка", onClick = { showAdd = true })
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
                onMoney = { money = it },
                onNewChallenge = { newChallenge = true },
                onDeleteChallenge = onDeleteChallenge,
                onEditGoal = { goalOpen = true },
                onDeleteLedger = onDeleteLedger,
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
    money?.let { m ->
        LedgerDialog(
            money = m,
            onDismiss = { money = null },
            onConfirm = { amount, note ->
                when (m) {
                    Money.BONUS -> onLedger(LedgerKind.BONUS, amount, note)
                    Money.PAYOUT -> onLedger(LedgerKind.PAYOUT, amount, note)
                    Money.FINE -> onFine(amount, note)
                }
                money = null
            },
        )
    }
    if (goalOpen && state is GradesUiState.Ready) {
        GoalDialog(
            title = state.child.goalTitle,
            amount = state.child.goalAmount,
            onDismiss = { goalOpen = false },
            onSave = { t, a ->
                onSetGoal(t, a)
                goalOpen = false
            },
        )
    }
    if (newChallenge) {
        ChallengeSheet(
            onDismiss = { newChallenge = false },
            onCreate = {
                onAddChallenge(it)
                newChallenge = false
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
    onMoney: (Money) -> Unit,
    onNewChallenge: () -> Unit,
    onDeleteChallenge: (String) -> Unit,
    onEditGoal: () -> Unit,
    onDeleteLedger: (String) -> Unit,
) {
    val actions = RowActions(onApprove, onUnapprove, onDelete)
    LazyColumn(
        modifier = Modifier.fillMaxSize().padding(padding),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 96.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        item(key = "kids") { ChildSelector(state.children, state.child.userId, onSelectChild) }
        item(key = "player") { PlayerCard(state, onMoney, onEditGoal) }
        if (state.road.isNotEmpty()) {
            item(key = "road-title") { ArenaTitle("🏆 Шлях нагород") }
            item(key = "road") { TrophyRoad(state.road) }
        }
        if (state.badges.isNotEmpty()) item(key = "badges") { BadgesCard(state.badges) }
        if (state.challenges.isNotEmpty() || state.isParent) {
            item(key = "ch-title") { ArenaTitle("🏅 Челенджі") }
            item(key = "challenges") {
                ChallengesCard(
                    items = state.challenges,
                    isParent = state.isParent,
                    onNew = onNewChallenge,
                    onDelete = onDeleteChallenge,
                )
            }
        }
        if (state.pending.isNotEmpty()) {
            item(key = "pending-title") { ArenaTitle("⏳ Чекають підтвердження") }
            item(key = "pending") { GradeList(state.pending, actions) }
        }
        if (state.compare != null || state.rank.isNotEmpty() || state.subjectAvgs.isNotEmpty()) {
            item(key = "stats-title") { ArenaTitle("📊 Статистика") }
            item(key = "stats") { StatsCard(state) }
        }
        item(key = "history-title") { ArenaTitle("🧾 Історія") }
        item(key = "history") { HistoryCard(state.history, actions, onDeleteLedger) }
    }
}

/** «Профіль гравця»: аватар, рівень, гаманець, серія й ціль. */
@Composable
private fun PlayerCard(state: GradesUiState.Ready, onMoney: (Money) -> Unit, onEditGoal: () -> Unit) {
    val c = RozkladTheme.colors
    val tile = Color.White.copy(alpha = 0.16f)
    GameSurface(brush = Brush.linearGradient(listOf(c.accent, Color(0xFFB062E8), c.accentEnd)), contentColor = Color.White, depth = 6.dp) {
        Column(Modifier.fillMaxWidth().padding(18.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Avatar(state.child.avatar)
                Column(Modifier.weight(1f).padding(start = 12.dp)) {
                    StrokeText(
                        state.child.name,
                        GameTitleStyle.copy(fontSize = 22.sp, lineHeight = 26.sp),
                        strokeWidth = 4.dp,
                        maxLines = 1,
                    )
                    Pill("Рівень ${state.level}", color = c.gold, contentColor = c.ink, modifier = Modifier.padding(top = 4.dp))
                }
                Column(horizontalAlignment = Alignment.End) {
                    Text("💰 гаманець", style = MaterialTheme.typography.labelSmall)
                    StrokeText(Format.balance(state.totals.balance), GameTitleStyle, strokeWidth = 4.dp, maxLines = 1)
                }
            }
            Column {
                Row {
                    Text("⚡ Досвід", style = MaterialTheme.typography.labelLarge, modifier = Modifier.weight(1f))
                    Text("${state.xpInLevel}/${Rewards.XP_PER_LEVEL} XP", style = MaterialTheme.typography.labelLarge)
                }
                GameProgressBar(
                    state.xpInLevel / Rewards.XP_PER_LEVEL.toFloat(),
                    Modifier.padding(top = 6.dp),
                    track = Color.Black.copy(alpha = 0.2f),
                )
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                StatTile("сьогодні", Format.money(state.totals.today), Modifier.weight(1f), Color.White, tile)
                StatTile("тиждень", Format.money(state.totals.week), Modifier.weight(1f), Color.White, tile)
                StatTile("місяць", Format.money(state.totals.month), Modifier.weight(1f), Color.White, tile)
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Pill("🔥 Серія ${state.run}/${state.runTarget}", color = tile, contentColor = Color.White)
                state.average?.let { Pill("📊 середній ${"%.1f".format(it)}", color = tile, contentColor = Color.White) }
            }
            if (state.child.goalTitle.isNotBlank() && state.child.goalAmount > 0) {
                Column(Modifier.clip(ButtonShape).background(tile).padding(12.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            "🎯 Ціль: ${state.child.goalTitle}",
                            style = MaterialTheme.typography.titleSmall,
                            modifier = Modifier.weight(1f),
                        )
                        Pill(
                            "змінити",
                            Modifier.clickable(onClickLabel = "Змінити ціль", onClick = onEditGoal),
                            color = Color.White.copy(alpha = 0.25f),
                            contentColor = Color.White,
                        )
                    }
                    GameProgressBar(
                        (state.totals.balance.toFloat() / state.child.goalAmount).coerceIn(0f, 1f),
                        Modifier.padding(vertical = 6.dp),
                        fill = Brush.horizontalGradient(listOf(Color(0xFF55EFC4), Color(0xFF00B894))),
                        track = Color.Black.copy(alpha = 0.2f),
                    )
                    val left = state.child.goalAmount - state.totals.balance.coerceAtLeast(0)
                    Text(
                        "${state.totals.balance.coerceAtLeast(0)} з ${state.child.goalAmount} ₴ · " +
                            if (left <= 0) "🎉 можна купувати!" else "ще $left ₴",
                        style = MaterialTheme.typography.labelMedium,
                    )
                }
            } else {
                GameButton(
                    "Постав ціль — на що збираєш?",
                    onEditGoal,
                    Modifier.fillMaxWidth(),
                    color = Color.White.copy(alpha = 0.9f),
                    contentColor = c.ink,
                    leading = "🎯",
                )
            }
            if (state.isParent) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    GameButton("Бонус", { onMoney(Money.BONUS) }, Modifier.weight(1f), color = c.gold, contentColor = c.ink, leading = "🎁")
                    GameButton("Штраф", { onMoney(Money.FINE) }, Modifier.weight(1f), color = Color(0xFFC0392B), leading = "⚠️")
                }
                GameButton(
                    "Виплатити",
                    { onMoney(Money.PAYOUT) },
                    Modifier.fillMaxWidth(),
                    color = Color(0xFF00A383),
                    leading = "💸",
                    enabled = state.totals.balance > 0,
                )
            }
        }
    }
}

@Composable
private fun Avatar(emoji: String) {
    val c = RozkladTheme.colors
    Box(
        Modifier
            .size(60.dp)
            .clip(CircleShape)
            .background(Brush.linearGradient(listOf(c.gold, c.goldEnd)))
            .border(3.dp, c.ink, CircleShape),
        contentAlignment = Alignment.Center,
    ) {
        Text(emoji.ifBlank { "🙂" }, fontSize = 32.sp)
    }
}

internal class RowActions(val onApprove: (Grade) -> Unit, val onUnapprove: (Grade) -> Unit, val onDelete: (Grade) -> Unit)

@Composable
private fun GradeList(items: List<GradeItem>, actions: RowActions) {
    GameSurface {
        Column(Modifier.fillMaxWidth().padding(horizontal = 6.dp, vertical = 4.dp)) {
            items.forEachIndexed { i, item ->
                if (i > 0) HorizontalDivider(Modifier.padding(horizontal = 8.dp), color = RozkladTheme.colors.line)
                GradeRow(item, actions)
            }
        }
    }
}

@Composable
internal fun GradeRow(item: GradeItem, actions: RowActions) {
    val c = RozkladTheme.colors
    val g = item.grade
    val pending = g.status != GradeStatus.APPROVED
    Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        GradeTile(Rewards.gradeLabel(g.value), gradeColor(g.value))
        Column(Modifier.weight(1f).padding(start = 12.dp)) {
            Text(
                "${Timetable.subject(g.subject).emoji} ${g.subject}",
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.ExtraBold,
                maxLines = 1,
            )
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 2.dp)) {
                Text(Format.dayShort(g.date), style = MaterialTheme.typography.bodySmall, color = c.muted)
                if (item.money != 0) {
                    Pill(
                        Format.money(item.money),
                        Modifier.padding(start = 8.dp),
                        color = (if (item.money > 0) c.positive else c.negative).copy(alpha = 0.16f),
                        contentColor = if (item.money > 0) c.positive else c.negative,
                    )
                }
                if (pending && !item.canApprove) Pill("чекає", Modifier.padding(start = 6.dp))
            }
        }
        if (item.canApprove) {
            GameSurface(
                shape = ButtonShape,
                color = c.positive,
                contentColor = Color.White,
                depth = 3.dp,
                onClick = { actions.onApprove(g) },
                onClickLabel = "Підтвердити",
            ) {
                Text("✓", Modifier.padding(horizontal = 14.dp, vertical = 6.dp), fontFamily = GameFont, fontSize = 18.sp)
            }
        } else if (item.canDelete && g.status == GradeStatus.APPROVED) {
            IconButton(onClick = { actions.onUnapprove(g) }) {
                Icon(Icons.AutoMirrored.Outlined.Undo, contentDescription = "Скасувати підтвердження", tint = c.muted)
            }
        }
        if (item.canDelete) {
            IconButton(onClick = { actions.onDelete(g) }) {
                Icon(Icons.Outlined.Delete, contentDescription = "Видалити", tint = c.muted, modifier = Modifier.height(22.dp))
            }
        }
    }
}
