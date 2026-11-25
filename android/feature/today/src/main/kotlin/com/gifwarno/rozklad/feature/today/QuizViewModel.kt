package com.gifwarno.rozklad.feature.today

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.gifwarno.rozklad.core.data.repository.ActiveFamily
import com.gifwarno.rozklad.core.data.repository.FamilyRepository
import com.gifwarno.rozklad.core.domain.Rewards
import com.gifwarno.rozklad.core.model.QuizQuestion
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** Стан вікторини: питання й правильні відповіді — лише на сервері; він же перевіряє відповідь і платить. */
sealed interface QuizUiState {
    data object Loading : QuizUiState

    /** Немає інтернету чи сервер не відповів. */
    data object Error : QuizUiState

    data class Question(
        val number: Int,
        val total: Int,
        val question: QuizQuestion,
        /** Що натиснули (до відповіді сервера — ще без кольору). */
        val picked: String?,
        /** Правильна відповідь — з'являється після перевірки сервером. */
        val right: String?,
        /** Не вдалося надіслати відповідь — можна натиснути ще раз. */
        val sendFailed: Boolean = false,
    ) : QuizUiState

    data class Done(val correct: Int, val total: Int, val earned: Int) : QuizUiState
}

/** Підказка над питанням: скільки дає правильна відповідь (дитині) і чи ще платимо сьогодні. */
data class QuizPriceNote(val isChild: Boolean, val price: Int, val left: Int?) {
    val text: String?
        get() = when {
            price <= 0 -> null
            !isChild -> "Гроші за правильні відповіді отримує дитина: +$price ₴, до ${Rewards.QUIZ_PAID_PER_DAY} на день"
            left == 0 -> "✅ Сьогодні за вікторину вже заплачено — грай для інтересу"
            else -> "Правильна відповідь — +$price ₴ (до ${Rewards.QUIZ_PAID_PER_DAY} на день)"
        }
}

@HiltViewModel
class QuizViewModel @Inject constructor(private val active: ActiveFamily, private val families: FamilyRepository) : ViewModel() {
    private val _state = MutableStateFlow<QuizUiState>(QuizUiState.Loading)
    val state: StateFlow<QuizUiState> = _state.asStateFlow()

    private val left = MutableStateFlow<Int?>(null)

    val priceNote: StateFlow<QuizPriceNote?> = combine(active.context, left) { ctx, l ->
        ctx?.let { QuizPriceNote(isChild = !it.isParent, price = it.data.settings.prices.quiz, left = l) }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), null)

    private var round: List<QuizQuestion> = emptyList()
    private var index = 0
    private var correct = 0
    private var earned = 0

    init {
        start()
    }

    /** Новий раунд з сервера. */
    fun start() {
        _state.value = QuizUiState.Loading
        viewModelScope.launch {
            families.quizRound()
                .onSuccess { qs ->
                    round = qs
                    index = 0
                    correct = 0
                    earned = 0
                    _state.value = if (qs.isEmpty()) QuizUiState.Error else question(picked = null, right = null)
                }
                .onFailure { _state.value = QuizUiState.Error }
        }
    }

    fun answer(option: String) {
        val s = _state.value as? QuizUiState.Question ?: return
        if (s.picked != null) return
        _state.value = s.copy(picked = option, sendFailed = false)
        viewModelScope.launch {
            val familyId = active.context.filterNotNull().first().familyId
            families.quizAnswer(familyId, s.question.id, option)
                .onSuccess { a ->
                    if (a.correct) correct++
                    earned += a.paid
                    left.value = a.left
                    _state.value = s.copy(picked = option, right = a.right, sendFailed = false)
                    delay(NEXT_DELAY_MS)
                    index++
                    _state.value = if (index >= round.size) QuizUiState.Done(correct, round.size, earned) else question(null, null)
                }
                .onFailure { _state.update { (it as? QuizUiState.Question)?.copy(picked = null, sendFailed = true) ?: it } }
        }
    }

    private fun question(picked: String?, right: String?) = QuizUiState.Question(index + 1, round.size, round[index], picked, right)

    private companion object {
        const val NEXT_DELAY_MS = 900L
        const val STOP_TIMEOUT_MS = 5_000L
    }
}
