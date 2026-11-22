package com.gifwarno.rozklad.core.designsystem.component

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.defaultMinSize
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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableFloatState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.CacheDrawScope
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.GraphicsLayerScope
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.drawOutline
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.takeOrElse
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.disabled
import androidx.compose.ui.semantics.progressBarRangeInfo
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.gifwarno.rozklad.core.designsystem.theme.HAND_SCALE
import com.gifwarno.rozklad.core.designsystem.theme.HandFont
import com.gifwarno.rozklad.core.designsystem.theme.RozkladColors
import com.gifwarno.rozklad.core.designsystem.theme.RozkladTheme
import kotlin.math.PI
import kotlin.math.min
import kotlin.math.sin

/** Форма карток (як --r-xl у вебі). */
val CardShape: Shape = RoundedCornerShape(20.dp)

/** Форма кнопок. */
val ButtonShape: Shape = RoundedCornerShape(14.dp)

/**
 * Картка «Щоденника»: заливка й м'яка тінь (аркуш на сторінці). З [onClick] — натискається:
 * тінь притискається, телефон легенько вібрує. [depth] = 0 — без тіні (пласка плашка, напр. невибрана «пігулка»).
 * [outline] > 0 — обвідка чорнилом (уночі крейдою): так позначаємо обране (колір, аватар, день).
 */
