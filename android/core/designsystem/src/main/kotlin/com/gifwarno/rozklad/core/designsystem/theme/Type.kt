package com.gifwarno.rozklad.core.designsystem.theme

import androidx.compose.material3.Typography
import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
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

/** Шрифт стилю «Арена» (як у вебі). */
internal val RussoOne = FontFamily(Font(R.font.russo_one, FontWeight.Normal))

/** Піксельний шрифт стилю «Блоки» (як у вебі); відсутні символи Android бере з системного шрифту. */
internal val PixelFont = FontFamily(Font(R.font.pixelify_500, FontWeight.Medium), Font(R.font.pixelify_700, FontWeight.Bold))

/** «Ігровий» шрифт великих цифр і заголовків — залежить від обраного стилю. */
val GameFont: FontFamily
    @Composable @ReadOnlyComposable
    get() = when (LocalGameStyle.current) {
        GameStyle.ARENA -> RussoOne
        GameStyle.BLOCKS -> PixelFont
        GameStyle.CLASSIC -> Nunito
    }

/** Товщина «ігрового» шрифту: у Nunito й піксельного — жирна, у Russo One — єдина. */
private val gameWeight: FontWeight
    @Composable @ReadOnlyComposable
    get() = when (LocalGameStyle.current) {
        GameStyle.ARENA -> FontWeight.Normal
        GameStyle.BLOCKS -> FontWeight.Bold
        GameStyle.CLASSIC -> FontWeight.Black
    }

private val base = Typography()

private fun TextStyle.n(weight: FontWeight) = copy(fontFamily = Nunito, fontWeight = weight)

/** Розміри — системні (масштабуються з налаштуваннями доступності), шрифт і товщина — як у вебі. */
internal val RozkladTypography = Typography(
    displayLarge = base.displayLarge.copy(fontFamily = RussoOne),
    displayMedium = base.displayMedium.copy(fontFamily = RussoOne),
    displaySmall = base.displaySmall.copy(fontFamily = RussoOne),
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
val TimerTextStyle: TextStyle
    @Composable @ReadOnlyComposable
    get() = TextStyle(fontFamily = GameFont, fontWeight = gameWeight, fontSize = 56.sp, lineHeight = 60.sp, fontFeatureSettings = "tnum")

/** Великі «ігрові» заголовки (назва уроку на головній картці). */
val GameTitleStyle: TextStyle
    @Composable @ReadOnlyComposable
    get() = TextStyle(fontFamily = GameFont, fontWeight = gameWeight, fontSize = 26.sp, lineHeight = 32.sp)
