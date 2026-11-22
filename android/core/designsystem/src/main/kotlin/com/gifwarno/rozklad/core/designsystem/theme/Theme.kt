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
import com.gifwarno.rozklad.core.model.Paper

/**
 * Фірмові кольори «Щоденника»: зошит у клітинку, синє чорнило, червона ручка вчителя, жовтий маркер;
 * уночі — шкільна дошка й крейда. Свої, а не з готових наборів (аудит 21.11.2026, Н8).
 */
object Brand {
    val Paper = Color(0xFFFBF8F0)
    val Ink = Color(0xFF1F3FA6)
    val InkDeep = Color(0xFF14236B)
    val Pencil = Color(0xFF23262F)
    val RedPen = Color(0xFFC8321F)
    val Marker = Color(0xFFFFE14A)

    val Board = Color(0xFF16221D)
    val BoardCard = Color(0xFF20302A)
    val Chalk = Color(0xFFEDEBE3)
    val ChalkBlue = Color(0xFF9DBBFF)
    val ChalkRed = Color(0xFFFF8A7A)
    val ChalkYellow = Color(0xFFF2D25C)

    /** Крафтовий папір (обкладинка-чернетка): сірий текст 5,3:1, чорнило 6,1:1. */
    val Kraft = Color(0xFFE4D2AE)
}

private val LightColors = lightColorScheme(
    primary = Brand.Ink,
    onPrimary = Color.White,
    primaryContainer = Color(0xFFDCE3FF),
    onPrimaryContainer = Color(0xFF0E1F5C),
    secondary = Color(0xFF00705A),
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFC4F2E4),
    onSecondaryContainer = Color(0xFF00201A),
    tertiary = Color(0xFF8A5C00),
    onTertiary = Color.White,
    tertiaryContainer = Color(0xFFFFF1B8),
    onTertiaryContainer = Color(0xFF2B1700),
    error = Color(0xFFC0392B),
    errorContainer = Color(0xFFFDECE8),
    onErrorContainer = Color(0xFFA4301A),
    background = Brand.Paper,
    onBackground = Brand.Pencil,
    surface = Color.White,
    onSurface = Brand.Pencil,
    surfaceVariant = Color(0xFFF3EEE2),
    onSurfaceVariant = Color(0xFF4C5163),
    outline = Color(0xFFB3AC98),
    outlineVariant = Color(0xFFE6DFCF),
    surfaceContainerLowest = Color.White,
    surfaceContainerLow = Color.White,
    surfaceContainer = Color(0xFFF3EEE2),
    surfaceContainerHigh = Color(0xFFEDE7D8),
    surfaceContainerHighest = Color(0xFFE6DFCF),
)

/** «Дошка» — темна тема «Щоденника»: зелено-чорне тло, текст крейдою. */
private val BoardColors = darkColorScheme(
    primary = Brand.ChalkBlue,
    onPrimary = Color(0xFF10192B),
    primaryContainer = Color(0xFF263F75),
    onPrimaryContainer = Color(0xFFDCE3FF),
    secondary = Color(0xFF4FD1A5),
    onSecondary = Color(0xFF00382C),
    secondaryContainer = Color(0xFF005141),
    onSecondaryContainer = Color(0xFFC4F2E4),
    tertiary = Brand.ChalkYellow,
    onTertiary = Color(0xFF2B2200),
    tertiaryContainer = Color(0xFF4A3E10),
    onTertiaryContainer = Color(0xFFFFF1B8),
    error = Brand.ChalkRed,
    errorContainer = Color(0xFF5C1A14),
    onErrorContainer = Color(0xFFFFDAD6),
    background = Brand.Board,
    onBackground = Brand.Chalk,
    surface = Brand.BoardCard,
    onSurface = Brand.Chalk,
    surfaceVariant = Color(0xFF1B2A24),
    onSurfaceVariant = Color(0xFFB7C2BB),
    outline = Color(0xFF52665D),
    outlineVariant = Color(0xFF2B3D36),
    surfaceContainerLowest = Brand.Board,
    surfaceContainerLow = Brand.BoardCard,
    surfaceContainer = Color(0xFF1B2A24),
    surfaceContainerHigh = Color(0xFF263B33),
    surfaceContainerHighest = Color(0xFF2B3D36),
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
    /** Темний колір тіней і тексту на жовтому маркері. */
    val ink: Color,
    /** Акцент для заливок з білим текстом (чорнило; людина може обрати свій колір). */
    val accent: Color,
    /** Червона ручка вчителя: оцінки, нагороди, найважливіше. */
    val accentEnd: Color,
    /** Підсвітка поточного (маркер). */
    val glow: Color,
    /** Тло рядків, перерв, плиток (--brk). */
    val soft: Color,
    val card: Color,
    val line: Color,
    val muted: Color,
    /** Тло сторінки за картками: папір чи дошка. */
    val page: Color,
    /** Жовтий маркер (рівні, прогрес, «зараз»). */
    val gold: Color,
    val goldEnd: Color,
    val dark: Boolean,
    /** Папір сторінки (нагорода за рівні); уночі той самий візерунок — крейдою на дошці. */
    val paper: Paper = Paper.GRID,
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
    ink = Color(0xFF1B1C33),
    accent = Brand.Ink,
    accentEnd = Brand.RedPen,
    glow = Brand.Marker.copy(alpha = 0.6f),
    soft = Color(0xFFF3EEE2),
    card = Color.White,
    line = Color(0xFFE6DFCF),
    muted = Color(0xFF4C5163),
    page = Brand.Paper,
    gold = Brand.Marker,
    goldEnd = Color(0xFFF5C400),
    dark = false,
)

