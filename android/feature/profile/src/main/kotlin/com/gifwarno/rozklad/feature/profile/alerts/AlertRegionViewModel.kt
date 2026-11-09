package com.gifwarno.rozklad.feature.profile.alerts

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.gifwarno.rozklad.core.data.repository.ActiveFamily
import com.gifwarno.rozklad.core.data.repository.FamilyRepository
import com.gifwarno.rozklad.core.model.AlertLocation
import com.gifwarno.rozklad.core.model.AlertLocationKind
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** Два кроки: спершу область (або Київ), потім — уся область, район чи громада. */
sealed interface RegionStep {
    data object Loading : RegionStep

    data class Oblasts(val items: List<AlertLocation>) : RegionStep

    data class InOblast(val oblast: AlertLocation, val children: List<AlertLocation>) : RegionStep
}

data class AlertRegionUiState(
    val step: RegionStep = RegionStep.Loading,
    val query: String = "",
    /** Іде збереження вибору. */
    val saving: Boolean = false,
    val error: String? = null,
    /** Вибір збережено — екран закривається. */
    val done: Boolean = false,
)

private const val OFFLINE = "Не вдалося завантажити — перевір інтернет"

@HiltViewModel
class AlertRegionViewModel @Inject constructor(private val families: FamilyRepository, private val active: ActiveFamily) : ViewModel() {
    private val _state = MutableStateFlow(AlertRegionUiState())
    val state: StateFlow<AlertRegionUiState> = _state.asStateFlow()

    /** Області кешуємо, щоб «назад» з області не ходив у мережу вдруге. */
    private var oblasts: List<AlertLocation> = emptyList()

    init {
        loadOblasts()
    }

    fun loadOblasts() {
        if (oblasts.isNotEmpty()) {
            _state.update { it.copy(step = RegionStep.Oblasts(oblasts), query = "", error = null) }
            return
        }
        _state.update { it.copy(step = RegionStep.Loading, error = null) }
        viewModelScope.launch {
            families.alertLocations(null)
                .onSuccess { list ->
                    // Київ — першим: його обирають найчастіше, а за абеткою він губиться серед «м.».
                    oblasts = list.sortedWith(
                        compareBy<AlertLocation> { it.kind != AlertLocationKind.CITY }.thenBy(ukrainianOrder) { it.name },
                    )
                    _state.update { it.copy(step = RegionStep.Oblasts(oblasts)) }
                }
                .onFailure { _state.update { it.copy(error = OFFLINE) } }
        }
    }

    /** Місто зі спецстатусом (Київ) — одразу вибір; область — показуємо її райони й громади. */
    fun openOblast(oblast: AlertLocation) {
        if (oblast.kind == AlertLocationKind.CITY) return choose(oblast.uid)
        _state.update { it.copy(step = RegionStep.Loading, query = "", error = null) }
        viewModelScope.launch {
            families.alertLocations(oblast.uid)
                .onSuccess { list -> _state.update { it.copy(step = RegionStep.InOblast(oblast, list)) } }
                .onFailure { _state.update { it.copy(step = RegionStep.Oblasts(oblasts), error = OFFLINE) } }
        }
    }

    fun onQuery(q: String) = _state.update { it.copy(query = q.take(MAX_QUERY)) }

    fun choose(uid: Int) {
        if (_state.value.saving) return
        _state.update { it.copy(saving = true, error = null) }
        viewModelScope.launch {
            val familyId = active.context.filterNotNull().first().familyId
            families.setAlertRegion(familyId, uid)
                .onSuccess { _state.update { it.copy(saving = false, done = true) } }
                .onFailure { e -> _state.update { it.copy(saving = false, error = e.message ?: OFFLINE) } }
        }
    }

    fun dismissError() = _state.update { it.copy(error = null) }

    private companion object {
        const val MAX_QUERY = 60
    }
}
