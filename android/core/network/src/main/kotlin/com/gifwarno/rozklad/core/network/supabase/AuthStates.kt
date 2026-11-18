package com.gifwarno.rozklad.core.network.supabase

import com.gifwarno.rozklad.core.network.model.NetworkAuthState
import com.gifwarno.rozklad.core.network.model.NetworkUser
import io.github.jan.supabase.auth.status.SessionStatus
import io.github.jan.supabase.auth.user.UserSession
import kotlin.time.Duration
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.withTimeoutOrNull

/**
 * Статус Supabase → стан входу застосунку.
 *
 * «Ще невідомо» (Initializing) і збій оновлення токена (RefreshFailure, найчастіше — офлайн чи сон телефона)
 * не означають «вийшов»: Supabase тоді вже не віддає поточного користувача, тож беремо останнього, кого бачили,
 * або сесію, збережену на пристрої. Інакше віджет і фонові задачі хибно бачать «завантаження» чи «увійди».
 * «Не увійшли» — лише коли Supabase так і каже (NotAuthenticated) або сесії на пристрої немає.
 */
internal fun authStates(
    statuses: Flow<SessionStatus>,
    storedUser: suspend () -> NetworkUser?,
    userOf: (UserSession) -> NetworkUser?,
): Flow<NetworkAuthState> = flow {
    var known: NetworkUser? = null
    statuses.collect { status ->
        val state = when (status) {
            is SessionStatus.Authenticated -> {
                known = userOf(status.session) ?: known
                known?.let(NetworkAuthState::SignedIn) ?: NetworkAuthState.SignedOut
            }

            is SessionStatus.NotAuthenticated -> {
                known = null
                NetworkAuthState.SignedOut
            }

            is SessionStatus.Initializing, is SessionStatus.RefreshFailure -> {
                known = known ?: storedUser()
                when {
                    known != null -> NetworkAuthState.SignedIn(checkNotNull(known))
                    status is SessionStatus.Initializing -> NetworkAuthState.Initializing
                    else -> NetworkAuthState.SignedOut
                }
            }
        }
        emit(state)
    }
}.distinctUntilChanged()

/**
 * Чекає справжньої сесії перед запитом до даних.
 *
 * [authStates] пускає людину в акаунт ще до того, як Supabase підняв сесію зі сховища чи оновив токен.
 * Запит у цей момент іде без токена, лише з публічним ключем, і RLS мовчки віддає порожньо: застосунок вирішив би,
 * що сім'ї немає («Майже готово!»), стер би кеш, а оновлення рядків «не зачепили б нічого» й загубились би.
 *
 * @return true — сесія є; false — вийшли або не дочекались (офлайн, токен так і не оновився).
 */
internal suspend fun awaitSignedIn(statuses: Flow<SessionStatus>, timeout: Duration): Boolean = withTimeoutOrNull(timeout) {
    statuses.first { it is SessionStatus.Authenticated || it is SessionStatus.NotAuthenticated }
} is SessionStatus.Authenticated
