package com.gifwarno.rozklad.feature.today

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.gifwarno.rozklad.core.designsystem.component.CardTitle
import com.gifwarno.rozklad.core.designsystem.component.GameButton
import com.gifwarno.rozklad.core.designsystem.component.GameSurface
import com.gifwarno.rozklad.core.designsystem.theme.RozkladTheme
import com.gifwarno.rozklad.core.domain.Rewards
import com.gifwarno.rozklad.core.model.QuizTodayEntry
import com.gifwarno.rozklad.core.ui.Format
import kotlin.random.Random

/**
 * Слот «Вікторина» на головній: дитині — гра, батькам — як дитина сьогодні відповідала ([ParentQuizViewModel]);
 * пограти самим батьки можуть кнопкою «Пограти самому».
 */
@Composable
internal fun QuizSlot(onCorrect: () -> Unit, modifier: Modifier = Modifier, viewModel: ParentQuizViewModel = hiltViewModel()) {
    val parent by viewModel.state.collectAsStateWithLifecycle()
    var selfPlay by rememberSaveable { mutableStateOf(false) }
    when {
        parent == ParentQuizUiState.NotParent -> QuizRoute(onCorrect, modifier)
        selfPlay -> QuizRoute(onCorrect, modifier, onBack = { selfPlay = false })
        else -> ParentQuizCard(parent, onRefresh = viewModel::refresh, onPlay = { selfPlay = true }, modifier = modifier)
    }
}

/** Вікторина з сервера: стан — у [QuizViewModel] (питання й правильні відповіді знає лише сервер). */
@Composable
internal fun QuizRoute(
    onCorrect: () -> Unit,
    modifier: Modifier = Modifier,
    onBack: (() -> Unit)? = null,
    viewModel: QuizViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val note by viewModel.priceNote.collectAsStateWithLifecycle()
    // Салют — коли сервер підтвердив правильну відповідь.
    val q = state as? QuizUiState.Question
    LaunchedEffect(q?.right, q?.number) { if (q?.right != null && q.picked == q.right) onCorrect() }
    QuizCard(state, note?.text, viewModel::answer, viewModel::start, modifier, onBack)
}

/** Батькам: сьогоднішні відповіді дитини — підсумок і кожне питання (✅/❌, що обрала, правильна, гроші). */
@Composable
internal fun ParentQuizCard(state: ParentQuizUiState, onRefresh: () -> Unit, onPlay: () -> Unit, modifier: Modifier = Modifier) {
    val c = RozkladTheme.colors
    FunCard("Вікторина", modifier) {
        val live = Modifier.semantics { liveRegion = LiveRegionMode.Polite }
        when (state) {
            ParentQuizUiState.NotParent, ParentQuizUiState.Loading ->
                Text("Завантажую…", style = MaterialTheme.typography.bodyLarge, color = c.muted, modifier = live)

            is ParentQuizUiState.Error ->
                Text(
                    "📡 ${state.childName}: не вдалося завантажити відповіді — потрібен інтернет",
                    style = MaterialTheme.typography.bodyLarge,
                    modifier = live,
                )

            is ParentQuizUiState.Ready -> if (state.entries.isEmpty()) {
                Text(
                    "🧠 ${state.childName}: сьогодні відповідей у вікторині ще немає. Після кожних 3 відповідей надійде сповіщення.",
                    style = MaterialTheme.typography.bodyLarge,
                    modifier = live,
                )
            } else {
                Text(state.summary, style = MaterialTheme.typography.titleMedium, modifier = live)
                state.entries.forEach { e -> QuizEntryRow(e) }
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            GameButton(
                "Пограти самому",
                onPlay,
                modifier = Modifier.weight(1f),
                color = c.soft,
                contentColor = MaterialTheme.colorScheme.onSurface,
                leading = "🎮",
            )
            GameButton(
                if (state is ParentQuizUiState.Error) "Ще раз" else "Оновити",
                onRefresh,
                modifier = Modifier.weight(1f),
                leading = "🔄",
                enabled = state != ParentQuizUiState.Loading,
            )
        }
    }
}

@Composable
private fun QuizEntryRow(e: QuizTodayEntry) {
    val c = RozkladTheme.colors
    val details = buildString {
        append(e.answer ?: "—")
        if (!e.correct) append(" · правильно: ${e.right}")
        if (e.paid > 0) append(" · ${Format.money(e.paid)}")
    }
    Column(
        Modifier
            .fillMaxWidth()
            .background(c.soft, RoundedCornerShape(12.dp))
            .padding(horizontal = 12.dp, vertical = 8.dp)
            .semantics(mergeDescendants = true) {
                contentDescription = (if (e.correct) "Правильно. " else "Неправильно. ") + e.question + ". Відповідь: " + details
            },
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        Text((if (e.correct) "✅ " else "❌ ") + e.question, style = MaterialTheme.typography.titleSmall)
        Text(details, style = MaterialTheme.typography.bodyMedium, color = if (e.correct) c.positive else c.negative)
    }
}

/** Вікторина на перерві: 3 питання, правильна відповідь — салют і гроші дитині (до 3 на день). */
@Composable
internal fun QuizCard(
    state: QuizUiState,
    note: String?,
    onAnswer: (String) -> Unit,
    onRestart: () -> Unit,
    modifier: Modifier = Modifier,
    onBack: (() -> Unit)? = null,
) {
    val c = RozkladTheme.colors
    FunCard("Вікторина", modifier) {
        when (state) {
            QuizUiState.Loading -> Text("Завантажую питання…", style = MaterialTheme.typography.bodyLarge, color = c.muted)

            QuizUiState.Error -> {
                Text("📡 Вікторина працює з інтернетом — не вдалося завантажити питання", style = MaterialTheme.typography.bodyLarge)
                GameButton("Спробувати ще раз", onRestart, leading = "🔄")
            }

            is QuizUiState.Done -> {
                val verdict = when (state.correct) {
                    state.total -> "🏆 Ідеально!"
                    0 -> "💪 Наступного разу вийде!"
                    else -> "👍 Непогано!"
                }
                val money = if (state.earned > 0) " — +${state.earned}\u00A0₴ у гаманець 💰" else ""
                Text("$verdict ${state.correct}/${state.total}$money", style = MaterialTheme.typography.titleMedium)
                note?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = c.muted) }
                GameButton("Ще раз", onRestart, leading = "🔄")
            }

            is QuizUiState.Question -> {
                note?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = c.muted) }
                Text("Питання ${state.number} з ${state.total}", style = MaterialTheme.typography.labelMedium, color = c.muted)
                Text(state.question.question, style = MaterialTheme.typography.titleMedium)
                if (state.sendFailed) {
                    Text(
                        "Немає зв’язку — відповідь не зараховано. Спробуй ще раз 🙏",
                        style = MaterialTheme.typography.bodySmall,
                        color = c.negative,
                        modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
                    )
                }
                state.question.options.chunked(2).forEach { row ->
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        row.forEach { o ->
                            val color = when {
                                state.right != null && o == state.right -> c.positive
                                state.right != null && o == state.picked -> c.negative
                                else -> c.soft
                            }
                            GameButton(
                                o,
                                onClick = { onAnswer(o) },
                                modifier = Modifier.weight(1f),
                                enabled = state.picked == null,
                                color = color,
                                contentColor = when (color) {
                                    c.positive -> c.onPositive
                                    c.negative -> c.onNegative
                                    else -> MaterialTheme.colorScheme.onSurface
                                },
                            )
                        }
                    }
                }
            }
        }
        onBack?.let {
            GameButton("Відповіді дитини", it, color = c.soft, contentColor = MaterialTheme.colorScheme.onSurface, leading = "👀")
        }
    }
}

