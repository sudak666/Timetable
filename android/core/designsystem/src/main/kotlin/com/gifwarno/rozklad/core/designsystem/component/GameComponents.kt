package com.gifwarno.rozklad.core.designsystem.component

import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.drawOutline
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.disabled
import androidx.compose.ui.semantics.progressBarRangeInfo
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import com.gifwarno.rozklad.core.designsystem.theme.GameStyle
import com.gifwarno.rozklad.core.designsystem.theme.LocalGameStyle
import com.gifwarno.rozklad.core.designsystem.theme.RozkladTheme

/** Форма карток (як --r-xl у вебі). */
val CardShape: Shape
    @Composable @ReadOnlyComposable
    get() = if (LocalGameStyle.current == GameStyle.BLOCKS) RoundedCornerShape(10.dp) else RoundedCornerShape(20.dp)

/** Форма кнопок. */
val ButtonShape: Shape
    @Composable @ReadOnlyComposable
    get() = if (LocalGameStyle.current == GameStyle.BLOCKS) RoundedCornerShape(8.dp) else RoundedCornerShape(14.dp)

/**
 * «Ігрова» поверхня: обведення й тверда тінь знизу (як стиль «Арена» у вебі).
 * З [onClick] — натискається «в глибину»: верх опускається на тінь, телефон легенько вібрує.
 */
@Composable
fun GameSurface(
    modifier: Modifier = Modifier,
    shape: Shape = CardShape,
    color: Color = RozkladTheme.colors.card,
    brush: Brush? = null,
    contentColor: Color = MaterialTheme.colorScheme.onSurface,
    depth: Dp = 4.dp,
    borderWidth: Dp = 2.dp,
    onClick: (() -> Unit)? = null,
    onClickLabel: String? = null,
    content: @Composable BoxScope.() -> Unit,
) {
    val c = RozkladTheme.colors
    val classic = LocalGameStyle.current == GameStyle.CLASSIC
    // «Звичайний» — тонка рамка кольору лінії й м'яка тінь; «Арена» і «Блоки» — темне обведення й тверда тінь.
    val ink = if (classic) c.line else c.ink
    val shadow = if (classic) Color.Black.copy(alpha = CLASSIC_SHADOW) else c.ink
    val border = if (classic) minOf(borderWidth, 1.dp) else borderWidth
    // «Звичайний» — лише ледь помітний «підйом», без важкої плити під кнопкою.
    val lift = if (classic) minOf(depth, CLASSIC_DEPTH.dp) else depth
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val press by animateDpAsState(if (pressed && onClick != null) lift else 0.dp, spring(stiffness = 1400f), label = "press")
    val haptics = LocalHapticFeedback.current
    Box(
        modifier.drawBehind {
            val d = lift.toPx()
            if (d > 0f) {
                val outline = shape.createOutline(Size(size.width, size.height - d), layoutDirection, this)
                translate(top = d) { drawOutline(outline, shadow) }
            }
        },
        // Верх займає весь розмір обгортки (fillMaxWidth/weight), інакше тінь ширша за кнопку.
        propagateMinConstraints = true,
    ) {
        Box(
            Modifier
                .padding(bottom = lift)
                .offset { IntOffset(0, press.roundToPx()) }
                .clip(shape)
                .then(if (brush != null) Modifier.background(brush) else Modifier.background(color))
                .border(border, ink, shape)
                .then(
                    if (onClick != null) {
                        Modifier.clickable(
                            interactionSource = interaction,
                            indication = null,
                            role = Role.Button,
                            onClickLabel = onClickLabel,
                        ) {
                            haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                            onClick()
                        }
                    } else {
                        Modifier
                    },
                ),
        ) {
            CompositionLocalProvider(LocalContentColor provides contentColor) { content() }
        }
    }
}

