package com.gifwarno.rozklad.core.designsystem.component

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.gifwarno.rozklad.core.designsystem.theme.GameFont
import com.gifwarno.rozklad.core.designsystem.theme.GameTitleStyle
import com.gifwarno.rozklad.core.designsystem.theme.RozkladTheme

/** Заголовок розділу прямо на тлі «арени»: білі літери з обведенням. */
@Composable
fun ArenaTitle(text: String, modifier: Modifier = Modifier) {
    StrokeText(text, GameTitleStyle.copy(fontSize = 20.sp, lineHeight = 26.sp), modifier.padding(top = 6.dp), strokeWidth = 4.dp)
}

/** Верхня панель на тлі «арени»: кругла кнопка «назад», заголовок з обведенням, дії праворуч. */
@Composable
fun GameTopBar(title: String, onBack: () -> Unit, modifier: Modifier = Modifier, actions: @Composable RowScope.() -> Unit = {}) {
    Row(
        modifier.fillMaxWidth().padding(start = 12.dp, end = 12.dp, top = 8.dp, bottom = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        GameSurface(shape = CircleShape, depth = 3.dp, onClick = onBack, onClickLabel = "Назад") {
            Icon(
                Icons.AutoMirrored.Filled.ArrowBack,
                contentDescription = "Назад",
                modifier = Modifier.padding(10.dp).size(22.dp),
            )
        }
        StrokeText(
            title,
            GameTitleStyle.copy(fontSize = 20.sp, lineHeight = 24.sp),
            Modifier.weight(1f),
            strokeWidth = 4.dp,
            maxLines = 2,
        )
        actions()
    }
}

/** Велика «плаваюча» кнопка дії (замість стандартної FAB). */
@Composable
fun GameFab(text: String, onClick: () -> Unit, modifier: Modifier = Modifier) {
    GameButton(text = text, onClick = onClick, modifier = modifier, leading = "＋")
}

/** Кругла галочка (як .cb у вебі): відмічена — зелена, «підстрибує». */
@Composable
fun RoundCheck(checked: Boolean, onToggle: () -> Unit, label: String, modifier: Modifier = Modifier, size: Dp = 32.dp) {
    val c = RozkladTheme.colors
    val haptics = LocalHapticFeedback.current
    val scale by animateFloatAsState(
        if (checked) 1f else 0.92f,
        spring(dampingRatio = 0.35f, stiffness = 700f),
        label = "check",
    )
    Box(
        modifier
            .size(48.dp)
            .toggleable(value = checked, role = Role.Checkbox) {
                haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                onToggle()
            }
            .semantics { contentDescription = label },
        contentAlignment = Alignment.Center,
    ) {
        Box(
            Modifier
                .size(size)
                .scale(scale)
                .clip(CircleShape)
                .background(if (checked) c.positive else Color.Transparent)
                .border(2.5.dp, if (checked) c.positive else c.muted, CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            if (checked) Text("✓", color = Color.White, fontWeight = FontWeight.Black, fontSize = 18.sp)
        }
    }
}

/** Перемикач з кількох варіантів — «пігулки», обрана залита акцентом. */
@Composable
fun <T> GameSegmented(options: List<Pair<T, String>>, selected: T, onSelect: (T) -> Unit, modifier: Modifier = Modifier) {
    val c = RozkladTheme.colors
    Row(modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        options.forEach { (value, label) ->
            val on = value == selected
            GameSurface(
                modifier = Modifier.weight(1f),
                shape = ButtonShape,
                color = if (on) c.accent else c.soft,
                contentColor = if (on) Color.White else MaterialTheme.colorScheme.onSurface,
                depth = 3.dp,
                onClick = { onSelect(value) },
            ) {
                Text(
                    label,
                    Modifier.fillMaxWidth().padding(vertical = 10.dp, horizontal = 4.dp),
                    style = MaterialTheme.typography.labelLarge,
                    textAlign = TextAlign.Center,
                    maxLines = 1,
                )
            }
        }
    }
}

/** Плитка-показник: підпис і велике число (як .sum у вебі). */
@Composable
fun StatTile(
    label: String,
    value: String,
    modifier: Modifier = Modifier,
    color: Color = Color.Unspecified,
    background: Color = RozkladTheme.colors.soft,
) {
    Column(
        modifier.clip(RoundedCornerShape(14.dp)).background(background).padding(horizontal = 8.dp, vertical = 10.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            value,
            style = MaterialTheme.typography.titleMedium,
            fontFamily = GameFont,
            fontWeight = FontWeight.Normal,
            color = color,
            maxLines = 1,
        )
        Text(label, style = MaterialTheme.typography.labelSmall, color = LocalContentColor.current.copy(alpha = 0.8f))
    }
}

/** Квадратна «плитка» з оцінкою — велика цифра ігровим шрифтом. */
@Composable
fun GradeTile(label: String, color: Color, modifier: Modifier = Modifier, size: Dp = 48.dp) {
    Box(
        modifier.size(size).clip(RoundedCornerShape(14.dp)).background(color),
        contentAlignment = Alignment.Center,
    ) {
        // Довгі мітки («Зар.») — дрібніше, щоб влазили в плитку.
        Text(
            label,
            color = Color.White,
            fontFamily = GameFont,
            fontSize = (
                size.value * if (label.length >
                    2
                ) {
                    0.27f
                } else {
                    0.42f
                }
                ).sp,
            maxLines = 1,
        )
    }
}
