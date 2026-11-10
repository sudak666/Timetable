package com.gifwarno.rozklad.feature.profile.alerts

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.gifwarno.rozklad.core.designsystem.component.ButtonShape
import com.gifwarno.rozklad.core.designsystem.component.GameSurface
import com.gifwarno.rozklad.core.designsystem.component.GameTopBar
import com.gifwarno.rozklad.core.designsystem.component.LoadingState
import com.gifwarno.rozklad.core.designsystem.theme.RozkladTheme
import com.gifwarno.rozklad.core.model.AlertLocation
import com.gifwarno.rozklad.core.model.AlertLocationKind

/** Дії екрана — щоб сам екран не знав про ViewModel (state hoisting). */
@Immutable
internal data class RegionActions(
    val openOblast: (AlertLocation) -> Unit,
    val backToOblasts: () -> Unit,
    val choose: (Int) -> Unit,
    val onQuery: (String) -> Unit,
    val retry: () -> Unit,
)

internal fun AlertRegionViewModel.actions() = RegionActions(
    openOblast = ::openOblast,
    backToOblasts = ::loadOblasts,
    choose = ::choose,
    onQuery = ::onQuery,
    retry = ::loadOblasts,
)

@Composable
fun AlertRegionRoute(onClose: () -> Unit, modifier: Modifier = Modifier, viewModel: AlertRegionViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    LaunchedEffect(state.done) { if (state.done) onClose() }
    AlertRegionScreen(state, remember(viewModel) { viewModel.actions() }, onClose, modifier)
}

@Composable
internal fun AlertRegionScreen(state: AlertRegionUiState, actions: RegionActions, onClose: () -> Unit, modifier: Modifier = Modifier) {
    val inOblast = state.step as? RegionStep.InOblast
    val back = if (inOblast != null) actions.backToOblasts else onClose
    BackHandler(onBack = back)

    Column(modifier.fillMaxSize()) {
        GameTopBar(title = inOblast?.oblast?.name ?: "📍 Місце для тривог", onBack = back)
        GameSurface(
            Modifier.fillMaxSize().padding(start = 12.dp, end = 12.dp, top = 8.dp),
            shape = RoundedCornerShape(24.dp),
            depth = 5.dp,
        ) {
            Column(Modifier.fillMaxSize()) {
                if (state.saving) LinearProgressIndicator(Modifier.fillMaxWidth())
                state.error?.let { msg ->
                    ListItem(
                        headlineContent = { Text(msg, color = MaterialTheme.colorScheme.error) },
                        trailingContent = if (state.step is RegionStep.Loading) {
                            { TextButton(onClick = actions.retry) { Text("Ще раз") } }
                        } else {
                            null
                        },
                        colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                    )
                }
                when (val step = state.step) {
                    RegionStep.Loading -> if (state.error == null) LoadingState()
                    is RegionStep.Oblasts -> OblastList(step.items, actions.openOblast)
                    is RegionStep.InOblast -> OblastContents(step, state.query, actions)
                }
            }
        }
    }
}

private val rowColors @Composable get() = ListItemDefaults.colors(containerColor = Color.Transparent)

@Composable
private fun OblastList(items: List<AlertLocation>, onOpen: (AlertLocation) -> Unit) {
    LazyColumn {
        item(key = "hint") {
            Text(
                "Обери область, а далі — район чи громаду, де навчаються діти. Точніше місце — менше зайвих тривог.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(16.dp),
            )
        }
        items(items, key = { it.uid }) { loc ->
            ListItem(
                headlineContent = { Text(loc.name, fontWeight = FontWeight.Bold) },
                supportingContent = if (loc.kind == AlertLocationKind.CITY) {
                    { Text("Тривоги оголошують окремо від області") }
                } else {
                    null
                },
                trailingContent = { Text("›", style = MaterialTheme.typography.titleLarge, color = RozkladTheme.colors.muted) },
                colors = rowColors,
                modifier = Modifier.clickable { onOpen(loc) },
            )
        }
    }
}

@Composable
private fun OblastContents(step: RegionStep.InOblast, query: String, actions: RegionActions) {
    val items = remember(step.children, query) { buildRegionItems(step.children, query) }
    LazyColumn {
        item(key = "search") {
            OutlinedTextField(
                value = query,
                onValueChange = actions.onQuery,
                leadingIcon = { Icon(Icons.Outlined.Search, contentDescription = null) },
                placeholder = { Text("Пошук громади чи району") },
                singleLine = true,
                shape = ButtonShape,
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
            )
        }
        if (query.isBlank()) {
            item(key = "whole") {
                GameSurface(
                    Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
                    shape = ButtonShape,
                    color = RozkladTheme.colors.soft,
                    depth = 3.dp,
                    onClick = { actions.choose(step.oblast.uid) },
                ) {
                    Column(Modifier.padding(14.dp)) {
                        Text("🗺️ Уся область", style = MaterialTheme.typography.titleMedium)
                        Text(
                            "Тривога — коли вона в усій області, «частково» — коли лише в деяких громадах",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        }
        if (items.isEmpty()) {
            item(key = "empty") { Text("Нічого не знайдено", modifier = Modifier.padding(16.dp)) }
        }
        items(items, key = { it.location.uid }) { item ->
            when (item) {
                is RegionItem.Raion -> ListItem(
                    headlineContent = { Text(item.location.name, fontWeight = FontWeight.ExtraBold) },
                    supportingContent = { Text("Увесь район") },
                    colors = rowColors,
                    modifier = Modifier.clickable { actions.choose(item.location.uid) },
                )

                is RegionItem.Hromada -> ListItem(
                    headlineContent = { Text(shortName(item.location.name)) },
                    colors = rowColors,
                    modifier = Modifier.padding(start = 16.dp).clickable { actions.choose(item.location.uid) },
                )
            }
        }
    }
}
