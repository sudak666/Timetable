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
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp

/** Токени — ті самі, що у вебі (src/styles.css: --accent, --bg, --card, --line …). */
private object Web {
    val Accent = Color(0xFF6C5CE7)
    val Pink = Color(0xFFFD79A8)
    val Ink = Color(0xFF1B1C33)

    val Bg = Color(0xFFF3F4FB)
    val Card = Color.White
    val Text = Color(0xFF1B1C33)
    val Muted = Color(0xFF5F6384)
    val Line = Color(0xFFE4E6F3)
    val Brk = Color(0xFFF0F1FA)

    val BgDark = Color(0xFF0F1024)
    val CardDark = Color(0xFF1A1B36)
    val TextDark = Color(0xFFECEEFE)
    val MutedDark = Color(0xFFA3A6CC)
    val LineDark = Color(0xFF2A2C4F)
    val BrkDark = Color(0xFF15162D)
}

private val LightColors = lightColorScheme(
    primary = Web.Accent,
    onPrimary = Color.White,
    primaryContainer = Color(0xFFE6E1FF),
    onPrimaryContainer = Color(0xFF22126E),
    secondary = Color(0xFF00705A),
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFC4F2E4),
    onSecondaryContainer = Color(0xFF00201A),
    tertiary = Color(0xFF8A5C00),
    onTertiary = Color.White,
    tertiaryContainer = Color(0xFFFFE3B8),
    onTertiaryContainer = Color(0xFF2B1700),
    error = Color(0xFFC0392B),
    errorContainer = Color(0xFFFFF1E6),
    onErrorContainer = Color(0xFFA4301A),
    background = Web.Bg,
    onBackground = Web.Text,
    surface = Web.Card,
    onSurface = Web.Text,
    surfaceVariant = Web.Brk,
    onSurfaceVariant = Web.Muted,
    outline = Color(0xFFB9BCD6),
    outlineVariant = Web.Line,
    surfaceContainerLowest = Web.Card,
    surfaceContainerLow = Web.Card,
    surfaceContainer = Web.Brk,
    surfaceContainerHigh = Color(0xFFEAEBF7),
    surfaceContainerHighest = Color(0xFFE4E6F3),
)

private val DarkColors = darkColorScheme(
    primary = Color(0xFFA29BFE),
    onPrimary = Web.Ink,
    primaryContainer = Color(0xFF3D2F9E),
    onPrimaryContainer = Color(0xFFE6E1FF),
    secondary = Color(0xFF4FD1A5),
    onSecondary = Color(0xFF00382C),
    secondaryContainer = Color(0xFF005141),
    onSecondaryContainer = Color(0xFFC4F2E4),
    tertiary = Color(0xFFFFB95C),
    onTertiary = Color(0xFF462A00),
    tertiaryContainer = Color(0xFF643F00),
    onTertiaryContainer = Color(0xFFFFE3B8),
    error = Color(0xFFFF8A80),
    // Чистий темно-червоний (без коричневого відтінку) і світліший текст: контраст ≈ 10:1.
    errorContainer = Color(0xFF6E1318),
    onErrorContainer = Color(0xFFFFDAD6),
    background = Web.BgDark,
    onBackground = Web.TextDark,
    surface = Web.CardDark,
    onSurface = Web.TextDark,
    surfaceVariant = Web.BrkDark,
    onSurfaceVariant = Web.MutedDark,
    outline = Color(0xFF4A4D7A),
    outlineVariant = Web.LineDark,
    surfaceContainerLowest = Web.BgDark,
    surfaceContainerLow = Web.CardDark,
    surfaceContainer = Web.BrkDark,
    surfaceContainerHigh = Color(0xFF22244A),
    surfaceContainerHighest = Color(0xFF2A2C4F),
)

/** Семантичні й «ігрові» кольори поза Material. */
@Immutable
data class RozkladColors(
    val positive: Color,
    /** Текст на заливці [positive]: у темній темі заливка світла, тож текст темний (≥ 4,5:1, WCAG). */
    val onPositive: Color,
    val negative: Color,
    /** Текст на заливці [negative] — так само, як [onPositive]. */
    val onNegative: Color,
    val gradeExcellent: Color,
    val gradeGood: Color,
    val gradeFair: Color,
    val gradePass: Color,
    val gradeLow: Color,
    /** Обведення й «тверда» тінь карток і кнопок (ігровий стиль). */
    val ink: Color,
    /** Фірмовий акцент для заливок з білим текстом (у темній темі теж #6c5ce7, як у вебі). */
    val accent: Color,
    val accentEnd: Color,
    val glow: Color,
    /** Тло рядків, перерв, плиток (--brk). */
    val soft: Color,
    val card: Color,
    val line: Color,
    val muted: Color,
    /** Небо «арени» за картками: верх → низ. */
    val skyTop: Color,
    val skyBottom: Color,
    val gold: Color,
    val goldEnd: Color,
    val dark: Boolean,
)

