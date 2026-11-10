package com.gifwarno.rozklad.core.designsystem.component

import android.provider.Settings
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import kotlin.math.PI
import kotlin.math.sin
import kotlin.random.Random

private class Piece(
    val x: Float,
    val delay: Float,
    val fall: Float,
    val sway: Float,
    val phase: Float,
    val spin: Float,
    val w: Float,
    val h: Float,
    val color: Color,
)

private val CONFETTI_COLORS = listOf(
    Color(0xFFFDCB6E),
    Color(0xFFFD79A8),
    Color(0xFF6C5CE7),
    Color(0xFF00B894),
    Color(0xFF0984E3),
    Color(0xFFE17055),
    Color(0xFFFFFFFF),
)

private const val PIECES = 110
private const val DURATION_MS = 3400

/**
 * Салют з конфеті, що падає згори. Кожна нова [burst] (> 0) запускає його знову.
 * Малюється лише в Canvas (без перекомпозиції на кожен кадр); вимкнені системні анімації — без салюту.
 */
@Composable
fun ConfettiBurst(burst: Int, modifier: Modifier = Modifier) {
    if (burst <= 0) return
    val context = LocalContext.current
    val animationsOn = remember {
        Settings.Global.getFloat(context.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f) > 0f
    }
    if (!animationsOn) return
    val haptics = LocalHapticFeedback.current
    val progress = remember(burst) { Animatable(0f) }
    val pieces = remember(burst) {
        val r = Random(burst * 7919L)
        List(PIECES) {
            Piece(
                x = r.nextFloat(),
                delay = r.nextFloat() * 0.35f,
                fall = 0.8f + r.nextFloat() * 0.6f,
                sway = 0.02f + r.nextFloat() * 0.05f,
                phase = r.nextFloat() * 2f * PI.toFloat(),
                spin = (r.nextFloat() - 0.5f) * 1440f,
                w = 6f + r.nextFloat() * 8f,
                h = 10f + r.nextFloat() * 10f,
                color = CONFETTI_COLORS[r.nextInt(CONFETTI_COLORS.size)],
            )
        }
    }
    LaunchedEffect(burst) {
        haptics.performHapticFeedback(HapticFeedbackType.LongPress)
        progress.animateTo(1f, tween(DURATION_MS, easing = LinearEasing))
    }
    Canvas(modifier.fillMaxSize()) {
        val t = progress.value
        if (t <= 0f || t >= 1f) return@Canvas
        val scale = density
        pieces.forEach { p ->
            val local = ((t - p.delay) / (1f - p.delay)).coerceIn(0f, 1f)
            if (local <= 0f) return@forEach
            val x = (p.x + sin(p.phase + local * 7f) * p.sway) * size.width
            val y = (-0.05f + local * p.fall * 1.15f) * size.height
            val alpha = if (local > 0.8f) (1f - local) / 0.2f else 1f
            val w = p.w * scale
            val h = p.h * scale
            rotate(degrees = p.spin * local, pivot = Offset(x, y)) {
                drawRect(p.color.copy(alpha = alpha), topLeft = Offset(x - w / 2, y - h / 2), size = Size(w, h))
            }
        }
    }
}
