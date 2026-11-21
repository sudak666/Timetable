package com.gifwarno.rozklad.core.designsystem.component

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
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
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathMeasure
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawOutline
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.takeOrElse
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.gifwarno.rozklad.core.designsystem.theme.Brand
import com.gifwarno.rozklad.core.designsystem.theme.GameTitleStyle
import com.gifwarno.rozklad.core.designsystem.theme.HandFont
import com.gifwarno.rozklad.core.designsystem.theme.RozkladTheme

/** Заголовок розділу прямо на сторінці — від руки чорнилом (уночі крейдою). Для TalkBack — заголовок. */
@Composable
fun SectionTitle(text: String, modifier: Modifier = Modifier) {
    TitleText(text, GameTitleStyle.copy(fontSize = 20.sp, lineHeight = 26.sp), modifier.padding(top = 6.dp).semantics { heading() })
}

/** Заголовок усередині картки — від руки чорнилом (уночі крейдою). Для TalkBack — заголовок (можна стрибати між розділами). */
@Composable
fun CardTitle(text: String, modifier: Modifier = Modifier, color: Color = Color.Unspecified) {
    val ink = if (RozkladTheme.colors.dark) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.primary
    Text(text, modifier.semantics { heading() }, style = CardTitleHand, color = color.takeOrElse { ink })
}

private val CardTitleHand = TextStyle(fontFamily = HandFont, fontWeight = FontWeight.Bold, fontSize = 24.sp, lineHeight = 26.sp)

/** Верхня панель на сторінці: кругла кнопка «назад», заголовок від руки, дії праворуч. */
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
        TitleText(
            title,
            GameTitleStyle.copy(fontSize = 20.sp, lineHeight = 24.sp),
            Modifier.weight(1f).semantics { heading() },
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
        CheckBox(checked, CircleShape, fill = c.positive, tick = c.onPositive, size = size)
    }
}

/**
 * Позначка з підписом — клітинка зошита з галочкою (замість квадратного Material-чекбокса).
 * Увесь рядок натискається (≥ 48 dp), TalkBack чує «прапорець, позначено / не позначено».
 */
@Composable
fun CheckRow(label: String, checked: Boolean, onToggle: () -> Unit, modifier: Modifier = Modifier) {
    val haptics = LocalHapticFeedback.current
    val ink = MaterialTheme.colorScheme.primary
    Row(
        modifier
            .heightIn(min = 48.dp)
            .clip(RoundedCornerShape(12.dp))
            .toggleable(value = checked, role = Role.Checkbox) {
                haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                onToggle()
            }
            .padding(horizontal = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // На заливці кольору людини галочка біла, на світлій (вночі) — кольору дошки.
        val tick = if (RozkladTheme.colors.dark) MaterialTheme.colorScheme.surface else Color.White
        CheckBox(checked, RoundedCornerShape(7.dp), fill = ink, tick = tick, size = 22.dp)
        Text(label, Modifier.padding(start = 10.dp), style = MaterialTheme.typography.labelLarge, color = ink, maxLines = 1)
    }
}

/**
 * Квадратик чи кружечок позначки: порожній — обвідка, позначений — заливка й намальована галочка, що «підстрибує».
 * Без пари clip + border (вона дає світлий «ореол» на краю); галочка — намальована лінія, а не символ шрифту.
 */
@Composable
private fun CheckBox(checked: Boolean, shape: Shape, fill: Color, tick: Color, size: Dp) {
    val muted = RozkladTheme.colors.muted
    val scale by animateFloatAsState(if (checked) 1f else 0.92f, spring(dampingRatio = 0.35f, stiffness = 700f), label = "check")
    val drawn by animateFloatAsState(if (checked) 1f else 0f, tween(CHECK_MS), label = "tick")
    Box(
        Modifier
            .size(size)
            .scale(scale)
            .drawWithCache {
                val edge = CHECK_EDGE.dp.toPx()
                val outer = shape.createOutline(this.size, layoutDirection, this)
                // Контур порожньої позначки — посередині лінії по краю, тож фігура менша на півлінії з кожного боку.
                val ring = shape.createOutline(Size(this.size.width - edge, this.size.height - edge), layoutDirection, this)
                val mark = Path().apply {
                    moveTo(this@drawWithCache.size.width * 0.27f, this@drawWithCache.size.height * 0.52f)
                    lineTo(this@drawWithCache.size.width * 0.44f, this@drawWithCache.size.height * 0.69f)
                    lineTo(this@drawWithCache.size.width * 0.74f, this@drawWithCache.size.height * 0.35f)
                }
                val measure = PathMeasure().apply { setPath(mark, false) }
                val part = Path()
                onDrawBehind {
                    if (checked) {
                        drawOutline(outer, fill)
                    } else {
                        translate(left = edge / 2, top = edge / 2) { drawOutline(ring, muted, style = Stroke(edge)) }
                    }
                    if (drawn > 0f) {
                        part.reset()
                        measure.getSegment(0f, measure.length * drawn, part, true)
                        drawPath(part, tick, style = Stroke(width = CHECK_STROKE.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round))
                    }
                }
            },
    )
}

private const val CHECK_EDGE = 2.5f
private const val CHECK_STROKE = 2.6f
private const val CHECK_MS = 180

/** Перемикач з кількох варіантів — «пігулки», обрана залита акцентом. */
@Composable
fun <T> GameSegmented(
    options: List<Pair<T, String>>,
    selected: T,
    onSelect: (T) -> Unit,
    modifier: Modifier = Modifier,
    /** 2 — для вузьких кнопок з емодзі над підписом (емодзі, перенос рядка, слово). */
    maxLines: Int = 1,
) {
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
                FitText(
                    label,
                    Modifier.fillMaxWidth().padding(vertical = 10.dp, horizontal = 4.dp),
                    style = MaterialTheme.typography.labelLarge,
                    textAlign = TextAlign.Center,
                    maxLines = maxLines,
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
        FitText(
            value,
            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Normal),
            color = color,
        )
        FitText(label, style = MaterialTheme.typography.labelSmall, color = LocalContentColor.current.copy(alpha = 0.8f))
    }
}

/**
 * Оцінка — клітинка, а в ній — від руки червоною ручкою вчителя (уночі — червоною крейдою).
 * Рамка клітинки — колір рівня ([color]: зелений — відмінно, червоний — погано), тож рівень видно з першого погляду.
 */
@Composable
fun GradeTile(label: String, color: Color, modifier: Modifier = Modifier, size: Dp = 48.dp) {
    val c = RozkladTheme.colors
    val pen = if (c.dark) Brand.ChalkRed else Brand.RedPen
    Box(
        modifier
            .size(size)
            .outlinedBackground(SolidColor(c.card), RoundedCornerShape(12.dp), 2.dp, color),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            label,
            color = pen,
            fontFamily = HandFont,
            fontWeight = FontWeight.Bold,
            fontSize = (size.value * if (label.length > 2) HAND_LONG else HAND_SHORT).sp,
            maxLines = 1,
        )
    }
}

/** Розмір цифри від руки відносно клітинки: «12» — великим, «Зар.» — дрібніше. */
private const val HAND_SHORT = 0.62f
private const val HAND_LONG = 0.4f
