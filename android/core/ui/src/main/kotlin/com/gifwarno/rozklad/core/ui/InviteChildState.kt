package com.gifwarno.rozklad.core.ui

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import com.gifwarno.rozklad.core.designsystem.component.EmptyState

/**
 * Порожній стан «у сім'ї ще немає дитини» — однаковий на всіх вкладках: код і кнопка запрошення,
 * щоб батькам не треба було шукати код у «Профілі». Емодзі — під вкладку.
 */
@Composable
fun InviteChildState(emoji: String, isParent: Boolean, inviteCode: String, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    if (isParent) {
        EmptyState(
            emoji = emoji,
            title = "Запросіть дитину",
            message = "Дитина встановлює застосунок або відкриває сайт, входить і вводить код $inviteCode.",
            actionLabel = "📨 Надіслати запрошення",
            onAction = { shareInvite(context, inviteCode) },
            modifier = modifier,
        )
    } else {
        EmptyState(emoji = emoji, title = "Немає даних", message = "Попроси батьків додати тебе в сім’ю.", modifier = modifier)
    }
}
