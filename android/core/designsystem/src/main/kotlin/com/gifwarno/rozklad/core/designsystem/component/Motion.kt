package com.gifwarno.rozklad.core.designsystem.component

import android.provider.Settings
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext

/**
 * Чи можна показувати декоративні анімації (салют, «плавання» емодзі, живе лого).
 * Людина вимкнула анімації в налаштуваннях доступності (масштаб 0) — показуємо кінцевий кадр без руху.
 * Звичайні переходи Compose і так зважають на цей масштаб; ця перевірка — для нескінченних і довгих ефектів.
 */
@Composable
fun rememberAnimationsEnabled(): Boolean {
    val context = LocalContext.current
    return remember(context) {
        Settings.Global.getFloat(context.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f) > 0f
    }
}
