package com.gifwarno.rozklad.core.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CloudOff
import androidx.compose.material.icons.outlined.ErrorOutline
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.gifwarno.rozklad.core.designsystem.component.EmojiBadge
import com.gifwarno.rozklad.core.designsystem.component.GameSurface
import com.gifwarno.rozklad.core.designsystem.component.ValueBadge
import com.gifwarno.rozklad.core.designsystem.theme.RozkladTheme
import com.gifwarno.rozklad.core.domain.Rewards
import com.gifwarno.rozklad.core.domain.Timetable
import com.gifwarno.rozklad.core.model.Grade
import com.gifwarno.rozklad.core.model.Member
import com.gifwarno.rozklad.core.model.SyncStatus

fun subjectColor(subject: String): Color = Color(Timetable.subject(subject).color)

@Composable
fun SubjectBadge(subject: String, modifier: Modifier = Modifier) {
    val info = Timetable.subject(subject)
    EmojiBadge(emoji = info.emoji, color = Color(info.color), modifier = modifier, contentDescription = subject)
}

@Composable
fun gradeColor(value: Int): Color {
    val c = RozkladTheme.colors
    return when {
        value == Grade.PASS -> c.gradePass
        value >= 10 -> c.gradeExcellent
        value >= 7 -> c.gradeGood
        value >= 4 -> c.gradeFair
        else -> c.gradeLow
    }
}

@Composable
fun GradeBadge(value: Int, modifier: Modifier = Modifier) {
    ValueBadge(label = Rewards.gradeLabel(value), color = gradeColor(value), modifier = modifier)
}

@Composable
fun moneyColor(v: Int): Color = when {
    v > 0 -> RozkladTheme.colors.positive
    v < 0 -> RozkladTheme.colors.negative
    else -> MaterialTheme.colorScheme.onSurfaceVariant
}

/** Чесний індикатор: скільки змін чекає мережі, і що сервер відхилив. */
@Composable
fun SyncBanner(sync: SyncStatus, onDismissError: () -> Unit, modifier: Modifier = Modifier) {
    val error = sync.lastError
    when {
        error != null -> Surface(modifier.fillMaxWidth(), color = MaterialTheme.colorScheme.errorContainer) {
            Row(Modifier.padding(horizontal = 16.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Outlined.ErrorOutline, contentDescription = null)
                Text(error, Modifier.weight(1f).padding(horizontal = 8.dp), style = MaterialTheme.typography.bodySmall)
                TextButton(onClick = onDismissError) { Text("OK") }
            }
        }

        sync.pendingChanges > 0 -> Surface(modifier.fillMaxWidth(), color = MaterialTheme.colorScheme.secondaryContainer) {
            Row(Modifier.padding(horizontal = 16.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Outlined.CloudOff, contentDescription = null)
                Text(
                    "Змін чекає відправки: ${sync.pendingChanges}. Надішлемо, щойно з’явиться інтернет.",
                    Modifier.padding(start = 8.dp),
                    style = MaterialTheme.typography.bodySmall,
                )
            }
        }
    }
}

/** Перемикач дитини для батьків. */
@Composable
fun ChildSelector(children: List<Member>, selectedId: String?, onSelect: (String) -> Unit, modifier: Modifier = Modifier) {
    if (children.size < 2) return
    LazyRow(modifier, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        items(children, key = { it.userId }) { kid ->
            val selected = kid.userId == selectedId
            GameSurface(
                shape = RoundedCornerShape(50),
                color = if (selected) RozkladTheme.colors.accent else RozkladTheme.colors.card,
                contentColor = if (selected) Color.White else MaterialTheme.colorScheme.onSurface,
                depth = 3.dp,
                onClick = { onSelect(kid.userId) },
                modifier = Modifier.semantics { this.selected = selected },
            ) {
                Text(
                    "${kid.avatar.ifBlank { "🙂" }} ${kid.name}",
                    Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
                    style = MaterialTheme.typography.labelLarge,
                )
            }
        }
    }
}
