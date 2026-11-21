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

/** «Від руки» — заголовки й лого «Щоденника» (Caveat; лише жирний, латиниця й кирилиця). */
val HandFont = FontFamily(Font(R.font.caveat_700, FontWeight.Bold))

/**
 * Caveat при тому самому розмірі виглядає дрібнішим (низькі малі літери) — збільшуємо, щоб заголовок
 * «від руки» мав ту саму вагу на екрані, що й звичайний.
 */
const val HAND_SCALE = 1.3f

private val base = Typography()

private fun TextStyle.n(weight: FontWeight) = copy(fontFamily = Nunito, fontWeight = weight)

/** Розміри — системні (масштабуються з налаштуваннями доступності), шрифт і товщина — як у вебі. */
internal val RozkladTypography = Typography(
    displayLarge = base.displayLarge.n(FontWeight.Black),
    displayMedium = base.displayMedium.n(FontWeight.Black),
    displaySmall = base.displaySmall.n(FontWeight.Black),
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
val TimerTextStyle =
    TextStyle(fontFamily = Nunito, fontWeight = FontWeight.Black, fontSize = 56.sp, lineHeight = 60.sp, fontFeatureSettings = "tnum")

/** Великі заголовки «від руки» (назва уроку на головній картці, дні, розділи); розмір підганяє TitleText. */
val GameTitleStyle = TextStyle(fontFamily = HandFont, fontWeight = FontWeight.Bold, fontSize = 26.sp, lineHeight = 32.sp)