private val LightExtra = RozkladColors(
    positive = Color(0xFF00705A),
    onPositive = Color.White,
    negative = Color(0xFFC0392B),
    onNegative = Color.White,
    gradeExcellent = Color(0xFF00866B),
    gradeGood = Color(0xFF0869B5),
    gradeFair = Color(0xFFB35900),
    gradeLow = Color(0xFFC62828),
    gradePass = Color(0xFF5B6472),
    ink = Web.Ink,
    accent = Web.Accent,
    accentEnd = Web.Pink,
    glow = Web.Accent.copy(alpha = 0.22f),
    soft = Web.Brk,
    card = Web.Card,
    line = Web.Line,
    muted = Web.Muted,
    skyTop = Color(0xFF3A1F8F),
    skyBottom = Color(0xFF7B4FD8),
    gold = Color(0xFFFDCB6E),
    goldEnd = Color(0xFFE17055),
    dark = false,
)

private val DarkExtra = RozkladColors(
    positive = Color(0xFF4FD1A5),
    onPositive = Color(0xFF00382C),
    negative = Color(0xFFFF8A80),
    onNegative = Color(0xFF3B1F1A),
    gradeExcellent = Color(0xFF00866B),
    gradeGood = Color(0xFF0869B5),
    gradeFair = Color(0xFFB35900),
    gradeLow = Color(0xFFC62828),
    gradePass = Color(0xFF5B6472),
    ink = Color.Black,
    accent = Web.Accent,
    accentEnd = Web.Pink,
    glow = Web.Accent.copy(alpha = 0.45f),
    soft = Web.BrkDark,
    card = Web.CardDark,
    line = Web.LineDark,
    muted = Web.MutedDark,
    skyTop = Color(0xFF0B0820),
    skyBottom = Color(0xFF2A1866),
    gold = Color(0xFFFDCB6E),
    goldEnd = Color(0xFFE17055),
    dark = true,
)

// Свідомий виняток: токени теми — класичний випадок для CompositionLocal (як MaterialTheme.colorScheme).
@Suppress("ComposeCompositionLocalUsage")
val LocalRozkladColors = staticCompositionLocalOf { LightExtra }

/** Радіуси як у вебі: 12 (кнопки), 20 (картки), 24 (вікна). */
private val RozkladShapes = Shapes(
    extraSmall = RoundedCornerShape(8.dp),
    small = RoundedCornerShape(12.dp),
    medium = RoundedCornerShape(16.dp),
    large = RoundedCornerShape(20.dp),
    extraLarge = RoundedCornerShape(24.dp),
)

@Composable
fun RozkladTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    /** Material You (Android 12+). Вимкнено за замовчуванням, щоб зберегти впізнаваний стиль. */
    dynamicColor: Boolean = false,
    /** Обраний людиною колір (відкривається з рівнями); null — фірмовий фіолетовий. */
    accent: Color? = null,
    /** Ігровий стиль: форма, шрифти, небо. */
    style: GameStyle = GameStyle.ARENA,
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
    val baseExtra = if (darkTheme) DarkExtra else LightExtra
    val accented =
        accent?.let { baseExtra.copy(accent = it, glow = it.copy(alpha = if (darkTheme) DARK_GLOW else LIGHT_GLOW)) } ?: baseExtra
    // «Блоки» — денне блакитне небо (уночі — темно-синє), як у вебі.
    val extra = when {
        style != GameStyle.BLOCKS -> accented
        darkTheme -> accented.copy(skyTop = Color(0xFF1B2A4A), skyBottom = Color(0xFF0F1024))
        else -> accented.copy(skyTop = Color(0xFF4FA9EC), skyBottom = Color(0xFFA9DEFF))
    }
    // У темній темі текст акцентного кольору має бути світлішим, щоб читався на темних картках.
    val scheme = accent?.let { colors.copy(primary = if (darkTheme) lerp(it, Color.White, DARK_PRIMARY_LIFT) else it) } ?: colors
    CompositionLocalProvider(LocalRozkladColors provides extra, LocalGameStyle provides style) {
        MaterialTheme(colorScheme = scheme, typography = RozkladTypography, shapes = RozkladShapes, content = content)
    }
}

private const val LIGHT_GLOW = 0.22f
private const val DARK_GLOW = 0.45f
private const val DARK_PRIMARY_LIFT = 0.45f

object RozkladTheme {
    val colors: RozkladColors
        @Composable @ReadOnlyComposable
        get() = LocalRozkladColors.current
}
