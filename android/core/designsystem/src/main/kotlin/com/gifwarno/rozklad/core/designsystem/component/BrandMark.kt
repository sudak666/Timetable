package com.gifwarno.rozklad.core.designsystem.component

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathMeasure
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.progressBarRangeInfo
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.gifwarno.rozklad.core.designsystem.theme.Brand
import com.gifwarno.rozklad.core.designsystem.theme.RozkladTheme
import kotlin.math.PI
import kotlin.math.sin
import kotlinx.coroutines.delay

/**
 * Живе лого «12» — той самий малюнок, що на іконці застосунку (`ic_launcher_foreground.xml`):
 * сторінка зошита «вистрибує», червона «12» пишеться ручкою вчителя, жовтий маркер підкреслює, потім зрідка
 * блищить іскра. Уночі — крейдою на дошці.
 *
 * [loop] — для очікування: лого пишеться знову й знову (замість звичайного крутильця).
 * [contentDescription] = null — лого лише прикраса (поруч уже є назва), TalkBack його пропускає.
 * Вимкнені системні анімації — одразу готове лого, без руху.
 */
@Composable
fun BrandMark(modifier: Modifier = Modifier, size: Dp = 112.dp, loop: Boolean = false, contentDescription: String? = "Дванадцятка") {
    val animate = rememberAnimationsEnabled()
    val draw = remember { Animatable(if (animate) 0f else 1f) }
    val pop = remember { Animatable(if (animate) POP_FROM else 1f) }
    val sparkle = remember { Animatable(0f) }
    val inkAlpha = remember { Animatable(1f) }
    if (animate) {
        LaunchedEffect(Unit) { pop.animateTo(1f, spring(dampingRatio = 0.45f, stiffness = 380f)) }
        LaunchedEffect(loop) {
            if (loop) {
                while (true) {
                    draw.snapTo(0f)
                    inkAlpha.snapTo(1f)
                    draw.animateTo(1f, tween(DRAW_MS, easing = LinearEasing))
                    delay(HOLD_MS)
                    inkAlpha.animateTo(0f, tween(FADE_MS))
                }
            } else {
                draw.animateTo(1f, tween(DRAW_MS, easing = LinearEasing))
                while (true) {
                    sparkle.snapTo(0f)
                    sparkle.animateTo(1f, tween(SPARKLE_MS, easing = LinearEasing))
                    delay(SPARKLE_PAUSE_MS)
                }
            }
        }
    }

    val c = RozkladTheme.colors
    val ink = if (c.dark) BoardInk else PaperInk
    val marks = remember { BrandPaths() }
    val tileShape = RoundedCornerShape(size * TILE_RADIUS)
    GameSurface(
        modifier = modifier
            .size(size)
            .graphicsLayer {
                scaleX = pop.value
                scaleY = pop.value
                rotationZ = (1f - pop.value) * POP_TILT
            }
            .semantics {
                if (loop) {
                    this.contentDescription = "Завантажую…"
                    progressBarRangeInfo = ProgressBarRangeInfo.Indeterminate
                } else if (contentDescription != null) {
                    this.contentDescription = contentDescription
                    role = Role.Image
                }
            },
        shape = tileShape,
        color = ink.tile,
        depth = 6.dp,
    ) {
        Canvas(Modifier.fillMaxSize()) {
            val cell = this.size.minDimension / TILE_CELLS
            var x = cell
            while (x < this.size.width) {
                drawLine(ink.grid, Offset(x, 0f), Offset(x, this.size.height), strokeWidth = 1.dp.toPx())
                x += cell
            }
            var y = cell
            while (y < this.size.height) {
                drawLine(ink.grid, Offset(0f, y), Offset(this.size.width, y), strokeWidth = 1.dp.toPx())
                y += cell
            }
            // Обводимо сторінку тонким чорнилом (уночі — крейдою).
            val edge = EDGE_DP.dp.toPx()
            drawRoundRect(
                ink.edge,
                topLeft = Offset(edge / 2, edge / 2),
                size = Size(this.size.width - edge, this.size.height - edge),
                cornerRadius = CornerRadius(size.toPx() * TILE_RADIUS - edge / 2),
                style = Stroke(edge),
            )
            // Малюнок — посередині плитки.
            val s = this.size.minDimension / VIEW
            withTransform({
                translate((this.size.width - VIEW * s) / 2f, (this.size.height - VIEW * s) / 2f)
                scale(s, s, pivot = Offset.Zero)
                translate(-VIEW_X, -VIEW_Y)
            }) {
                marks.draw(this, draw.value, inkAlpha.value, ink)
                if (!loop && sparkle.value > 0f) drawSparkle(sin(PI.toFloat() * sparkle.value), sparkle.value)
            }
        }
    }
}