/** Цікавий факт: «Ще факт» гортає по колу. */
@Composable
internal fun FactCard(modifier: Modifier = Modifier) {
    var i by rememberSaveable { mutableIntStateOf(Random.nextInt(FACTS.size)) }
    FunCard("Цікавий факт", modifier) {
        Text(
            FACTS[i % FACTS.size],
            style = MaterialTheme.typography.bodyLarge,
            modifier = Modifier.semantics {
                liveRegion =
                    LiveRegionMode.Polite
            },
        )
        GameButton("Ще факт", { i++ }, color = RozkladTheme.colors.soft, contentColor = MaterialTheme.colorScheme.onSurface, leading = "✨")
    }
}

/** «До вихідних» і зірки за поведінку: ставлять лише батьки, кожна — гроші в гаманці дитини. */
@Composable
internal fun WeekendStarsCard(
    weekend: String,
    stars: StarsStrip,
    onGive: () -> Unit,
    onTake: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val c = RozkladTheme.colors
    FunCard("До вихідних", modifier) {
        Text(weekend, style = MaterialTheme.typography.bodyLarge)
        Text("Зірки за поведінку сьогодні", style = MaterialTheme.typography.titleSmall, modifier = Modifier.padding(top = 6.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (stars.count > 0) {
                Text(
                    "⭐".repeat(stars.count) + "  " + Format.money(stars.sum),
                    fontSize = 20.sp,
                    modifier = Modifier.weight(1f).semantics { contentDescription = "Зірок: ${stars.count}, ${Format.money(stars.sum)}" },
                )
            } else {
                Text(
                    if (stars.canGive) {
                        "Постав ${stars.childName.ifBlank { "дитині" }} зірку за добру справу — +${stars.price}\u00A0₴"
                    } else {
                        "Зірки ставлять батьки за добру поведінку — кожна +${stars.price}\u00A0₴"
                    },
                    style = MaterialTheme.typography.bodyMedium,
                    color = c.muted,
                    modifier = Modifier.weight(1f).padding(end = 8.dp),
                )
            }
        }
        if (stars.canGive) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                stars.lastId?.let { id ->
                    GameButton(
                        "Прибрати",
                        { onTake(id) },
                        color = c.soft,
                        contentColor = MaterialTheme.colorScheme.onSurface,
                        leading = "−",
                    )
                }
                GameButton(
                    "+${stars.price}\u00A0₴",
                    onGive,
                    modifier = Modifier.weight(1f),
                    enabled = !stars.full && stars.price > 0,
                    leading = "⭐",
                )
            }
            if (stars.full) {
                Text(
                    "На сьогодні вже ${Rewards.STARS_PER_DAY} зірок — максимум",
                    style = MaterialTheme.typography.bodySmall,
                    color = c.muted,
                )
            }
        }
    }
}

@Composable
private fun FunCard(title: String, modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    GameSurface(modifier.fillMaxWidth(), shape = RoundedCornerShape(20.dp)) {
        Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            CardTitle(title)
            content()
        }
    }
}