/** «Дошка»: крейда на темно-зеленому. */
private val BoardExtra = RozkladColors(
    positive = Color(0xFF4FD1A5),
    onPositive = Color(0xFF00382C),
    negative = Brand.ChalkRed,
    onNegative = Color(0xFF3B1F1A),
    gradeExcellent = Color(0xFF00866B),
    gradeGood = Color(0xFF0869B5),
    gradeFair = Color(0xFFB35900),
    gradeLow = Color(0xFFC62828),
    gradePass = Color(0xFF5B6472),
    ink = Color.Black,
    accent = Color(0xFF3554C2),
    accentEnd = Brand.ChalkRed,
    glow = Brand.ChalkYellow.copy(alpha = 0.1f),
    soft = Color(0xFF1B2A24),
    card = Brand.BoardCard,
    line = Color(0xFF2B3D36),
    muted = Color(0xFFB7C2BB),
    page = Brand.Board,
    gold = Brand.ChalkYellow,
    goldEnd = Color(0xFFE0B83A),
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
    /** Обраний людиною колір (відкривається з рівнями); null — фірмове чорнило. */
    accent: Color? = null,
    /** Папір сторінки (відкривається з рівнями). */
    paper: Paper = Paper.GRID,
    content: @Composable () -> Unit,
) {
    val colors: ColorScheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            val ctx = LocalContext.current
            if (darkTheme) dynamicDarkColorScheme(ctx) else dynamicLightColorScheme(ctx)
        }

        darkTheme -> BoardColors

        else -> LightColors
    }
    // Свій колір людини (відкривається з рівнями) замінює чорнило; маркер «зараз» лишається жовтим.
    val extra = (if (darkTheme) BoardExtra else LightExtra).let { base -> accent?.let { base.copy(accent = it) } ?: base }.let {
        // Крафт удень міняє й колір сторінки (смуги під шапкою й над вкладками — теж); уночі дошка лишається дошкою.
        it.copy(paper = paper, page = if (!darkTheme && paper == Paper.KRAFT) Brand.Kraft else it.page)
    }
    // У темній темі текст акцентного кольору має бути світлішим, щоб читався на темних картках.
    val scheme = accent?.let { colors.copy(primary = if (darkTheme) lerp(it, Color.White, DARK_PRIMARY_LIFT) else it) } ?: colors
    CompositionLocalProvider(LocalRozkladColors provides extra) {
        MaterialTheme(colorScheme = scheme, typography = RozkladTypography, shapes = RozkladShapes, content = content)
    }
}

private const val DARK_PRIMARY_LIFT = 0.45f

object RozkladTheme {
    val colors: RozkladColors
        @Composable @ReadOnlyComposable
        get() = LocalRozkladColors.current
}