/** Об'ємна кнопка: акцентна заливка, білий жирний текст. */
@Composable
fun GameButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    color: Color = RozkladTheme.colors.accent,
    contentColor: Color = Color.White,
    leading: String? = null,
    enabled: Boolean = true,
) {
    GameSurface(
        modifier = modifier.semantics { if (!enabled) disabled() },
        shape = ButtonShape,
        color = if (enabled) color else RozkladTheme.colors.soft,
        contentColor = if (enabled) contentColor else RozkladTheme.colors.muted,
        depth = if (enabled) 4.dp else 2.dp,
        onClick = if (enabled) onClick else null,
    ) {
        Text(
            // «+» і «−» — впритул до слова; емодзі — з невеликим відступом.
            text = when {
                leading == null -> text
                leading in TIGHT_SIGNS -> "${leading.replace('＋', '+')} $text"
                else -> "$leading  $text"
            },
            modifier = Modifier.align(Alignment.Center).padding(horizontal = 18.dp, vertical = 12.dp),
            style = MaterialTheme.typography.labelLarge,
            textAlign = TextAlign.Center,
        )
    }
}

/** Білий текст з темним обведенням і тінню — великі «ігрові» написи на кольоровому тлі. */
@Composable
fun StrokeText(
    text: String,
    style: TextStyle,
    modifier: Modifier = Modifier,
    color: Color = Color.White,
    stroke: Color = RozkladTheme.colors.ink,
    strokeWidth: Dp = 4.dp,
    maxLines: Int = Int.MAX_VALUE,
) {
    val px = with(androidx.compose.ui.platform.LocalDensity.current) { strokeWidth.toPx() }
    if (LocalGameStyle.current == GameStyle.CLASSIC) {
        // «Звичайний» — без обведення, лише легка тінь.
        Text(
            text,
            modifier,
            style = style.copy(shadow = Shadow(Color.Black.copy(alpha = CLASSIC_TEXT_SHADOW), Offset(0f, px / 2), px)),
            color = color,
            maxLines = maxLines,
        )
        return
    }
    Box(modifier.semantics(mergeDescendants = true) { contentDescription = text }) {
        Text(
            text,
            style = style.copy(drawStyle = Stroke(width = px, join = androidx.compose.ui.graphics.StrokeJoin.Round)),
            color = stroke,
            maxLines = maxLines,
            modifier = Modifier.clearAndSetSemantics { },
        )
        Text(
            text,
            style = style.copy(shadow = Shadow(stroke, Offset(0f, px * 0.75f), 0f)),
            color = color,
            maxLines = maxLines,
            modifier = Modifier.clearAndSetSemantics { },
        )
    }
}

/** Товста смужка прогресу з обведенням — як «рівень» у грі. */
@Composable
fun GameProgressBar(
    progress: Float,
    modifier: Modifier = Modifier,
    fill: Brush = Brush.horizontalGradient(listOf(RozkladTheme.colors.gold, RozkladTheme.colors.goldEnd)),
    track: Color = RozkladTheme.colors.soft,
    height: Dp = 14.dp,
) {
    val c = RozkladTheme.colors
    val gameStyle = LocalGameStyle.current
    val ink = if (gameStyle == GameStyle.CLASSIC) c.line else c.ink
    val blocks = gameStyle == GameStyle.BLOCKS
    val p = progress.coerceIn(0f, 1f)
    Box(
        modifier
            .fillMaxWidth()
            .height(height)
            .semantics { progressBarRangeInfo = ProgressBarRangeInfo(p, 0f..1f) }
            .clip(if (blocks) RoundedCornerShape(4.dp) else RoundedCornerShape(50))
            .background(track)
            .border(2.dp, ink, if (blocks) RoundedCornerShape(4.dp) else RoundedCornerShape(50)),
    ) {
        if (p > 0f) {
            Box(
                Modifier
                    .fillMaxHeight()
                    .fillMaxWidth(p)
                    .clip(if (blocks) RoundedCornerShape(4.dp) else RoundedCornerShape(50))
                    .background(fill)
                    .drawBehind {
                        // Відблиск зверху й тінь знизу — «скляна» смужка.
                        drawRect(Color.White.copy(alpha = 0.35f), size = Size(size.width, size.height * 0.35f))
                        // «Блоки» — смужка з окремих «кубиків».
                        if (blocks) {
                            val step = BLOCK_STEP.dp.toPx()
                            var x = step
                            while (x < size.width) {
                                drawRect(Color.Black.copy(alpha = 0.18f), topLeft = Offset(x, 0f), size = Size(2.dp.toPx(), size.height))
                                x += step
                            }
                        }
                        drawRect(
                            Color.Black.copy(alpha = 0.15f),
                            topLeft = Offset(0f, size.height * 0.75f),
                            size = Size(size.width, size.height * 0.25f),
                        )
                    },
            )
        }
    }
}

