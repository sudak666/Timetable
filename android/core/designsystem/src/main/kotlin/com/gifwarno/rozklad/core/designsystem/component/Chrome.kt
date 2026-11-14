package com.gifwarno.rozklad.core.designsystem.component

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.calculateEndPadding
import androidx.compose.foundation.layout.calculateStartPadding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.platform.LocalLayoutDirection

/**
 * Скільки місця зверху й знизу займають «плаваючі» панелі оболонки (статус-бар, плашка тривоги, нижній бар).
 * Екрани не відступають від них, а додають це у contentPadding — тоді вміст пропливає під панелями,
 * а не обрізається рівною лінією.
 */
val LocalChromePadding = compositionLocalOf { PaddingValues() }

/** Ці відступи + місце під плаваючі панелі — для contentPadding списків. */
@Composable
fun PaddingValues.withChrome(): PaddingValues {
    val chrome = LocalChromePadding.current
    val dir = LocalLayoutDirection.current
    return PaddingValues(
        start = calculateStartPadding(dir),
        top = calculateTopPadding() + chrome.calculateTopPadding(),
        end = calculateEndPadding(dir),
        bottom = calculateBottomPadding() + chrome.calculateBottomPadding(),
    )
}
