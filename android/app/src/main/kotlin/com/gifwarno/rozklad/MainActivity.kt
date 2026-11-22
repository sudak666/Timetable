package com.gifwarno.rozklad

import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import android.view.animation.AccelerateInterpolator
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.core.splashscreen.SplashScreenViewProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.gifwarno.rozklad.core.datastore.ThemeMode
import com.gifwarno.rozklad.core.designsystem.theme.RozkladTheme
import com.gifwarno.rozklad.core.model.SessionState
import com.gifwarno.rozklad.ui.AppActions
import com.gifwarno.rozklad.ui.RozkladApp
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    private val viewModel: MainActivityViewModel by viewModels()

    /** Куди перейти після натискання на сповіщення («#grades», «#hw»). */
    private val deepLink = mutableStateOf<String?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        val splash = installSplashScreen()
        super.onCreate(savedInstanceState)
        // Сплеш тримаємо, поки не відомо, чи користувач увійшов (без миготіння екрана входу).
        splash.setKeepOnScreenCondition { viewModel.sessionState.value == SessionState.Loading }
        splash.setOnExitAnimationListener(::playSplashExit)
        // До першого кадру — за системною темою (як сплеш); далі уточнює applySystemBars за стилем.
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.auto(Color.TRANSPARENT, Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.auto(Color.TRANSPARENT, Color.TRANSPARENT),
        )
        deepLink.value = intent?.getStringExtra(EXTRA_URL)
        handleOAuthRedirect(intent)
        val actions = AppActions(
            onRefresh = viewModel::refresh,
            onRetrySession = viewModel::retrySession,
            onSignOut = viewModel::signOut,
            onDismissSyncError = viewModel::dismissSyncError,
            onDeepLinkHandled = { deepLink.value = null },
            onRefreshAlert = viewModel::refreshAlert,
        )
        setContent {
            val theme by viewModel.themeMode.collectAsStateWithLifecycle()
            val session by viewModel.sessionState.collectAsStateWithLifecycle()
            val sync by viewModel.sync.collectAsStateWithLifecycle()
            val hasData by viewModel.hasFamilyData.collectAsStateWithLifecycle()
            val alert by viewModel.alertBanner.collectAsStateWithLifecycle()
            val accent by viewModel.accent.collectAsStateWithLifecycle()
            val paper by viewModel.paper.collectAsStateWithLifecycle()
            val gradesBadge by viewModel.gradesBadge.collectAsStateWithLifecycle()
            val dark = when (theme) {
                ThemeMode.SYSTEM -> isSystemInDarkTheme()
                ThemeMode.LIGHT -> false
                ThemeMode.DARK -> true
            }
            // Світлий папір — темні значки годинника й батареї; дошка — світлі.
            val lightBars = !dark
            DisposableEffect(lightBars) {
                applySystemBars(lightBars)
                onDispose { }
            }
            RozkladTheme(darkTheme = dark, accent = accent?.let { androidx.compose.ui.graphics.Color(it) }, paper = paper) {
                RozkladApp(
                    session = session,
                    sync = sync,
                    hasFamilyData = hasData,
                    actions = actions,
                    alert = alert,
                    deepLink = deepLink.value,
                    gradesBadge = gradesBadge,
                )
            }
        }
    }

    /**
     * Лого «злітає» назустріч (трохи збільшується), сплеш тане — і під ним уже сторінка зошита (уночі — дошка) того самого кольору.
     * Системний масштаб анімацій ViewPropertyAnimator враховує сам: вимкнені анімації — сплеш зникає одразу.
     */
    private fun playSplashExit(provider: SplashScreenViewProvider) {
        // Без значка (напр., запуск не з лаунчера) iconView може бракувати — тоді лише тане тло.
        runCatching {
            provider.iconView.animate()
                .scaleX(SPLASH_EXIT_SCALE)
                .scaleY(SPLASH_EXIT_SCALE)
                .setDuration(SPLASH_EXIT_MS)
                .setInterpolator(AccelerateInterpolator())
                .start()
        }
        provider.view.animate()
            .alpha(0f)
            .setDuration(SPLASH_EXIT_MS)
            .setInterpolator(AccelerateInterpolator())
            .withEndAction(provider::remove)
            .start()
    }

    private fun applySystemBars(light: Boolean) {
        val bars = if (light) {
            SystemBarStyle.light(Color.TRANSPARENT, Color.TRANSPARENT)
        } else {
            SystemBarStyle.dark(Color.TRANSPARENT)
        }
        enableEdgeToEdge(statusBarStyle = bars, navigationBarStyle = bars)
    }

    /** Застосунок уже відкритий, а людина натиснула сповіщення. */
    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        intent.getStringExtra(EXTRA_URL)?.let { deepLink.value = it }
        handleOAuthRedirect(intent)
    }

    /** Браузер повернув нас після входу Google — завершуємо вхід (обмін коду на сесію). */
    private fun handleOAuthRedirect(intent: Intent?) {
        val uri = intent?.data ?: return
        if (uri.scheme == packageName && uri.host == "login-callback") viewModel.completeGoogleSignIn(uri.toString())
    }

    private companion object {
        /** Той самий ключ, що й у сповіщеннях (core:push → PushNotifier.EXTRA_URL і data.url з сервера). */
        const val EXTRA_URL = "url"

        const val SPLASH_EXIT_MS = 260L
        const val SPLASH_EXIT_SCALE = 1.35f
    }
}