/** Квадратик з номером уроку кольору предмета (як .n у вебі). */
@Composable
fun NumberTile(number: Int, color: Color, modifier: Modifier = Modifier, size: Dp = 30.dp) {
    Box(
        modifier
            .size(size)
            .clip(RoundedCornerShape(9.dp))
            .background(color)
            .drawBehind {
                drawRoundRect(
                    Color.Black.copy(alpha = 0.2f),
                    topLeft = Offset(0f, this.size.height - 3.dp.toPx()),
                    size = Size(this.size.width, 3.dp.toPx()),
                )
            },
        contentAlignment = Alignment.Center,
    ) {
        Text("$number", color = Color.White, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Black)
    }
}

/** Невеличка «пігулка» з текстом. */
@Composable
fun Pill(
    text: String,
    modifier: Modifier = Modifier,
    color: Color = RozkladTheme.colors.soft,
    contentColor: Color = RozkladTheme.colors.muted,
) {
    Text(
        text,
        modifier = modifier.clip(RoundedCornerShape(50)).background(color).padding(horizontal = 10.dp, vertical = 4.dp),
        color = contentColor,
        style = MaterialTheme.typography.labelMedium,
    )
}

/**
 * Тло «арени»: нічне фіолетове небо із зірками й летючими кубиками — власний малюнок,
 * без чужих персонажів (застосунок публікується в Google Play).
 */
@Composable
fun ArenaBackground(modifier: Modifier = Modifier, content: @Composable BoxScope.() -> Unit) {
    val c = RozkladTheme.colors
    val blocksSky = LocalGameStyle.current == GameStyle.BLOCKS
    Box(
        modifier.drawBehind {
            drawRect(Brush.verticalGradient(listOf(c.skyTop, c.skyBottom)))
            drawCircle(
                Brush.radialGradient(
                    listOf(c.accentEnd.copy(alpha = if (c.dark) 0.18f else 0.30f), Color.Transparent),
                    center = Offset(size.width * 0.8f, size.height * 0.08f),
                    radius = size.width * 0.9f,
                ),
                radius = size.width * 0.9f,
                center = Offset(size.width * 0.8f, size.height * 0.08f),
            )
            val u = size.minDimension / 100f
            if (blocksSky) {
                // «Блоки»: прямокутні піксельні хмари замість зірок.
                CLOUDS.forEach { (x, y, w) ->
                    val u2 = size.minDimension / 100f
                    val tl = Offset(x * size.width, y * size.height)
                    drawRect(Color.White.copy(alpha = if (c.dark) 0.10f else 0.75f), topLeft = tl, size = Size(w * u2, 6 * u2))
                    drawRect(
                        Color.White.copy(alpha = if (c.dark) 0.10f else 0.75f),
                        topLeft = tl + Offset(w * u2 * 0.25f, -4 * u2),
                        size = Size(w * u2 * 0.5f, 4 * u2),
                    )
                }
            } else {
                STARS.forEach { (x, y, r) ->
                    // Чотирипроменева «іскра» — читається як зірка, а не як випадкова крапка.
                    val cx = x * size.width
                    val cy = y * size.height
                    val big = r * u * 1.6f
                    val path = Path().apply {
                        moveTo(cx, cy - big)
                        quadraticTo(cx, cy, cx + big, cy)
                        quadraticTo(cx, cy, cx, cy + big)
                        quadraticTo(cx, cy, cx - big, cy)
                        quadraticTo(cx, cy, cx, cy - big)
                        close()
                    }
                    drawPath(path, Color(0xFFFFF3C4).copy(alpha = 0.55f))
                }
            }
            BLOCKS.forEach { (x, y, s) ->
                val side = s * u
                val tl = Offset(x * size.width, y * size.height)
                drawRoundRect(
                    Color.Black.copy(alpha = 0.18f),
                    topLeft = tl + Offset(0f, side * 0.12f),
                    size = Size(side, side),
                    cornerRadius = CornerRadius(side * 0.18f),
                )
                drawRoundRect(
                    Color.White.copy(alpha = if (c.dark) 0.06f else 0.10f),
                    topLeft = tl,
                    size = Size(side, side),
                    cornerRadius = CornerRadius(side * 0.18f),
                )
            }
        },
        content = content,
    )
}

