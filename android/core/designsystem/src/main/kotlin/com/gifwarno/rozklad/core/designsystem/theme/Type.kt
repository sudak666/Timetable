package com.gifwarno.rozklad.core.designsystem.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

private val base = Typography()

/** Системний шрифт (повна кирилиця, підтримка великого шрифту в налаштуваннях доступності). */
internal val RozkladTypography = Typography(
    displayLarge = base.displayLarge.copy(fontWeight = FontWeight.Bold),
    displayMedium = base.displayMedium.copy(fontWeight = FontWeight.Bold),
    headlineMedium = base.headlineMedium.copy(fontWeight = FontWeight.SemiBold),
    titleLarge = base.titleLarge.copy(fontWeight = FontWeight.SemiBold),
    titleMedium = base.titleMedium.copy(fontWeight = FontWeight.SemiBold),
)

/** Цифри однакової ширини — таймер не «тремтить». */
val TimerTextStyle = TextStyle(
    fontSize = 56.sp,
    fontWeight = FontWeight.Bold,
    fontFeatureSettings = "tnum",
)
