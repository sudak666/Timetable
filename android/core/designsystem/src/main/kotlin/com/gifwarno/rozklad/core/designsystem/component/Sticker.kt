package com.gifwarno.rozklad.core.designsystem.component

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.padding
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.drawOutline
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.dp

/**
 * Наліпка «Щоденника»: трохи нахилена, з білим вирізом по краю й м'якою тінню — як справжня наліпка в зошиті.
 * Вміст (заливку) задавайте після цього модифікатора. [tilt] — нахил у градусах ([stickerTilt] дає різний для сусідів).
 */
fun Modifier.sticker(tilt: Float, shape: Shape): Modifier = this
    .graphicsLayer {
        rotationZ = tilt
        shadowElevation = STICKER_SHADOW.dp.toPx()
        this.shape = shape
        clip = false
    }
    .background(Color.White, shape)
    .padding(STICKER_EDGE.dp)

/** Порожнє місце під наліпку: пунктирний контур — видно, що наліпку ще можна заробити. */
fun Modifier.stickerSlot(shape: Shape, color: Color): Modifier = drawWithCache {
    val width = 2.dp.toPx()
    val outline = shape.createOutline(size, layoutDirection, this)
    val dashes = Stroke(width = width, pathEffect = PathEffect.dashPathEffect(floatArrayOf(6.dp.toPx(), 4.dp.toPx())))
    onDrawBehind { drawOutline(outline, color, style = dashes) }
}

/** Нахил наліпки за номером: сусідні наліпки нахилені по-різному, але щоразу однаково (без «тремтіння»). */
fun stickerTilt(index: Int): Float = TILTS[Math.floorMod(index, TILTS.size)]

private val TILTS = floatArrayOf(-3f, 2f, -1.5f, 2.5f, -2f, 1f)
private const val STICKER_SHADOW = 4
private const val STICKER_EDGE = 3
