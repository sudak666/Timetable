package com.gifwarno.rozklad.feature.today

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.gifwarno.rozklad.core.data.repository.ActiveFamily
import com.gifwarno.rozklad.core.data.repository.FamilyRepository
import com.gifwarno.rozklad.core.model.QuizTodayEntry
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update

/** Картка «Вікторина» для батьків: як вибрана дитина сьогодні відповідала (school_quiz_today). */
sealed interface ParentQuizUiState {
    /** Дитина (чи батьки без дітей) — показуємо саму гру. */
    data object NotParent : ParentQuizUiState

    data object Loading : ParentQuizUiState

    /** Немає інтернету чи сервер не відповів. */
    data class Error(val childName: String) : ParentQuizUiState

    data class Ready(val childName: String, val entries: List<QuizTodayEntry>) : ParentQuizUiState {
        val correct: Int get() = entries.count { it.correct }
        val paid: Int get() = entries.sumOf { it.paid.coerceAtLeast(0) }

        /** «Сашко сьогодні: 3 відповіді, 2 правильно — +40 ₴». */
        val summary: String
            get() = "$childName сьогодні: ${entries.size} ${answersWord(entries.size)}, $correct правильно" +
                if (paid > 0) " — +$paid ₴" else ""
    }
}

/** «відповідь / відповіді / відповідей» для числа [n]. */
internal fun answersWord(n: Int): String {
    val d = n % 10
    val h = n % 100
    return when {
        d == 1 && h != 11 -> "відповідь"
        d in 2..4 && h !in 12..14 -> "відповіді"
        else -> "відповідей"
    }
}

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class ParentQuizViewModel @Inject constructor(active: ActiveFamily, private val families: FamilyRepository) : ViewModel() {
    private val reload = MutableStateFlow(0)

    /** Вибрана дитина (id, ім'я) — лише для батьків; синхронізація сім'ї не перезавантажує відповіді. */
    private val kid = active.context
        .map { ctx -> ctx?.takeIf { it.isParent }?.child?.let { it.userId to it.name } }
        .distinctUntilChanged()

    /**
     * Відповіді завантажуються, коли картку видно (після 5 с у фоні — знову, тож після сповіщення
     * батьки бачать свіже), а також кнопкою «Оновити» і при зміні дитини.
     */
    val state: StateFlow<ParentQuizUiState> = combine(kid, reload) { k, _ -> k }
        .flatMapLatest { k ->
            if (k == null) {
                flowOf<ParentQuizUiState>(ParentQuizUiState.NotParent)
            } else {
                flow<ParentQuizUiState> {
                    emit(ParentQuizUiState.Loading)
                    emit(
                        families.quizToday(k.first).fold(
                            onSuccess = { ParentQuizUiState.Ready(k.second, it) },
                            onFailure = { ParentQuizUiState.Error(k.second) },
                        ),
                    )
                }
            }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), ParentQuizUiState.Loading)

    fun refresh() = reload.update { it + 1 }

    private companion object {
        const val STOP_TIMEOUT_MS = 5_000L
    }
}