/** Контури «1», «2» і риски у вікні іконки 108×108 — точно як в `ic_launcher_foreground.xml`. */
private class BrandPaths {
    private val one = Path().apply {
        moveTo(40f, 42f)
        lineTo(47f, 36f)
        lineTo(47f, 66f)
    }
    private val two = Path().apply {
        moveTo(55f, 42f)
        cubicTo(55f, 37.5f, 58.5f, 35f, 63f, 35f)
        cubicTo(67.5f, 35f, 71f, 38f, 71f, 42.5f)
        cubicTo(71f, 47.5f, 66f, 51f, 55f, 66f)
        lineTo(72f, 66f)
    }

    /** Мазок маркера: трохи навскіс, під основою цифр (малюється першим — лежить під ними). */
    private val bar = Path().apply {
        moveTo(33f, 70f)
        lineTo(76f, 68f)
    }
    private val measure = PathMeasure()

    // Порядок малювання: маркер унизу, цифри зверху; пишеться ж маркер останнім.
    private val parts = listOf(bar to Path(), one to Path(), two to Path())

    fun draw(scope: DrawScope, t: Float, alpha: Float, ink: BrandInk) = with(scope) {
        val segments = listOf(segment(t, BAR_START, 1f), segment(t, 0f, ONE_END), segment(t, TWO_START, TWO_END))
        parts.forEachIndexed { i, (full, part) ->
            val p = segments[i]
            if (p <= 0f) return@forEachIndexed
            part.reset()
            measure.setPath(full, false)
            measure.getSegment(0f, measure.length * p, part, true)
            val isBar = i == 0
            val color = if (isBar) ink.marker else ink.digits
            drawPath(part, color.copy(alpha = color.alpha * alpha), style = stroke(if (isBar) BAR_WIDTH else DIGIT_WIDTH))
        }
    }

    private fun stroke(width: Float) = Stroke(width = width, cap = StrokeCap.Round, join = StrokeJoin.Round)

    /** Частка [a]..[b] загальної шкали — з плавним розгоном і гальмуванням. */
    private fun segment(t: Float, a: Float, b: Float) = FastOutSlowInEasing.transform(((t - a) / (b - a)).coerceIn(0f, 1f))
}

/** Чотирипроменева іскра біля «2». */
private fun DrawScope.drawSparkle(scale: Float, turn: Float) {
    val r = SPARKLE_R * scale
    if (r <= 0f) return
    rotate(degrees = turn * SPARKLE_TURN, pivot = SPARKLE_AT) {
        val (cx, cy) = SPARKLE_AT
        val path = Path().apply {
            moveTo(cx, cy - r)
            quadraticTo(cx, cy, cx + r, cy)
            quadraticTo(cx, cy, cx, cy + r)
            quadraticTo(cx, cy, cx - r, cy)
            quadraticTo(cx, cy, cx, cy - r)
            close()
        }
        drawPath(path, SPARKLE_COLOR)
    }
}

/** Вікно малюнка в координатах іконки: цифри посередині плитки з повітрям навколо. */
private const val VIEW_X = 27f
private const val VIEW_Y = 26f
private const val VIEW = 56f

private const val DIGIT_WIDTH = 7f
private const val BAR_WIDTH = 11f

private const val ONE_END = 0.38f
private const val TWO_START = 0.3f
private const val TWO_END = 0.82f
private const val BAR_START = 0.74f

private const val DRAW_MS = 1100
private const val HOLD_MS = 450L
private const val FADE_MS = 250
private const val SPARKLE_MS = 700
private const val SPARKLE_PAUSE_MS = 3500L

private const val POP_FROM = 0.6f
private const val POP_TILT = -20f
private const val TILE_RADIUS = 0.28f
private const val EDGE_DP = 2.5f

/** Скільки клітинок зошита вміщує плитка по ширині. */
private const val TILE_CELLS = 9f

private const val SPARKLE_R = 6f
private const val SPARKLE_TURN = 90f
private val SPARKLE_AT = Offset(77f, 32f)
private val SPARKLE_COLOR = Color(0xFFFFF3C4)

/** Барви лого: сторінка, клітинка, обведення, цифри, маркер. */
private class BrandInk(val tile: Color, val grid: Color, val edge: Color, val digits: Color, val marker: Color)

/** Удень: червона ручка на папері в клітинку. */
private val PaperInk = BrandInk(
    tile = Brand.Paper,
    grid = Brand.Ink.copy(alpha = 0.14f),
    edge = Brand.Ink,
    digits = Brand.RedPen,
    marker = Brand.Marker,
)

/** Уночі: крейда на дошці. */
private val BoardInk = BrandInk(
    tile = Brand.BoardCard,
    grid = Brand.Chalk.copy(alpha = 0.06f),
    edge = Brand.Chalk.copy(alpha = 0.3f),
    digits = Brand.Chalk,
    marker = Brand.ChalkYellow.copy(alpha = 0.85f),
)
