package com.gifwarno.rozklad.core.designsystem.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import com.gifwarno.rozklad.core.designsystem.R

/** Той самий шрифт, що й у вебі (Nunito): округлий, дружній, повна українська кирилиця. */
val Nunito = FontFamily(
    Font(R.font.nunito_400, FontWeight.Normal),
    Font(R.font.nunito_600, FontWeight.SemiBold),
    Font(R.font.nunito_700, FontWeight.Bold),
    Font(R.font.nunito_800, FontWeight.ExtraBold),
    Font(R.font.nunito_900, FontWeight.Black),
)

/** «Ігровий» шрифт для великих цифр і заголовків — як стиль «Арена» у вебі. */
val GameFont = FontFamily(Font(R.font.russo_one, FontWeight.Normal))

private val base = Typography()

private fun TextStyle.n(weight: FontWeight) = copy(fontFamily = Nunito, fontWeight = weight)

/** Розміри — системні (масштабуються з налаштуваннями доступності), шрифт і товщина — як у вебі. */
internal val RozkladTypography = Typography(
    displayLarge = base.displayLarge.copy(fontFamily = GameFont),
    displayMedium = base.displayMedium.copy(fontFamily = GameFont),
    displaySmall = base.displaySmall.copy(fontFamily = GameFont),
    headlineLarge = base.headlineLarge.n(FontWeight.Black),
    headlineMedium = base.headlineMedium.n(FontWeight.Black),
    headlineSmall = base.headlineSmall.n(FontWeight.ExtraBold),
    titleLarge = base.titleLarge.n(FontWeight.ExtraBold),
    titleMedium = base.titleMedium.n(FontWeight.ExtraBold),
    titleSmall = base.titleSmall.n(FontWeight.ExtraBold),
    bodyLarge = base.bodyLarge.n(FontWeight.SemiBold),
    bodyMedium = base.bodyMedium.n(FontWeight.SemiBold),
    bodySmall = base.bodySmall.n(FontWeight.SemiBold),
    labelLarge = base.labelLarge.n(FontWeight.ExtraBold),
    labelMedium = base.labelMedium.n(FontWeight.ExtraBold),
    labelSmall = base.labelSmall.n(FontWeight.ExtraBold),
)

/** Цифри однакової ширини — таймер не «тремтить». */
val TimerTextStyle = TextStyle(
    fontFamily = GameFont,
    fontSize = 56.sp,
    lineHeight = 60.sp,
    fontFeatureSettings = "tnum",
)

/** Великі «ігрові» заголовки (назва уроку на головній картці). */
val GameTitleStyle = TextStyle(fontFamily = GameFont, fontSize = 26.sp, lineHeight = 32.sp)
