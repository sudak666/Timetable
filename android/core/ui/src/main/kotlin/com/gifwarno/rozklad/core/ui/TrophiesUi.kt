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
import com.gifwarno.rozklad.core.designsystem.component.GameSurface
import com.gifwarno.rozklad.core.designsystem.component.outlineWidth
import com.gifwarno.rozklad.core.designsystem.component.outlinedBackground
import com.gifwarno.rozklad.core.designsystem.theme.RozkladTheme
import com.gifwarno.rozklad.core.domain.Badge
import com.gifwarno.rozklad.core.domain.RoadStep
import com.gifwarno.rozklad.core.domain.Trophies
import com.gifwarno.rozklad.core.domain.TrophyReward

/** Шлях нагород: плитки рівнів з аватарами й кольорами, що відкриваються. */
@Composable
fun TrophyRoad(steps: List<RoadStep>, modifier: Modifier = Modifier) {
    LazyRow(
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
    GameSurface(
        modifier = Modifier.width(96.dp).semantics(mergeDescendants = true) { contentDescription = label },
        color = if (step.next) {
            c.card
        } else if (step.got) {
            c.card
        } else {
            c.soft
        },
        brush = if (step.got) Brush.verticalGradient(listOf(lerp(c.card, c.accent, TINT), c.card)) else null,
        borderWidth = if (step.next) 3.dp else 2.dp,
    ) {
        Column(Modifier.fillMaxWidth().padding(vertical = 10.dp, horizontal = 6.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Box(Modifier.size(44.dp).alpha(if (step.got || step.next) 1f else LOCKED_ALPHA), contentAlignment = Alignment.Center) {
                when (val r = step.reward) {
                    is TrophyReward.Avatar -> Text(r.emoji, fontSize = 34.sp)
                    is TrophyReward.Accent -> Box(Modifier.size(34.dp).clip(RoundedCornerShape(10.dp)).background(Color(r.color)))
                }
            }
            Text("Рівень ${step.level}", style = MaterialTheme.typography.labelLarge, modifier = Modifier.padding(top = 4.dp))
            Text(
                when {
                    step.got -> "✓ відкрито"
                    step.next -> "далі · ${step.xpNeeded} XP"
                    else -> "🔒 ${step.xpNeeded} XP"
                },
                style = MaterialTheme.typography.labelSmall,
                color = if (step.got) c.positive else c.muted,
                textAlign = TextAlign.Center,
            )
        }
    }
}

/** Досягнення: отримані — золоті, решта — сірі. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun BadgesCard(badges: List<Badge>, modifier: Modifier = Modifier) {
    val c = RozkladTheme.colors
    GameSurface(modifier.fillMaxWidth()) {
        Column(Modifier.fillMaxWidth().padding(16.dp)) {
            Text(
                "🏅 Досягнення · ${badges.count { it.got }}/${badges.size}",
                style = MaterialTheme.typography.titleMedium,
            )
            FlowRow(
                Modifier.padding(top = 10.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                badges.forEach { b ->
                    val shape = RoundedCornerShape(50)
                    Text(
                        "${b.emoji} ${b.title}",
                        modifier = Modifier
                            .outlinedBackground(
                                if (b.got) Brush.horizontalGradient(listOf(c.gold, Color(0xFFFAB1A0))) else SolidColor(c.soft),
                                shape,
                                if (b.got) outlineWidth(2.dp) else 0.dp,
                                c.ink,
                            )
                            .padding(horizontal = 12.dp, vertical = 6.dp)
                            .alpha(if (b.got) 1f else LOCKED_ALPHA)
                            .semantics { contentDescription = b.title + if (b.got) " — отримано" else " — ще ні" },
                        style = MaterialTheme.typography.labelLarge,
                        color = if (b.got) Color(0xFF1B1C33) else c.muted,
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
                    borderWidth = if (on) {
                        3.dp
                    } else if (open) {
                        2.dp
                    } else {
                        0.dp
                    },
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
                    borderWidth = if (on) 4.dp else 2.dp,
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