@Composable
fun GameSurface(
    modifier: Modifier = Modifier,
    shape: Shape = CardShape,
    color: Color = RozkladTheme.colors.card,
    brush: Brush? = null,
    contentColor: Color = MaterialTheme.colorScheme.onSurface,
    depth: Dp = 4.dp,
    outline: Dp = 0.dp,
    outlineColor: Color = Color.Unspecified,
    onClick: (() -> Unit)? = null,
    onClickLabel: String? = null,
    content: @Composable BoxScope.() -> Unit,
) {
    val c = RozkladTheme.colors
    val ring = outlineColor.takeOrElse { if (c.dark) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.primary }
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val elevation by animateDpAsState(
        when {
            depth == 0.dp -> 0.dp
            pressed && onClick != null -> 1.dp
            else -> ELEVATION.dp
        },
        label = "elevation",
    )
    val haptics = LocalHapticFeedback.current
    // Зовнішня обгортка передає розмір (fillMaxWidth/weight) внутрішній, а та — ні вмісту: вміст сам вирівнюється.
    Box(
        modifier.then(
            if (elevation >
                0.dp
            ) {
                Modifier.shadow(elevation, shape, clip = false, ambientColor = c.ink, spotColor = c.ink)
            } else {
                Modifier
            },
        ),
        propagateMinConstraints = true,
    ) {
        Box(
            Modifier
                // Обвідка й заливка — одна фігура: спершу контур кольором обвідки, поверх — заливка, менша на її ширину.
                // Так на краю немає світлого «ореолу», який дає пара clip + border.
                .drawWithCache {
                    val bw = outline.toPx()
                    val outer = shape.createOutline(size, layoutDirection, this)
                    val inner = shape.createOutline(Size(size.width - 2 * bw, size.height - 2 * bw), layoutDirection, this)
                    onDrawBehind {
                        if (bw > 0f) drawOutline(outer, ring)
                        translate(left = bw, top = bw) {
                            if (brush != null) drawOutline(inner, brush) else drawOutline(inner, color)
                        }
                    }
                }
                .clip(shape)
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

/** Кнопка: акцентна заливка, білий жирний текст. */
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
        // Вимкнена кнопка — притлумлений власний колір, а не колір картки: її видно, але вона явно неактивна.
        color = if (enabled) color else lerp(RozkladTheme.colors.soft, color, DISABLED_TINT),
        contentColor = if (enabled) contentColor else contentColor.copy(alpha = DISABLED_TEXT),
        depth = if (enabled) 4.dp else 0.dp,
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

/**
 * Великий напис «Щоденника». Стиль із [com.gifwarno.rozklad.core.designsystem.theme.HandFont] — від руки
 * (Caveat трохи збільшуємо, бо він на вигляд дрібніший); інший стиль (напр. таймер) — як є.
 * [color] не задано — чорнило на папері (уночі — крейда); на кольоровій картці передавайте колір картки.
 */
@Composable
fun TitleText(
    text: String,
    style: TextStyle,
    modifier: Modifier = Modifier,
    color: Color = Color.Unspecified,
    maxLines: Int = Int.MAX_VALUE,
) {
    val styled = if (style.fontFamily == HandFont) {
        style.copy(fontSize = style.fontSize * HAND_SCALE, lineHeight = style.fontSize * HAND_SCALE * HAND_LINE)
    } else {
        style
    }
    val ink = if (RozkladTheme.colors.dark) MaterialTheme.colorScheme.onBackground else MaterialTheme.colorScheme.primary
    Text(text, modifier, style = styled, color = color.takeOrElse { ink }, maxLines = maxLines)
}

/** Товста смужка прогресу — «замальовується маркером». */
@Composable
fun GameProgressBar(
    progress: Float,
    modifier: Modifier = Modifier,
    fill: Brush = Brush.horizontalGradient(listOf(RozkladTheme.colors.gold, RozkladTheme.colors.goldEnd)),
    track: Color = RozkladTheme.colors.soft,
    height: Dp = 14.dp,
) {
    // Нові бали «доливаються» в смужку, а не стрибають; перший показ — одразу з потрібним значенням.
    // Значення читається лише під час малювання — анімація перемальовує смужку без перекомпозиції.
    val target = progress.coerceIn(0f, 1f)
    val animated = animateFloatAsState(target, tween(PROGRESS_MS), label = "progress")
    Box(
        modifier
            .fillMaxWidth()
            .height(height)
            .semantics { progressBarRangeInfo = ProgressBarRangeInfo(target, 0f..1f) }
            .drawBehind {
                val p = animated.value
                val r = CornerRadius(size.height / 2)
                drawRoundRect(track, cornerRadius = r)
                if (p > 0f) {
                    val filled = Size(size.width * p, size.height)
                    val path = Path().apply { addRoundRect(RoundRect(Rect(Offset.Zero, filled), r)) }
                    clipPath(path) {
                        drawRect(fill, size = filled)
                        // Об'єм — плавним переходом: світліше зверху, темніше знизу (різкі смуги читались як лінії).
                        drawRect(barShading(filled.height), size = filled)
                    }
                }
            },
    )
}

/** Відблиск і тінь смужки прогресу одним градієнтом — без видимих меж. */
private fun barShading(height: Float) = Brush.verticalGradient(
    0f to Color.White.copy(alpha = 0.30f),
    0.45f to Color.Transparent,
    0.6f to Color.Transparent,
    1f to Color.Black.copy(alpha = 0.15f),
    startY = 0f,
    endY = height,
)

/** Легке затемнення до низу квадратика — «об'ємна» плитка без смуги-лінії. */
private fun tileShading(height: Float) = Brush.verticalGradient(
    0.5f to Color.Transparent,
    1f to Color.Black.copy(alpha = 0.18f),
    startY = 0f,
    endY = height,
)

/** Квадратик з номером уроку кольору предмета (як .n у вебі). */
@Composable
fun NumberTile(number: Int, color: Color, modifier: Modifier = Modifier, size: Dp = 30.dp) {
    Box(
        modifier
            .size(size)
            .clip(RoundedCornerShape(9.dp))
            .background(color)
            .drawBehind { drawRect(tileShading(this.size.height)) },
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
    FitText(
        text,
        modifier = modifier.clip(RoundedCornerShape(50)).background(color).padding(horizontal = 10.dp, vertical = 4.dp),
        color = contentColor,
        style = MaterialTheme.typography.labelMedium,
    )
}

private const val DISABLED_TINT = 0.35f
private const val DISABLED_TEXT = 0.7f
private const val ELEVATION = 3
private val TIGHT_SIGNS = setOf("＋", "+", "−", "-")
private const val HAND_LINE = 1.05f
private const val PROGRESS_MS = 600
private const val NAV_COLOR_MS = 180
private const val NAV_MOTION_NS = 520_000_000L
private const val MAX_STEP_NS = 33_000_000L
private const val FROZEN_NS = 60_000_000L

/**
 * Програє рух значка. Натискання відкриває іншу вкладку, і кадри поруч із ним бувають важкі (малюється новий екран).
 * Тому кадр довший за [FROZEN_NS] (екран стояв) рух не просуває, а решта — не більше ніж на [MAX_STEP_NS]:
 * рух починається з першого плавного кадру й видно його повністю (звичайна анімація за часом «програвалась» би,
 * поки екран стоїть).
 */
private suspend fun playMotion(progress: MutableFloatState) {
    progress.floatValue = 0f
    // Кадр, у якому з'являється новий екран і вкладка стає обраною, — далі рух.
    var last = withFrameNanos { it }
    var t = 0f
    while (t < 1f) {
        val now = withFrameNanos { it }
        val dt = now - last
        // Застиглий кадр (екран не оновлювався) рух не просуває — інакше він «програвався» б непомітно.
        if (dt < FROZEN_NS) t += min(dt, MAX_STEP_NS).toFloat() / NAV_MOTION_NS
        last = now
        progress.floatValue = min(t, 1f)
    }
}
private const val POP = 0.18f
private const val FLIP_CAMERA = 12f
private const val HOP_PEAK = 0.4f
private const val HOP_LAND = 0.68f
private const val HOP_HEIGHT = 7f
private const val SQUASH = 0.16f
private const val WAVE_ANGLE = -16f
private const val WAVE_TURNS = 4f

/** Плаваюча нижня навігація-«пігулка» (як .bnav у вебі). */
@Composable
fun GameNavBar(modifier: Modifier = Modifier, content: @Composable RowScope.() -> Unit) {
    GameSurface(modifier = modifier, shape = RoundedCornerShape(24.dp), depth = 5.dp) {
        Row(Modifier.fillMaxWidth().padding(6.dp), verticalAlignment = Alignment.CenterVertically, content = content)
    }
}

/**
 * Маленька анімація значка вкладки при натисканні — у кожної своя, за змістом значка.
 * Повертає трансформацію значка для поступу `t` від 0 до 1.
 */
enum class NavMotion {
    /** Календар: сторінка перегортається вперед (оберт навколо горизонтальної осі). */
    FLIP,

    /** Зірка: перевертається, як медаль (оберт навколо вертикальної осі), з легким пружинним доворотом. */
    SPIN,

    /** Книжка: підстрибує й «приземляється» на стіл — сплющується й випрямляється. */
    HOP,

    /** Людина: помахує, похитуючись від «ніг». */
    WAVE,
    ;

    internal fun apply(layer: GraphicsLayerScope, t: Float) {
        val pop = 1f + POP * sin(PI.toFloat() * t)
        when (this) {
            FLIP -> {
                layer.rotationX = -360f * FastOutSlowInEasing.transform(t)
                layer.cameraDistance = FLIP_CAMERA * layer.density
                layer.scaleX = pop
                layer.scaleY = pop
            }

            SPIN -> {
                // Оберт зірки на місці майже не видно (вона симетрична), а «медаль» читається одразу.
                layer.rotationY = 360f * SpinEasing.transform(t)
                layer.cameraDistance = FLIP_CAMERA * layer.density
                layer.scaleX = pop
                layer.scaleY = pop
            }

            HOP -> {
                // Злітає (0..0,45), падає (0,45..0,7), сплющується при «приземленні» й випрямляється.
                val fall = ((t - HOP_PEAK) / (HOP_LAND - HOP_PEAK)).coerceIn(0f, 1f)
                // Вгору — гальмуючи, вниз — з прискоренням (як справжній стрибок).
                val up = if (t < HOP_PEAK) sin(PI.toFloat() / 2 * t / HOP_PEAK) else 1f - fall * fall
                layer.translationY = -HOP_HEIGHT * layer.density * up
                val squash = if (t > HOP_LAND) sin(PI.toFloat() * (t - HOP_LAND) / (1f - HOP_LAND)) else 0f
                layer.scaleX = 1f + SQUASH * squash
                layer.scaleY = 1f - SQUASH * squash
                layer.transformOrigin = TransformOrigin(0.5f, 1f)
            }

            WAVE -> {
                // Затухаюче похитування: −14°, +11°, −7°, +3°, 0.
                layer.rotationZ = WAVE_ANGLE * sin(WAVE_TURNS * PI.toFloat() * t) * (1f - t)
                layer.transformOrigin = TransformOrigin(0.5f, 1f)
            }
        }
    }
}

/** Пружинний доворот: трохи далі цілі й назад (без «гумового» розгойдування). */
private val SpinEasing = CubicBezierEasing(0.34f, 1.56f, 0.64f, 1f)

/**
 * Вкладка навігації: обрана — підпис «замальований» жовтим маркером, значок чорнилом; інші — приглушені.
 * [badge] > 0 — червоне число над іконкою (як .nb у вебі). Кольори перетікають, а не блимають.
 * [motion] — анімація значка при натисканні (і при повторному на ту саму вкладку).
 */
@Composable
fun RowScope.GameNavItem(
    selected: Boolean,
    onClick: () -> Unit,
    icon: ImageVector,
    label: String,
    modifier: Modifier = Modifier,
    badge: Int = 0,
    motion: NavMotion? = null,
) {
    val c = RozkladTheme.colors
    val haptics = LocalHapticFeedback.current
    val animate = rememberAnimationsEnabled()
    // Поступ анімації значка (1 — спокій). Читається лише в шарі малювання — без перекомпозиції на кожен кадр.
    val play = remember { mutableFloatStateOf(1f) }
    var taps by remember { mutableIntStateOf(0) }
    LaunchedEffect(taps) {
        if (taps > 0 && motion != null && animate) playMotion(play)
    }
    val scale by animateFloatAsState(if (selected) 1.1f else 1f, spring(dampingRatio = 0.45f, stiffness = 600f), label = "nav")
    val marker by animateColorAsState(if (selected) c.gold else c.gold.copy(alpha = 0f), tween(NAV_COLOR_MS), label = "navMarker")
    val tint by animateColorAsState(if (selected) MaterialTheme.colorScheme.primary else c.muted, tween(NAV_COLOR_MS), label = "navTint")
    Column(
        modifier
            .weight(1f)
            .clip(RoundedCornerShape(18.dp))
            .selectable(selected = selected, role = Role.Tab) {
                if (!selected) haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                taps++
                onClick()
            }
            .padding(vertical = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box {
            Icon(
                icon,
                contentDescription = null,
                tint = tint,
                // Значення анімації читаються лише в шарі малювання — без перекомпозиції на кожен кадр.
                modifier = Modifier
                    .size(24.dp)
                    .scale(scale)
                    .graphicsLayer { if (motion != null && play.floatValue < 1f) motion.apply(this, play.floatValue) },
            )
            if (badge > 0) {
                val text = if (badge > MAX_BADGE) "$MAX_BADGE+" else badge.toString()
                Text(
                    text,
                    Modifier
                        .align(Alignment.TopEnd)
                        .offset(x = 12.dp, y = (-6).dp)
                        .defaultMinSize(minWidth = 18.dp)
                        .clip(RoundedCornerShape(50))
                        .background(BADGE_RED)
                        .padding(horizontal = 5.dp)
                        .semantics { contentDescription = "$badge чекають підтвердження" },
                    color = Color.White,
                    style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                    textAlign = TextAlign.Center,
                    maxLines = 1,
                )
            }
        }
        FitText(
            label,
            modifier = Modifier.background(marker, MarkerShape).padding(horizontal = 8.dp),
            color = if (selected) MARKER_TEXT else tint,
            style = MaterialTheme.typography.labelMedium,
        )
    }
}

/** Мазок маркера: трохи нерівні кути, як проведено від руки. */
val MarkerShape: Shape = RoundedCornerShape(topStart = 3.dp, topEnd = 9.dp, bottomEnd = 4.dp, bottomStart = 8.dp)

/** Текст на жовтому маркері — графіт, читається і вдень, і на дошці. */
private val MARKER_TEXT = Color(0xFF23262F)

/** Червоний бейдж однаковий у світлій і темній темі: білий текст на ньому має контраст 5,4:1. */
private val BADGE_RED = Color(0xFFC0392B)
private const val MAX_BADGE = 99

/**
 * Заливка з обведенням без світлого «ореолу» на краю (пара clip + border його дає):
 * спершу фігура кольором рамки, поверх — заливка, менша на ширину рамки.
 */
fun Modifier.outlinedBackground(fill: Brush, shape: Shape, borderWidth: Dp, borderColor: Color): Modifier = drawWithCache {
    val bw = borderWidth.toPx()
    val outer = shape.createOutline(size, layoutDirection, this)
    val inner = shape.createOutline(Size(size.width - 2 * bw, size.height - 2 * bw), layoutDirection, this)
    onDrawBehind {
        if (bw > 0f) drawOutline(outer, borderColor)
        translate(left = bw, top = bw) { drawOutline(inner, fill) }
    }
}

/**
 * Підсвітка «зараз» (поточний урок, заняття, обраний предмет): удень — мазок жовтого маркера,
 * уночі — обведено жовтою крейдою з ледь помітною заливкою (суцільна напівпрозора заливка на дошці виходила брудно-сірою).
 */
fun Modifier.nowHighlight(on: Boolean, colors: RozkladColors): Modifier = if (!on) {
    this
} else {
    drawWithCache {
        val outline = HighlightShape.createOutline(size, layoutDirection, this)
        val chalk = Stroke(width = CHALK_LINE.dp.toPx())
        onDrawBehind {
            drawOutline(outline, colors.glow)
            if (colors.dark) drawOutline(outline, colors.gold.copy(alpha = CHALK_ALPHA), style = chalk)
        }
    }
}

private val HighlightShape = RoundedCornerShape(12.dp)
private const val CHALK_LINE = 1.5f
private const val CHALK_ALPHA = 0.8f
