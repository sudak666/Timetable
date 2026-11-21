package com.gifwarno.rozklad.core.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.gifwarno.rozklad.core.designsystem.component.CardTitle
import com.gifwarno.rozklad.core.designsystem.component.FitText
import com.gifwarno.rozklad.core.designsystem.component.GameSurface
import com.gifwarno.rozklad.core.designsystem.component.outlinedBackground
import com.gifwarno.rozklad.core.designsystem.component.sticker
import com.gifwarno.rozklad.core.designsystem.component.stickerSlot
import com.gifwarno.rozklad.core.designsystem.component.stickerTilt
import com.gifwarno.rozklad.core.designsystem.theme.RozkladTheme
import com.gifwarno.rozklad.core.domain.Badge
import com.gifwarno.rozklad.core.domain.RoadStep
import com.gifwarno.rozklad.core.domain.Trophies
import com.gifwarno.rozklad.core.domain.TrophyReward

/** Шлях нагород: плитки рівнів з аватарами й кольорами, що відкриваються. */
@Composable
fun TrophyRoad(steps: List<RoadStep>, modifier: Modifier = Modifier) {
    // Одразу показуємо, де гравець зараз: останній відкритий рівень і наступний, а не рівні 2–5.
    val next = steps.indexOfFirst { it.next }.takeIf { it >= 0 } ?: steps.indexOfLast { it.got }.coerceAtLeast(0)
    val list = rememberLazyListState(initialFirstVisibleItemIndex = (next - 1).coerceAtLeast(0))
    LazyRow(
        state = list,
        modifier = modifier.fillMaxWidth(),
        contentPadding = PaddingValues(0.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        items(steps, key = { it.level }) { RoadTile(it) }
    }
}

@Composable
private fun RoadTile(step: RoadStep) {
    val c = RozkladTheme.colors
    val label = when {
        step.got -> "Рівень ${step.level}, відкрито"
        else -> "Рівень ${step.level}, відкриється на ${step.xpNeeded} XP"
    }
    // Відкрите — наліпка, ще закрите — пунктирне місце під неї; наступний рівень — картка з чорнильною обвідкою.
    if (!step.next) {
        val shape = RoundedCornerShape(16.dp)
        val tile = Modifier.width(96.dp).semantics(mergeDescendants = true) { contentDescription = label }
        Box(
            if (step.got) {
                tile.sticker(stickerTilt(step.level), shape).background(lerp(c.gold, Color.White, STICKER_PALE), shape)
            } else {
                tile.stickerSlot(shape, c.muted.copy(alpha = SLOT_ALPHA))
            },
        ) { RoadTileContent(step) }
        return
    }
    GameSurface(
        modifier = Modifier.width(96.dp).semantics(mergeDescendants = true) { contentDescription = label },
        outline = 2.5.dp,
    ) { RoadTileContent(step) }
}

@Composable
private fun RoadTileContent(step: RoadStep) {
    val c = RozkladTheme.colors
    val onSticker = step.got
    // Порожнє місце лежить просто на сторінці (не на картці) — колір тексту задаємо явно.
    val inSlot = !step.got && !step.next
    Column(Modifier.fillMaxWidth().padding(vertical = 10.dp, horizontal = 6.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Box(Modifier.size(44.dp).alpha(if (step.got || step.next) 1f else LOCKED_ALPHA), contentAlignment = Alignment.Center) {
            when (val r = step.reward) {
                is TrophyReward.Avatar -> Text(r.emoji, fontSize = 34.sp)
                is TrophyReward.Accent -> Box(Modifier.size(34.dp).clip(RoundedCornerShape(10.dp)).background(Color(r.color)))
            }
        }
        FitText(
            "Рівень ${step.level}",
            Modifier.padding(top = 4.dp),
            style = MaterialTheme.typography.labelLarge,
            color = when {
                onSticker -> STICKER_INK
                inSlot -> c.muted
                else -> Color.Unspecified
            },
        )
        Text(
            when {
                step.got -> "✓ відкрито"
                step.next -> "далі · ${step.xpNeeded} XP"
                else -> "🔒 ${step.xpNeeded} XP"
            },
            style = MaterialTheme.typography.labelSmall,
            color = when {
                onSticker -> STICKER_INK
                step.got -> c.positive
                else -> c.muted
            },
            textAlign = TextAlign.Center,
        )
    }
}

/** Досягнення: отримані — золоті, решта — сірі. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun BadgesCard(badges: List<Badge>, modifier: Modifier = Modifier) {
    val c = RozkladTheme.colors
    GameSurface(modifier.fillMaxWidth()) {
        Column(Modifier.fillMaxWidth().padding(16.dp)) {
            CardTitle("Досягнення · ${badges.count { it.got }}/${badges.size}")
            FlowRow(
                Modifier.padding(top = 10.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                badges.forEachIndexed { i, b ->
                    val shape = RoundedCornerShape(50)
                    val text = "${b.emoji} ${b.title}"
                    val said = b.title + if (b.got) " — отримано" else " — ще ні"
                    // Отримане — жовта наліпка з білим вирізом, решта — пунктирне місце під неї.
                    Text(
                        text,
                        modifier = (
                            if (b.got) {
                                Modifier.sticker(stickerTilt(i), shape).background(c.gold, shape)
                            } else {
                                Modifier.stickerSlot(shape, c.muted.copy(alpha = SLOT_ALPHA))
                            }
                            )
                            .padding(horizontal = 12.dp, vertical = 6.dp)
                            .semantics { contentDescription = said },
                        style = MaterialTheme.typography.labelLarge,
                        color = if (b.got) STICKER_INK else c.muted,
                    )
                }
            }
        }
    }
}

/** Вибір аватара й кольору: недоступні показують рівень, з якого відкриються. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun LookPicker(
    level: Int,
    avatar: String,
    accent: String,
    onAvatar: (String) -> Unit,
    onAccent: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val c = RozkladTheme.colors
    Column(modifier, verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(
            if (level >= Trophies.PARENT_LEVEL) "Аватар" else "Аватар · рівень $level",
            style = MaterialTheme.typography.titleMedium,
        )
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Trophies.AVATARS.forEach { (a, l) ->
                val open = l <= level
                val on = a == avatar
                GameSurface(
                    modifier = Modifier.semantics {
                        selected = on
                        contentDescription = if (open) "Аватар $a" else "Аватар відкриється на рівні $l"
                    },
                    shape = RoundedCornerShape(14.dp),
                    color = if (on) lerp(c.card, c.accent, TINT) else c.soft,
                    depth = if (open) 3.dp else 0.dp,
                    // Обраний аватар — обвідка чорнилом.
                    outline = if (on) SELECTED_RING else 0.dp,
                    onClick = if (open) ({ onAvatar(a) }) else null,
                ) {
                    Box(Modifier.size(56.dp), contentAlignment = Alignment.Center) {
                        Text(a, fontSize = 30.sp, modifier = Modifier.alpha(if (open) 1f else LOCKED_ALPHA))
                        if (!open) {
                            Text(
                                "🔒$l",
                                Modifier.align(Alignment.BottomEnd).padding(2.dp),
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Black,
                            )
                        }
                    }
                }
            }
        }
        Text("Колір застосунку", style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(top = 4.dp))
        FlowRow(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Trophies.THEMES.forEach { (hex, l) ->
                val open = l <= level
                val on = hex.equals(accent, ignoreCase = true)
                val color = Color(checkNotNull(Trophies.parseColor(hex)))
                GameSurface(
                    modifier = Modifier.semantics {
                        selected = on
                        contentDescription = if (open) "Колір $hex" else "Колір відкриється на рівні $l"
                    },
                    shape = CircleShape,
                    color = color,
                    depth = if (open) 3.dp else 0.dp,
                    // Обраний колір — жовте кільце маркера: видно на будь-якому кольорі, і світлому, і темному.
                    outline = if (on) SELECTED_RING else 0.dp,
                    outlineColor = c.gold,
                    onClick = if (open) ({ onAccent(hex) }) else null,
                    onClickLabel = "Вибрати колір",
                ) {
                    Box(Modifier.size(44.dp).alpha(if (open) 1f else LOCKED_ALPHA), contentAlignment = Alignment.Center) {
                        when {
                            on -> Text("✓", color = Color.White, fontWeight = FontWeight.Black, fontSize = 20.sp)
                            !open -> Text("🔒$l", color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.Black)
                        }
                    }
                }
            }
        }
    }
}

private const val LOCKED_ALPHA = 0.45f

/** Непрозорий відтінок акценту: напівпрозорий фон пропускав би чорну «тверду» тінь. */
private const val TINT = 0.28f

/** Текст на жовтій наліпці — графіт (і вдень, і на дошці). */
private val STICKER_INK = Color(0xFF23262F)

/** Наліпка рівня — блідо-жовта, щоб емодзі й підписи на ній читались. */
private const val STICKER_PALE = 0.55f

/** Пунктир порожнього місця — ледь помітний. */
private const val SLOT_ALPHA = 0.6f

/** Обвідка обраного аватара чи кольору. */
private val SELECTED_RING = 3.dp
