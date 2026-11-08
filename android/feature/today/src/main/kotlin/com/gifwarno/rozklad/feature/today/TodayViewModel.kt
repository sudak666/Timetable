package com.gifwarno.rozklad.feature.today

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.gifwarno.rozklad.core.common.ticks
import com.gifwarno.rozklad.core.domain.SchoolCalendar
import dagger.hilt.android.lifecycle.HiltViewModel
import java.time.Clock
import java.time.LocalDateTime
import javax.inject.Inject
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn

@HiltViewModel
class TodayViewModel @Inject constructor(clock: Clock) : ViewModel() {
    private val calendar = SchoolCalendar()

    /** Тікає щосекунди лише поки екран видимий (WhileSubscribed) — батарея не страждає. */
    val uiState: StateFlow<TodayUiState> = clock.ticks()
        .map { buildTodayState(it, calendar) }
        .distinctUntilChanged()
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS),
            initialValue = buildTodayState(LocalDateTime.now(clock), calendar),
        )

    private companion object {
        const val STOP_TIMEOUT_MS = 5_000L
    }
}
