package com.gifwarno.rozklad.core.designsystem.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp

/** Фірмові кольори — ті самі, що у вебі (#6c5ce7). */
private object Brand {
    val Violet = Color(0xFF6C5CE7)
    val VioletDark = Color(0xFFC9BFFF)
    val Teal = Color(0xFF00866B)
    val TealDark = Color(0xFF6FDBBE)
    val Amber = Color(0xFF8A5C00)
    val AmberDark = Color(0xFFFFB95C)
}

private val LightColors = lightColorScheme(
    primary = Brand.Violet,
    onPrimary = Color.White,
    primaryContainer = Color(0xFFE6DEFF),
    onPrimaryContainer = Color(0xFF1D0F66),
    secondary = Brand.Teal,
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFB8F2DE),
    onSecondaryContainer = Color(0xFF002118),
    tertiary = Brand.Amber,
    onTertiary = Color.White,
    tertiaryContainer = Color(0xFFFFDDB3),
    onTertiaryContainer = Color(0xFF2B1700),
    background = Color(0xFFFCF8FF),
    surface = Color(0xFFFCF8FF),
)

private val DarkColors = darkColorScheme(
    primary = Brand.VioletDark,
    onPrimary = Color(0xFF2F1E85),
    primaryContainer = Color(0xFF4636B5),
    onPrimaryContainer = Color(0xFFE6DEFF),
    secondary = Brand.TealDark,
    onSecondary = Color(0xFF00382C),
    secondaryContainer = Color(0xFF005141),
    onSecondaryContainer = Color(0xFFB8F2DE),
    tertiary = Brand.AmberDark,
    onTertiary = Color(0xFF462A00),
    tertiaryContainer = Color(0xFF643F00),
    onTertiaryContainer = Color(0xFFFFDDB3),
)

/** Семантичні кольори поза Material: оцінки, гроші. */
@Immutable
data class RozkladColors(
    val positive: Color,
    val negative: Color,
    val gradeExcellent: Color,
    val gradeGood: Color,
    val gradeFair: Color,
    val gradeLow: Color,
    val gradePass: Color,
)

private val LightExtra = RozkladColors(
    positive = Color(0xFF00866B),
    negative = Color(0xFFC62828),
    gradeExcellent = Color(0xFF00866B),
    gradeGood = Color(0xFF0869B5),
    gradeFair = Color(0xFF8A5C00),
    gradeLow = Color(0xFFC62828),
    gradePass = Color(0xFF5B6472),
)

private val DarkExtra = RozkladColors(
    positive = Color(0xFF6FDBBE),
    negative = Color(0xFFFFB4AB),
    gradeExcellent = Color(0xFF6FDBBE),
    gradeGood = Color(0xFF9ECAFF),
    gradeFair = Color(0xFFFFB95C),
    gradeLow = Color(0xFFFFB4AB),
    gradePass = Color(0xFFC4C6D0),
)

// Свідомий виняток: токени теми — класичний випадок для CompositionLocal (як MaterialTheme.colorScheme).
@Suppress("ComposeCompositionLocalUsage")
val LocalRozkladColors = staticCompositionLocalOf { LightExtra }

private val RozkladShapes = Shapes(
    small = RoundedCornerShape(8.dp),
    medium = RoundedCornerShape(16.dp),
    large = RoundedCornerShape(24.dp),
)

@Composable
fun RozkladTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    /** Material You (Android 12+). Вимкнено за замовчуванням, щоб зберегти впізнаваний стиль. */
    dynamicColor: Boolean = false,
    content: @Composable () -> Unit,
) {
    val colors: ColorScheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            val ctx = LocalContext.current
            if (darkTheme) dynamicDarkColorScheme(ctx) else dynamicLightColorScheme(ctx)
        }

        darkTheme -> DarkColors

        else -> LightColors
    }
    CompositionLocalProvider(LocalRozkladColors provides if (darkTheme) DarkExtra else LightExtra) {
        MaterialTheme(colorScheme = colors, typography = RozkladTypography, shapes = RozkladShapes, content = content)
    }
}

object RozkladTheme {
    val colors: RozkladColors
        @Composable @ReadOnlyComposable
        get() = LocalRozkladColors.current
}