/** Хмари «Блоків»: (x, y — частки екрана; ширина в сотих короткої сторони). */
private val CLOUDS = listOf(
    Triple(0.05f, 0.08f, 26f),
    Triple(0.62f, 0.15f, 32f),
    Triple(0.30f, 0.34f, 22f),
    Triple(0.70f, 0.52f, 28f),
    Triple(0.08f, 0.70f, 30f),
    Triple(0.55f, 0.86f, 24f),
)

private const val CLASSIC_SHADOW = 0.08f
private const val CLASSIC_DEPTH = 2
private val TIGHT_SIGNS = setOf("＋", "+", "−", "-")
private const val CLASSIC_TEXT_SHADOW = 0.35f
private const val BLOCK_STEP = 16

/** (x, y — частки екрана; r — радіус у сотих короткої сторони). */
private val STARS = listOf(
    Triple(0.08f, 0.06f, 0.9f), Triple(0.27f, 0.13f, 0.6f), Triple(0.52f, 0.04f, 1.1f),
    Triple(0.71f, 0.17f, 0.7f), Triple(0.92f, 0.09f, 0.8f), Triple(0.15f, 0.31f, 0.7f),
    Triple(0.86f, 0.36f, 1.0f), Triple(0.40f, 0.42f, 0.5f), Triple(0.05f, 0.55f, 0.8f),
    Triple(0.63f, 0.58f, 0.6f), Triple(0.95f, 0.66f, 0.7f), Triple(0.22f, 0.74f, 1.0f),
    Triple(0.48f, 0.83f, 0.6f), Triple(0.78f, 0.88f, 0.9f), Triple(0.10f, 0.94f, 0.6f),
)

private val BLOCKS = listOf(
    Triple(-0.04f, 0.18f, 16f),
    Triple(0.88f, 0.24f, 12f),
    Triple(0.80f, 0.70f, 18f),
    Triple(-0.06f, 0.80f, 20f),
    Triple(0.55f, 0.95f, 14f),
)

/** Плаваюча нижня навігація-«пігулка» (як .bnav у вебі). */
@Composable
fun GameNavBar(modifier: Modifier = Modifier, content: @Composable RowScope.() -> Unit) {
    GameSurface(modifier = modifier, shape = RoundedCornerShape(24.dp), depth = 5.dp) {
        Row(Modifier.fillMaxWidth().padding(6.dp), verticalAlignment = Alignment.CenterVertically, content = content)
    }
}

/** Вкладка навігації: обрана — залита акцентом, інші — приглушені. */
@Composable
fun RowScope.GameNavItem(selected: Boolean, onClick: () -> Unit, icon: ImageVector, label: String, modifier: Modifier = Modifier) {
    val c = RozkladTheme.colors
    val haptics = LocalHapticFeedback.current
    val scale by animateFloatAsState(if (selected) 1.1f else 1f, spring(dampingRatio = 0.45f, stiffness = 600f), label = "nav")
    Column(
        modifier
            .weight(1f)
            .clip(RoundedCornerShape(18.dp))
            .background(if (selected) c.accent else Color.Transparent)
            .selectable(selected = selected, role = Role.Tab) {
                if (!selected) haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                onClick()
            }
            .padding(vertical = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        val tint = if (selected) Color.White else c.muted
        Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(24.dp).scale(scale))
        Text(label, color = tint, style = MaterialTheme.typography.labelMedium, maxLines = 1)
    }
}
