package com.gifwarno.rozklad

import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
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
        // Тло «арени» темне в обох темах — значки системних панелей завжди світлі.
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(Color.TRANSPARENT),
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
            val style by viewModel.gameStyle.collectAsStateWithLifecycle()
            val dark = when (theme) {
                ThemeMode.SYSTEM -> isSystemInDarkTheme()
                ThemeMode.LIGHT -> false
                ThemeMode.DARK -> true
            }
            RozkladTheme(darkTheme = dark, accent = accent?.let { androidx.compose.ui.graphics.Color(it) }, style = style) {
                RozkladApp(
                    session = session,
                    sync = sync,
                    hasFamilyData = hasData,
                    actions = actions,
                    alert = alert,
                    deepLink = deepLink.value,
                )
            }
        }
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
    }
}
