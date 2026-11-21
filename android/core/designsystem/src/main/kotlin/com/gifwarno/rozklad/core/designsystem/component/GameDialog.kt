package com.gifwarno.rozklad.core.designsystem.component

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ProvideTextStyle
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.gifwarno.rozklad.core.designsystem.theme.HandFont
import com.gifwarno.rozklad.core.designsystem.theme.RozkladTheme

/**
 * Вікно-«аркуш» «Щоденника» замість стандартного Material-діалогу (аудит 21.11.2026, Н5):
 * сторінка з червоними полями (уночі — дошка), заголовок від руки, кнопки праворуч унизу.
 * Параметри — як у Material `AlertDialog`, тож заміна не змінює логіки вікон.
 */
@Composable
fun GameDialog(
    onDismissRequest: () -> Unit,
    confirmButton: @Composable () -> Unit,
    modifier: Modifier = Modifier,
    dismissButton: (@Composable () -> Unit)? = null,
    title: (@Composable () -> Unit)? = null,
    text: (@Composable () -> Unit)? = null,
    properties: DialogProperties = DialogProperties(),
) {
    val c = RozkladTheme.colors
    val ink = if (c.dark) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.primary
    Dialog(
        onDismissRequest = onDismissRequest,
        // Ширину задаємо самі (як Material AlertDialog): 280–560 dp з полями від країв екрана.
        properties = DialogProperties(
            dismissOnBackPress = properties.dismissOnBackPress,
            dismissOnClickOutside = properties.dismissOnClickOutside,
            usePlatformDefaultWidth = false,
        ),
    ) {
        GameSurface(
            modifier = modifier.padding(horizontal = 24.dp).widthIn(min = 280.dp, max = 560.dp),
            shape = RoundedCornerShape(24.dp),
            depth = 6.dp,
        ) {
            Column(
                Modifier
                    .fillMaxWidth()
                    // Удень — червона лінія полів, як на сторінці зошита.
                    .drawBehind {
                        if (!c.dark) {
                            val x = MARGIN_DP.dp.toPx()
                            drawLine(MARGIN_RED, Offset(x, 0f), Offset(x, size.height), strokeWidth = 2.dp.toPx())
                        }
                    }
                    .padding(start = 28.dp, end = 24.dp, top = 24.dp, bottom = 16.dp),
            ) {
                title?.let {
                    Box(Modifier.padding(bottom = 12.dp).semantics { heading() }) {
                        CompositionLocalProvider(LocalContentColor provides ink) { ProvideTextStyle(DialogTitle, it) }
                    }
                }
                text?.let {
                    // Довгий вміст (вибір часу, предмета) не виштовхує кнопки за край — займає лише те, що лишилось.
                    Box(Modifier.weight(1f, fill = false)) {
                        CompositionLocalProvider(LocalContentColor provides MaterialTheme.colorScheme.onSurfaceVariant) {
                            ProvideTextStyle(MaterialTheme.typography.bodyMedium, it)
                        }
                    }
                }
                Row(
                    Modifier.align(Alignment.End).padding(top = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    dismissButton?.invoke()
                    confirmButton()
                }
            }
        }
    }
}

private val DialogTitle = TextStyle(fontFamily = HandFont, fontWeight = FontWeight.Bold, fontSize = 30.sp, lineHeight = 32.sp)
private const val MARGIN_DP = 12
private val MARGIN_RED = Color(0xFFC8321F).copy(alpha = 0.35f)
