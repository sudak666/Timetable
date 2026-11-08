package com.gifwarno.rozklad.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.adaptive.navigationsuite.NavigationSuiteScaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.viewmodel.navigation3.rememberViewModelStoreNavEntryDecorator
import androidx.navigation3.runtime.NavKey
import androidx.navigation3.runtime.entryProvider
import androidx.navigation3.runtime.rememberNavBackStack
import androidx.navigation3.runtime.rememberSaveableStateHolderNavEntryDecorator
import androidx.navigation3.ui.NavDisplay
import com.gifwarno.rozklad.core.designsystem.component.LoadingState
import com.gifwarno.rozklad.core.model.SessionState
import com.gifwarno.rozklad.core.model.SyncStatus
import com.gifwarno.rozklad.core.ui.SyncBanner
import com.gifwarno.rozklad.feature.auth.AuthRoute
import com.gifwarno.rozklad.feature.auth.JoinFamilyRoute
import com.gifwarno.rozklad.feature.auth.UnavailableScreen
import com.gifwarno.rozklad.feature.grades.GradesRoute
import com.gifwarno.rozklad.feature.homework.HomeworkRoute
import com.gifwarno.rozklad.feature.profile.ProfileRoute
import com.gifwarno.rozklad.feature.schedule.ScheduleEditorRoute
import com.gifwarno.rozklad.feature.today.TodayRoute
import com.gifwarno.rozklad.navigation.GradesKey
import com.gifwarno.rozklad.navigation.HomeworkKey
import com.gifwarno.rozklad.navigation.ProfileKey
import com.gifwarno.rozklad.navigation.ScheduleEditorKey
import com.gifwarno.rozklad.navigation.TodayKey
import com.gifwarno.rozklad.navigation.TopLevelDestination

/** Дії рівня застосунку — стан піднято в MainActivity, UI лише відображає (state hoisting). */
@Immutable
data class AppActions(
    val onRefresh: () -> Unit,
    val onRetrySession: () -> Unit,
    val onSignOut: () -> Unit,
    val onDismissSyncError: () -> Unit,
)

/** Корінь UI: стан сесії вирішує, що показати. */
@Composable
fun RozkladApp(session: SessionState, sync: SyncStatus, hasFamilyData: Boolean, actions: AppActions, modifier: Modifier = Modifier) {
    Surface(modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
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

            is SessionState.Ready -> MainShell(sync, hasFamilyData, actions)
        }
    }
}

@Composable
private fun MainShell(sync: SyncStatus, hasData: Boolean, actions: AppActions, modifier: Modifier = Modifier) {
    LifecycleResumeEffect(Unit) {
        actions.onRefresh()
        onPauseOrDispose { }
    }

    // Стек: [Сьогодні] або [Сьогодні, вкладка] — «назад» з будь-якої вкладки веде на «Сьогодні».
    val backStack = rememberNavBackStack(TodayKey)
    // Підсвічуємо вкладку, навіть коли поверх неї відкрито редактор.
    val current = backStack.lastOrNull { key -> TopLevelDestination.entries.any { it.key == key } }

    NavigationSuiteScaffold(
        modifier = modifier,
        navigationSuiteItems = {
            TopLevelDestination.entries.forEach { dest ->
                val selected = current == dest.key
                item(
                    selected = selected,
                    onClick = {
                        if (selected && backStack.last() == dest.key) return@item
                        while (backStack.size > 1) backStack.removeAt(backStack.lastIndex)
                        if (dest.key != TodayKey) backStack.add(dest.key)
                    },
                    icon = { Icon(if (selected) dest.selectedIcon else dest.icon, contentDescription = null) },
                    label = { Text(dest.label) },
                )
            }
        },
    ) {
        Column(Modifier.fillMaxSize().safeDrawingPadding()) {
            SyncBanner(sync, onDismissError = actions.onDismissSyncError)
            NavDisplay(
                backStack = backStack,
                onBack = { if (backStack.size > 1) backStack.removeAt(backStack.lastIndex) },
                entryDecorators = listOf(
                    rememberSaveableStateHolderNavEntryDecorator(),
                    rememberViewModelStoreNavEntryDecorator(),
                ),
                entryProvider = entryProvider<NavKey> {
                    entry<TodayKey> { TodayRoute(onEditSchedule = { backStack.add(ScheduleEditorKey) }) }
                    entry<ScheduleEditorKey> { ScheduleEditorRoute(onClose = { backStack.removeAt(backStack.lastIndex) }) }
                    entry<GradesKey> { if (hasData) GradesRoute() else LoadingState() }
                    entry<HomeworkKey> { if (hasData) HomeworkRoute() else LoadingState() }
                    entry<ProfileKey> { if (hasData) ProfileRoute() else LoadingState() }
                },
            )
        }
    }
}
