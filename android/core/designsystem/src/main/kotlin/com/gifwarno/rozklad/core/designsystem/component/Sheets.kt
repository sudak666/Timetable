package com.gifwarno.rozklad.core.designsystem.component

import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.SheetState
import androidx.compose.material3.SheetValue
import androidx.compose.material3.rememberBottomSheetState
import androidx.compose.runtime.Composable

/**
 * Стани нижньої шторки, що відкривається одразу повністю — без проміжного «наполовину».
 * Так само поводився колишній `rememberModalBottomSheetState(skipPartiallyExpanded = true)`.
 *
 * Це ДОЗВОЛЕНІ стани, а не пропущені: без [SheetValue.Expanded] шторка падає при створенні
 * («Expanded must be one of the enabled values») — так впала версія 18 (0.2.16).
 */
@OptIn(ExperimentalMaterial3Api::class)
val FullSheetValues: Set<SheetValue> = setOf(SheetValue.Hidden, SheetValue.Expanded)

/** Стан шторки для `ModalBottomSheet`: відкривається одразу повністю. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun rememberFullSheetState(): SheetState = rememberBottomSheetState(
    initialValue = SheetValue.Hidden,
    enabledValues = FullSheetValues,
)
