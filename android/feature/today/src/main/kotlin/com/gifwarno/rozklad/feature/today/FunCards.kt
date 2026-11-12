package com.gifwarno.rozklad.feature.today

import android.content.Context
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
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.edit
import com.gifwarno.rozklad.core.designsystem.component.GameButton
import com.gifwarno.rozklad.core.designsystem.component.GameSurface
import com.gifwarno.rozklad.core.designsystem.theme.RozkladTheme
import java.time.LocalDate
import kotlin.random.Random
import kotlinx.coroutines.delay

/** Вікторина на перерві: 3 питання, правильна відповідь — зірочка й салют. */
@Composable
internal fun QuizCard(onCorrect: () -> Unit, modifier: Modifier = Modifier) {
    val c = RozkladTheme.colors
    var seed by rememberSaveable { mutableLongStateOf(Random.nextLong()) }
    val round = remember(seed) { quizRound(seed) }
    var index by rememberSaveable(seed) { mutableIntStateOf(0) }
    var right by rememberSaveable(seed) { mutableIntStateOf(0) }
    var picked by rememberSaveable(seed) { mutableStateOf<String?>(null) }
    LaunchedEffect(picked) {
        if (picked != null) {
            delay(NEXT_DELAY_MS)
            index++
            picked = null
        }
    }
    FunCard("🧠 Вікторина на перерві", modifier) {
        if (index >= round.size) {
            val verdict = when (right) {
                QUIZ_SIZE -> "🏆 Ідеально!"
                0 -> "💪 Наступного разу вийде!"
                else -> "👍 Непогано!"
            }
            Text("$verdict $right/$QUIZ_SIZE — +$right ⭐", style = MaterialTheme.typography.titleMedium)
            GameButton("Ще раз", { seed = Random.nextLong() }, leading = "🔄")
            return@FunCard
        }
        val q = round[index]
        Text("Питання ${index + 1} з ${round.size}", style = MaterialTheme.typography.labelMedium, color = c.muted)
        Text(q.question, style = MaterialTheme.typography.titleMedium)
        q.options.chunked(2).forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                row.forEach { o ->
                    val color = when {
                        picked != null && o == q.right -> c.positive
                        picked == o -> c.negative
                        else -> c.soft
                    }
                    GameButton(
                        o,
                        onClick = {
                            if (picked == null) {
                                picked = o
                                if (o == q.right) {
                                    right++
                                    onCorrect()
                                }
                            }
                        },
                        modifier = Modifier.weight(1f),
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

/** Цікавий факт: «Ще факт» гортає по колу. */
@Composable
internal fun FactCard(modifier: Modifier = Modifier) {
    var i by rememberSaveable { mutableIntStateOf(Random.nextInt(FACTS.size)) }
    FunCard("🦉 Цікавий факт", modifier) {
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

/** «До вихідних» і зірочки за сьогодні (зберігаються на цьому телефоні, як у вебі — у браузері). */
@Composable
internal fun WeekendStarsCard(weekend: String, stars: Int, onStar: () -> Unit, modifier: Modifier = Modifier) {
    FunCard("🌈 До вихідних", modifier) {
        Text(weekend, style = MaterialTheme.typography.bodyLarge)
        Text("⭐ Мої зірочки за сьогодні", style = MaterialTheme.typography.titleSmall, modifier = Modifier.padding(top = 6.dp))
        Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
            if (stars > 0) {
                Text(
                    "⭐".repeat(stars),
                    fontSize = 22.sp,
                    modifier = Modifier.weight(1f).semantics { contentDescription = "Зірочок: $stars" },
                )
            } else {
                Text(
                    "Ще жодної — постав собі за добру справу",
                    style = MaterialTheme.typography.bodyMedium,
                    color = com.gifwarno.rozklad.core.designsystem.theme.RozkladTheme.colors.muted,
                    modifier = Modifier.weight(1f).padding(end = 8.dp),
                )
            }
            GameButton("зірка", onStar, enabled = stars < MAX_STARS, leading = "＋")
        }
    }
}

@Composable
private fun FunCard(title: String, modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    GameSurface(modifier.fillMaxWidth(), shape = RoundedCornerShape(20.dp)) {
        Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium, textAlign = TextAlign.Start)
            content()
        }
    }
}

/** Зірочки дня на цьому пристрої: нова дата — знову з нуля. */
@Composable
internal fun rememberStars(): Pair<Int, () -> Unit> {
    val context = LocalContext.current
    val prefs = remember { context.getSharedPreferences(STARS_PREFS, Context.MODE_PRIVATE) }
    val today = LocalDate.now().toString()
    var stars by remember(today) { mutableIntStateOf(if (prefs.getString(KEY_DAY, null) == today) prefs.getInt(KEY_COUNT, 0) else 0) }
    val add = {
        stars = (stars + 1).coerceAtMost(MAX_STARS)
        prefs.edit {
            putString(KEY_DAY, today)
            putInt(KEY_COUNT, stars)
        }
    }
    return stars to add
}

private const val NEXT_DELAY_MS = 900L
private const val STARS_PREFS = "fun"
private const val KEY_DAY = "stars_day"
private const val KEY_COUNT = "stars"
