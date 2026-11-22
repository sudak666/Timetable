package com.gifwarno.rozklad.core.designsystem.component

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawOutline
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.gifwarno.rozklad.core.designsystem.theme.Brand
import com.gifwarno.rozklad.core.designsystem.theme.HandFont

/**
 * Обкладинка шкільного зошита предмета: кольорова, з темнішим корінцем і двома скобами;
 * на білій етикетці — номер уроку червоною ручкою. [faded] — урок минув: обкладинка вицвітає, номер — олівцем.
 */
@Composable
fun NotebookCover(number: Int, color: Color, modifier: Modifier = Modifier, faded: Boolean = false, width: Dp = 28.dp) {
    val fill = if (faded) lerp(color, FADED_GREY, FADE) else color
    val pen = if (faded) PENCIL_GREY else Brand.RedPen
    val measurer = rememberTextMeasurer()
    Box(
        modifier
            .size(width, width * COVER_RATIO)
            .shadow(if (faded) 0.dp else 2.dp, CoverShape)
            .semantics { contentDescription = "$number" }
            .drawWithCache {
                val outline = CoverShape.createOutline(size, layoutDirection, this)
                val spine = SPINE_DP.dp.toPx()
                val staple = STAPLE_DP.dp.toPx()
                val labelX = spine + LABEL_INSET_DP.dp.toPx()
                val labelTop = size.height * LABEL_TOP
                val labelSize = Size(size.width - labelX - LABEL_INSET_DP.dp.toPx(), size.height * LABEL_HEIGHT)
                val labelRadius = CornerRadius(2.dp.toPx())
                // Цифра — точно по центру етикетки за метриками Caveat (у шрифту великі поля над і під цифрами,
                // а штрихи нахилені вправо), розмір — від ширини обкладинки, а не від системного шрифту.
                val fs = size.width * DIGIT_PER_WIDTH
                val digit = measurer.measure("$number", CoverDigit.copy(fontSize = fs.toSp(), color = pen))
                val digitAt = Offset(
                    labelX + labelSize.width / 2 - digit.size.width / 2f - fs * INK_SHIFT_X,
                    labelTop + labelSize.height / 2 + fs * INK_HALF_HEIGHT - digit.firstBaseline,
                )
                onDrawBehind {
                    drawOutline(outline, fill)
                    // Корінець — та сама барва, лише темніша (а не кольорова смужка на нейтральному).
                    drawRect(lerp(fill, Color.Black, SPINE_SHADE), size = Size(spine, size.height))
                    listOf(STAPLE_A, STAPLE_B).forEach { y ->
                        val cy = size.height * y
                        drawLine(
                            STAPLE,
                            Offset(spine / 2, cy - staple / 2),
                            Offset(spine / 2, cy + staple / 2),
                            strokeWidth = 1.2.dp.toPx(),
                            cap = StrokeCap.Round,
                        )
                    }
                    drawRoundRect(LABEL, Offset(labelX, labelTop), labelSize, labelRadius)
                    drawText(digit, topLeft = digitAt)
                }
            },
    )
}

/**
 * Невелика «обкладинка-ярлик» з назвою предмета (рюкзак, домашка): барва предмета, темніший корінець, біла назва.
 * Як [Pill], але читається як зошит, а не як кнопка.
 */
@Composable
fun CoverTag(text: String, color: Color, modifier: Modifier = Modifier) {
    FitText(
        text,
        modifier = modifier
            .drawWithCache {
                val outline = TagShape.createOutline(size, layoutDirection, this)
                val spine = TAG_SPINE_DP.dp.toPx()
                onDrawBehind {
                    drawOutline(outline, color)
                    drawRect(lerp(color, Color.Black, SPINE_SHADE), size = Size(spine, size.height))
                }
            }
            .padding(start = CoverTextStart, end = 10.dp, top = 4.dp, bottom = 4.dp),
        color = Color.White,
        style = MaterialTheme.typography.labelMedium,
    )
}

/**
 * Темніший корінець обкладинки зліва — для кнопок-предметів на [GameSurface] з формою [TagShape].
 * [inset] — ширина обвідки поверхні, щоб корінець не перекривав її.
 */
fun Modifier.coverSpine(color: Color, inset: Dp = 0.dp): Modifier = drawBehind {
    val i = inset.toPx()
    drawRect(lerp(color, Color.Black, SPINE_SHADE), Offset(i, i), Size(TAG_SPINE_DP.dp.toPx(), size.height - 2 * i))
}

/** Відступ тексту від краю з корінцем. */
val CoverTextStart: Dp = (TAG_SPINE_DP + 8).dp

/** Форма обкладинки: біля корінця кути майже прямі, з відкритого боку — заокруглені. */
val CoverShape: Shape = RoundedCornerShape(topStart = 2.dp, bottomStart = 2.dp, topEnd = 5.dp, bottomEnd = 5.dp)

/** Форма ярлика-обкладинки (рюкзак, домашка, вибір предмета). */
val TagShape: Shape = RoundedCornerShape(topStart = 3.dp, bottomStart = 3.dp, topEnd = 9.dp, bottomEnd = 9.dp)

private val CoverDigit = TextStyle(fontFamily = HandFont, fontWeight = FontWeight.Bold)

/** Обкладинка вища за ширину, як зошит (≈ A5). */
private const val COVER_RATIO = 1.3f
private const val SPINE_DP = 4
private const val TAG_SPINE_DP = 4
private const val STAPLE_DP = 3.5f
private const val STAPLE_A = 0.28f
private const val STAPLE_B = 0.72f
private const val LABEL_INSET_DP = 3
private const val LABEL_TOP = 0.2f
private const val LABEL_HEIGHT = 0.46f
private const val SPINE_SHADE = 0.28f
private const val FADE = 0.72f
private const val DIGIT_PER_WIDTH = 0.68f

/** Caveat: цифри від лінії письма до ≈0,57 кегля (центр — 0,28 над нею), чорнило зміщене вправо на ≈0,1 кегля. */
private const val INK_HALF_HEIGHT = 0.28f
private const val INK_SHIFT_X = 0.1f
private val STAPLE = Color(0xFFDCE0E6)
private val LABEL = Color.White
private val FADED_GREY = Color(0xFF9EA3AC)
private val PENCIL_GREY = Color(0xFF5E6270)
