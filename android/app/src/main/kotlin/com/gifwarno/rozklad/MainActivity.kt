package com.gifwarno.rozklad

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.getValue
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

    override fun onCreate(savedInstanceState: Bundle?) {
        val splash = installSplashScreen()
        super.onCreate(savedInstanceState)
        // Сплеш тримаємо, поки не відомо, чи користувач увійшов (без миготіння екрана входу).
        splash.setKeepOnScreenCondition { viewModel.sessionState.value == SessionState.Loading }
        enableEdgeToEdge()
        val actions = AppActions(
            onRefresh = viewModel::refresh,
            onRetrySession = viewModel::retrySession,
            onSignOut = viewModel::signOut,
            onDismissSyncError = viewModel::dismissSyncError,
        )
        setContent {
            val theme by viewModel.themeMode.collectAsStateWithLifecycle()
            val session by viewModel.sessionState.collectAsStateWithLifecycle()
            val sync by viewModel.sync.collectAsStateWithLifecycle()
            val hasData by viewModel.hasFamilyData.collectAsStateWithLifecycle()
            val dark = when (theme) {
                ThemeMode.SYSTEM -> isSystemInDarkTheme()
                ThemeMode.LIGHT -> false
                ThemeMode.DARK -> true
            }
            RozkladTheme(darkTheme = dark) {
                RozkladApp(session = session, sync = sync, hasFamilyData = hasData, actions = actions)
            }
        }
    }
}
