package com.gifwarno.rozklad.feature.today

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.gifwarno.rozklad.core.designsystem.component.GameSurface
import com.gifwarno.rozklad.core.designsystem.component.NumberTile
import com.gifwarno.rozklad.core.designsystem.theme.RozkladTheme
import com.gifwarno.rozklad.core.domain.Timetable

/**
 * Розклад на тиждень: дні — «пігулки», уроки обраного дня, і лічильник предметів.
 * Натиснув предмет — його уроки підсвічуються, а на днях, де він є, з'являється крапка (як у вебі).
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun WeekCard(week: WeekSchedule, modifier: Modifier = Modifier) {
    val c = RozkladTheme.colors
    val todayIndex = week.days.firstOrNull { it.isToday }?.index ?: 0
    var day by rememberSaveable { mutableIntStateOf(todayIndex) }
    var highlight by rememberSaveable { mutableStateOf<String?>(null) }
    val shown = week.days.getOrNull(day) ?: week.days.firstOrNull() ?: return
    GameSurface(modifier.fillMaxWidth()) {
        Column(Modifier.fillMaxWidth().padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                items(week.days, key = { it.index }) { d ->
                    val on = d.index == day
                    val has = highlight != null && d.lessons.any { it.subject == highlight }
                    GameSurface(
                        shape = RoundedCornerShape(50),
                        color = if (on) c.accent else c.soft,
                        contentColor = if (on) Color.White else MaterialTheme.colorScheme.onSurface,
                        depth = if (on) 3.dp else 0.dp,
                        borderWidth = if (on) 2.dp else 0.dp,
                        onClick = { day = d.index },
                        modifier = Modifier.semantics { selected = on },
                    ) {
                        Row(Modifier.padding(horizontal = 12.dp, vertical = 7.dp), verticalAlignment = Alignment.CenterVertically) {
                            Text("${d.short} · ${d.lessons.size}", style = MaterialTheme.typography.labelLarge)
                            if (d.isToday) Text(" ★", style = MaterialTheme.typography.labelLarge)
                            if (has) Box(Modifier.padding(start = 6.dp).size(8.dp).clip(CircleShape).background(c.gold))
                        }
                    }
                }
            }
            Text(shown.name, style = MaterialTheme.typography.titleMedium)
            if (shown.lessons.isEmpty()) Text("Вихідний 🎮", color = c.muted)
            shown.lessons.forEach { l ->
                val info = Timetable.subject(l.subject)
                val dim = highlight != null && l.subject != highlight
                Row(
                    Modifier
                        .fillMaxWidth()
                        .alpha(if (dim) DIM_ALPHA else 1f)
                        .clip(RoundedCornerShape(10.dp))
                        .background(if (highlight == l.subject) c.glow else Color.Transparent)
                        .padding(horizontal = 6.dp, vertical = 5.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    NumberTile(l.number, Color(info.color), size = 26.dp)
                    Text(
                        l.time,
                        Modifier.padding(start = 10.dp).width(92.dp),
                        style = MaterialTheme.typography.bodySmall.copy(fontFeatureSettings = "tnum"),
                        color = c.muted,
                    )
                    Text(
                        "${info.emoji} ${l.subject}" + if (l.optional) " · факультатив" else "",
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.ExtraBold,
                    )
                }
            }
            HorizontalDivider(color = c.line)
            Text("Уроків на тиждень · натисни, щоб підсвітити", style = MaterialTheme.typography.labelMedium, color = c.muted)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                week.counts.forEach { sc ->
                    val info = Timetable.subject(sc.subject)
                    val on = highlight == sc.subject
                    GameSurface(
                        shape = RoundedCornerShape(50),
                        color = Color(info.color),
                        contentColor = Color.White,
                        depth = if (on) 3.dp else 0.dp,
                        borderWidth = if (on) 3.dp else 0.dp,
                        onClick = { highlight = if (on) null else sc.subject },
                        modifier = Modifier.alpha(if (highlight == null || on) 1f else DIM_ALPHA).semantics { selected = on },
                    ) {
                        Text(
                            "${sc.subject} ×${sc.count}",
                            Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
                            style = MaterialTheme.typography.labelMedium,
                        )
                    }
                }
            }
        }
    }
}

private const val DIM_ALPHA = 0.35f
