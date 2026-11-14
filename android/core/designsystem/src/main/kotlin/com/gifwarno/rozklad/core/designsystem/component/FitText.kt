package com.gifwarno.rozklad.core.designsystem.component

import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.text.TextAutoSize
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.LocalTextStyle
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.takeOrElse
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.isSpecified
import androidx.compose.ui.unit.sp

/**
 * Короткий підпис, який за великого системного шрифту зменшується, щоб влізти, — а не обрізається
 * («Сьогод») і не рветься посеред слова («тиждень / ь»). Рядки — лише явні «\n» (аудит 14.11.2026, В3).
 */
@Composable
fun FitText(
    text: String,
    modifier: Modifier = Modifier,
    style: TextStyle = LocalTextStyle.current,
    color: Color = Color.Unspecified,
    textAlign: TextAlign = TextAlign.Unspecified,
    maxLines: Int = 1,
    minFontSize: TextUnit = MIN_FONT,
) {
    val tint = color.takeOrElse { style.color.takeOrElse { LocalContentColor.current } }
    val max = if (style.fontSize.isSpecified) style.fontSize else LocalTextStyle.current.fontSize
    BasicText(
        text,
        modifier,
        style = style.copy(color = tint, textAlign = textAlign),
        maxLines = maxLines,
        softWrap = false,
        autoSize = TextAutoSize.StepBased(minFontSize = minFontSize, maxFontSize = max),
    )
}

private val MIN_FONT = 9.sp
