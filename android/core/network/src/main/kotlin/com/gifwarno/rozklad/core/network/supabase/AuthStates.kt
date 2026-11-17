package com.gifwarno.rozklad.core.network.supabase

import com.gifwarno.rozklad.core.network.model.NetworkAuthState
import com.gifwarno.rozklad.core.network.model.NetworkUser
import io.github.jan.supabase.auth.status.SessionStatus
import io.github.jan.supabase.auth.user.UserSession
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flow

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
