package com.gifwarno.rozklad.core.designsystem.component

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.CacheDrawScope
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.dp
import com.gifwarno.rozklad.core.designsystem.theme.Brand
import com.gifwarno.rozklad.core.designsystem.theme.RozkladColors
import com.gifwarno.rozklad.core.designsystem.theme.RozkladTheme
import com.gifwarno.rozklad.core.model.Paper
import kotlin.math.cos
import kotlin.math.sin
import kotlin.random.Random

/**
 * Малює [paper] у розмірі цього шару: удень — аркуш (клітинка, лінійка, крапки, міліметрівка, крафт),
 * уночі — той самий візерунок ледь помітною крейдою на дошці. Контури рахуються раз на розмір (drawWithCache).
 * [margin] — червона лінія полів (у клітинці й лінійці); [boost] — у скільки разів яскравіший візерунок
 * (мініатюри для вибору: на дошці ледь помітна крейда інакше зливається).
 */
fun Modifier.paper(paper: Paper, c: RozkladColors, margin: Boolean = true, boost: Float = 1f): Modifier = drawWithCache {
    val art = paperArt(paper, c.dark)
    val base = paperBase(paper, c)
    val hair = 1.dp.toPx()
    val marginX = MARGIN_DP.dp.toPx()
    val marginW = 2.dp.toPx()
    onDrawBehind {
        drawRect(base)
        if (c.dark) drawChalkDust()
        listOfNotNull(art.lines, art.bold, art.fibersLight).forEach {
            drawPath(it.path, it.color.boosted(boost), style = Stroke(width = hair * it.width, cap = StrokeCap.Round))
        }
        art.dots?.let { drawPath(it.path, it.color.boosted(boost)) }
        if (margin && !c.dark && paper.hasMargin) {
            drawLine(MARGIN_RED, Offset(marginX, 0f), Offset(marginX, size.height), strokeWidth = marginW)
        }
    }
}

/**
 * Тло сторінки з обраним папером за картками. Папір — в окремому шарі, тож прокрутка карток
 * не перемальовує тисячі ліній і крапок щокадру.
 */
@Composable
fun PageBackground(modifier: Modifier = Modifier, content: @Composable BoxScope.() -> Unit) {
    val c = RozkladTheme.colors
    Box(modifier) {
        Box(Modifier.matchParentSize().graphicsLayer().paper(c.paper, c))
        content()
    }
}

/** Колір самого аркуша: дошка вночі, крафт, інакше — кремовий папір (а не c.page, бо той може бути крафтом). */
private fun paperBase(paper: Paper, c: RozkladColors): Color = when {
    c.dark -> c.page
    paper == Paper.KRAFT -> Brand.Kraft
    else -> Brand.Paper
}

private fun Color.boosted(k: Float): Color = if (k == 1f) this else copy(alpha = (alpha * k).coerceAtMost(1f))

/** Поля є в зошитах у клітинку й лінійку; крапки, міліметрівка й крафт — без них. */
private val Paper.hasMargin: Boolean get() = this == Paper.GRID || this == Paper.LINES

private class Strokes(val path: Path, val color: Color, val width: Float = 1f)

private class Dots(val path: Path, val color: Color)

private class PaperArt(val lines: Strokes? = null, val bold: Strokes? = null, val fibersLight: Strokes? = null, val dots: Dots? = null)

private fun CacheDrawScope.paperArt(paper: Paper, dark: Boolean): PaperArt {
    val ink = if (dark) Brand.Chalk else Brand.Ink
    return when (paper) {
        Paper.GRID -> PaperArt(lines = Strokes(grid(GRID_DP.dp.toPx()), ink.copy(alpha = if (dark) 0.04f else 0.09f)))

        Paper.LINES -> PaperArt(lines = Strokes(ruled(LINE_DP.dp.toPx()), ink.copy(alpha = if (dark) 0.06f else 0.16f)))

        Paper.DOTS -> PaperArt(dots = Dots(dotGrid(GRID_DP.dp.toPx(), DOT_DP.dp.toPx()), ink.copy(alpha = if (dark) 0.12f else 0.26f)))

        Paper.GRAPH -> {
            // Міліметрівка: дрібна сітка й кожна п'ята лінія — жирніша. Удень — помаранчева, як справжня.
            val tone = if (dark) Brand.Chalk else GRAPH_ORANGE
            val step = MM_DP.dp.toPx()
            PaperArt(
                lines = Strokes(grid(step), tone.copy(alpha = if (dark) 0.03f else 0.13f), width = 0.6f),
                bold = Strokes(grid(step * MM_BOLD_EVERY), tone.copy(alpha = if (dark) 0.07f else 0.3f)),
            )
        }

        Paper.KRAFT -> {
            // Волокна крафту: короткі риски темніші й світліші за папір, розкидані завжди однаково (сталий seed).
            val (darkFibers, lightFibers) = fibers()
            PaperArt(
                lines = Strokes(darkFibers, if (dark) Brand.Chalk.copy(alpha = 0.05f) else KRAFT_FIBER, width = 0.9f),
                fibersLight = Strokes(lightFibers, Color.White.copy(alpha = if (dark) 0.03f else 0.35f), width = 0.9f),
            )
        }
    }
}

