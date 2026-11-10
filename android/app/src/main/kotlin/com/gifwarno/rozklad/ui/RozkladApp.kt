package com.gifwarno.rozklad.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import androidx.lifecycle.viewmodel.navigation3.rememberViewModelStoreNavEntryDecorator
import androidx.navigation3.runtime.NavKey
import androidx.navigation3.runtime.entryProvider
import androidx.navigation3.runtime.rememberNavBackStack
import androidx.navigation3.runtime.rememberSaveableStateHolderNavEntryDecorator
import androidx.navigation3.ui.NavDisplay
import com.gifwarno.rozklad.core.designsystem.component.ArenaBackground
import com.gifwarno.rozklad.core.designsystem.component.GameNavBar
import com.gifwarno.rozklad.core.designsystem.component.GameNavItem
import com.gifwarno.rozklad.core.designsystem.component.LoadingState
import com.gifwarno.rozklad.core.model.SessionState
import com.gifwarno.rozklad.core.model.SyncStatus
import com.gifwarno.rozklad.core.ui.AlertBanner
import com.gifwarno.rozklad.core.ui.AlertBannerText
import com.gifwarno.rozklad.core.ui.SyncBanner
import com.gifwarno.rozklad.feature.auth.AuthRoute
import com.gifwarno.rozklad.feature.auth.JoinFamilyRoute
import com.gifwarno.rozklad.feature.auth.UnavailableScreen
import com.gifwarno.rozklad.feature.grades.GradesRoute
import com.gifwarno.rozklad.feature.homework.HomeworkRoute
import com.gifwarno.rozklad.feature.profile.ProfileRoute
import com.gifwarno.rozklad.feature.profile.alerts.AlertRegionRoute
import com.gifwarno.rozklad.feature.schedule.ScheduleEditorRoute
import com.gifwarno.rozklad.feature.today.TodayRoute
import com.gifwarno.rozklad.navigation.AlertRegionKey
import com.gifwarno.rozklad.navigation.GradesKey
import com.gifwarno.rozklad.navigation.HomeworkKey
import com.gifwarno.rozklad.navigation.ProfileKey
import com.gifwarno.rozklad.navigation.ScheduleEditorKey
import com.gifwarno.rozklad.navigation.TodayKey
import com.gifwarno.rozklad.navigation.TopLevelDestination
import kotlinx.coroutines.delay

/** Дії рівня застосунку — стан піднято в MainActivity, UI лише відображає (state hoisting). */
@Immutable
data class AppActions(
    val onRefresh: () -> Unit,
    val onRetrySession: () -> Unit,
    val onSignOut: () -> Unit,
    val onDismissSyncError: () -> Unit,
    val onDeepLinkHandled: () -> Unit,
    val onRefreshAlert: () -> Unit,
)

/** Як часто перевіряти стан тривоги, поки застосунок на екрані (сервер оновлює раз на хвилину). */
private const val ALERT_POLL_MS = 60_000L

/** Корінь UI: стан сесії вирішує, що показати. */
@Composable
fun RozkladApp(
    session: SessionState,
    sync: SyncStatus,
    hasFamilyData: Boolean,
    actions: AppActions,
    modifier: Modifier = Modifier,
    alert: AlertBannerText? = null,
    deepLink: String? = null,
) {
    ArenaBackground(modifier.fillMaxSize()) {
        when (session) {
            SessionState.Loading -> LoadingState()

            SessionState.SignedOut -> AuthRoute(Modifier.safeDrawingPadding())

            is SessionState.NoFamily -> JoinFamilyRoute(Modifier.safeDrawingPadding())

            is SessionState.Unavailable -> UnavailableScreen(
                message = session.message,
                onRetry = actions.onRetrySession,
                onSignOut = actions.onSignOut,
                modifier = Modifier.safeDrawingPadding(),
            )

            is SessionState.Ready -> MainShell(sync, hasFamilyData, actions, alert, deepLink)
        }
    }
}

@Composable
private fun MainShell(
    sync: SyncStatus,
    hasData: Boolean,
    actions: AppActions,
    alert: AlertBannerText?,
    deepLink: String?,
    modifier: Modifier = Modifier,
) {
    LifecycleResumeEffect(Unit) {
        actions.onRefresh()
        onPauseOrDispose { }
    }

    // Стан тривоги — щохвилини, лише поки застосунок на екрані (у фоні — push).
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    val refreshAlert by rememberUpdatedState(actions.onRefreshAlert)
    LaunchedEffect(lifecycle) {
        lifecycle.repeatOnLifecycle(Lifecycle.State.RESUMED) {
            while (true) {
                delay(ALERT_POLL_MS)
                refreshAlert()
            }
        }
    }

    // Стек: [Сьогодні] або [Сьогодні, вкладка] — «назад» з будь-якої вкладки веде на «Сьогодні».
    val backStack = rememberNavBackStack(TodayKey)
    // Підсвічуємо вкладку, навіть коли поверх неї відкрито редактор.
    val current = backStack.lastOrNull { key -> TopLevelDestination.entries.any { it.key == key } }

    // Натиснули сповіщення → відкриваємо потрібну вкладку поверх «Сьогодні».
    LaunchedEffect(deepLink) {
        val target = when {
            deepLink == null -> null
            deepLink.startsWith("#hw") -> HomeworkKey
            deepLink.startsWith("#grades") -> GradesKey
            else -> null
        }
        if (target != null) {
            while (backStack.size > 1) backStack.removeAt(backStack.lastIndex)
            backStack.add(target)
        }
        if (deepLink != null) actions.onDeepLinkHandled()
    }

    Column(modifier.fillMaxSize().safeDrawingPadding()) {
        AlertBanner(alert)
        SyncBanner(sync, onDismissError = actions.onDismissSyncError)
        NavDisplay(
            backStack = backStack,
            onBack = { if (backStack.size > 1) backStack.removeAt(backStack.lastIndex) },
            modifier = Modifier.weight(1f),
            entryDecorators = listOf(
                rememberSaveableStateHolderNavEntryDecorator(),
                rememberViewModelStoreNavEntryDecorator(),
            ),
            entryProvider = entryProvider<NavKey> {
                entry<TodayKey> {
                    TodayRoute(
                        onEditSchedule = { backStack.add(ScheduleEditorKey) },
                        onOpenPlayer = { backStack.add(GradesKey) },
                    )
                }
                entry<ScheduleEditorKey> { ScheduleEditorRoute(onClose = { backStack.removeAt(backStack.lastIndex) }) }
                entry<GradesKey> { if (hasData) GradesRoute() else LoadingState() }
                entry<HomeworkKey> { if (hasData) HomeworkRoute() else LoadingState() }
                entry<ProfileKey> {
                    if (hasData) ProfileRoute(onPickAlertRegion = { backStack.add(AlertRegionKey) }) else LoadingState()
                }
                entry<AlertRegionKey> { AlertRegionRoute(onClose = { backStack.removeAt(backStack.lastIndex) }) }
            },
        )
        Box(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp), contentAlignment = Alignment.Center) {
            GameNavBar(Modifier.widthIn(max = 560.dp)) {
                TopLevelDestination.entries.forEach { dest ->
                    val selected = current == dest.key
                    GameNavItem(
                        selected = selected,
                        onClick = {
                            if (selected && backStack.last() == dest.key) return@GameNavItem
                            while (backStack.size > 1) backStack.removeAt(backStack.lastIndex)
                            if (dest.key != TodayKey) backStack.add(dest.key)
                        },
                        icon = if (selected) dest.selectedIcon else dest.icon,
                        label = dest.label,
                    )
                }
            }
        }
    }
}
