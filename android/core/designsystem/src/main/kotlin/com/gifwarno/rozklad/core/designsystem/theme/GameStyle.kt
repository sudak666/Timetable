package com.gifwarno.rozklad.core.designsystem.theme

import androidx.compose.runtime.staticCompositionLocalOf

/**
 * Ігровий стиль інтерфейсу — як у вебі («✨ Звичайний», «🏆 Арена», «⛏️ Блоки»).
 * Окремо від світлої/темної теми: стиль змінює форму, шрифти й небо, тема — кольори.
 */
enum class GameStyle(val label: String) {
    CLASSIC("✨ Звичайний"),
    ARENA("🏆 Арена"),
    BLOCKS("⛏️ Блоки"),
    ;

    companion object {
        /** Збережене ім'я → стиль; невідоме чи порожнє — «Арена» (стиль Android за замовчуванням). */
        fun of(name: String?): GameStyle = entries.firstOrNull { it.name == name } ?: ARENA
    }
}

// Свідомий виняток: токени теми — класичний випадок для CompositionLocal (як MaterialTheme.colorScheme).
@Suppress("ComposeCompositionLocalUsage")
val LocalGameStyle = staticCompositionLocalOf { GameStyle.ARENA }