private fun CacheDrawScope.grid(step: Float): Path = Path().apply {
    var x = step
    while (x < size.width) {
        moveTo(x, 0f)
        lineTo(x, size.height)
        x += step
    }
    var y = step
    while (y < size.height) {
        moveTo(0f, y)
        lineTo(size.width, y)
        y += step
    }
}

private fun CacheDrawScope.ruled(step: Float): Path = Path().apply {
    var y = step
    while (y < size.height) {
        moveTo(0f, y)
        lineTo(size.width, y)
        y += step
    }
}

/** Усі крапки одним контуром — один виклик малювання замість тисячі кіл. */
private fun CacheDrawScope.dotGrid(step: Float, r: Float): Path = Path().apply {
    var y = step
    while (y < size.height) {
        var x = step
        while (x < size.width) {
            addOval(Rect(Offset(x, y), r))
            x += step
        }
        y += step
    }
}

private fun CacheDrawScope.fibers(): Pair<Path, Path> {
    val rnd = Random(KRAFT_SEED)
    val cell = FIBER_CELL_DP.dp.toPx()
    val count = ((size.width * size.height) / (cell * cell)).toInt().coerceAtLeast(MIN_FIBERS)
    val minLen = FIBER_MIN_DP.dp.toPx()
    val spread = FIBER_SPREAD_DP.dp.toPx()
    val darkP = Path()
    val lightP = Path()
    repeat(count) {
        val x = rnd.nextFloat() * size.width
        val y = rnd.nextFloat() * size.height
        // Волокна лягають переважно вздовж аркуша (як у справжньому папері), з невеликим розкидом кута.
        val a = (rnd.nextFloat() - 0.5f) * FIBER_ANGLE
        val len = minLen + rnd.nextFloat() * spread
        val p = if (rnd.nextFloat() < DARK_SHARE) darkP else lightP
        p.moveTo(x, y)
        p.lineTo(x + cos(a) * len, y + sin(a) * len)
    }
    return darkP to lightP
}

/** Розводи крейди на дошці під будь-яким візерунком. */
private fun DrawScope.drawChalkDust() {
    SMUDGES.forEach { (x, y, r) ->
        val center = Offset(x * size.width, y * size.height)
        val radius = r * size.minDimension
        drawCircle(Brush.radialGradient(listOf(CHALK_DUST, Color.Transparent), center, radius), radius, center)
    }
}

/** Клітинка — як у зошиті (5 мм ≈ 18 dp); лінійка — 8 мм; поля — біля лівого краю. */
private const val GRID_DP = 18
private const val LINE_DP = 28
private const val DOT_DP = 1.2f
private const val MM_DP = 6
private const val MM_BOLD_EVERY = 5
private const val MARGIN_DP = 9
private val MARGIN_RED = Brand.RedPen.copy(alpha = 0.35f)
private val CHALK_DUST = Brand.Chalk.copy(alpha = 0.05f)
private val GRAPH_ORANGE = Color(0xFFD0661C)
private val KRAFT_FIBER = Color(0xFF7A5A2E).copy(alpha = 0.16f)
private const val KRAFT_SEED = 12
private const val FIBER_CELL_DP = 22
private const val MIN_FIBERS = 12
private const val FIBER_MIN_DP = 3
private const val FIBER_SPREAD_DP = 7
private const val FIBER_ANGLE = 1.2f
private const val DARK_SHARE = 0.6f

/** Розводи крейди на дошці: (x, y — частки екрана; радіус — частка короткої сторони). */
private val SMUDGES = listOf(
    Triple(0.2f, 0.12f, 0.7f),
    Triple(0.85f, 0.55f, 0.6f),
    Triple(0.3f, 0.9f, 0.5f),
)
