package com.gifwarno.rozklad.core.network.supabase

import com.gifwarno.rozklad.core.network.model.NetworkAuthState
import com.gifwarno.rozklad.core.network.model.NetworkUser
import io.github.jan.supabase.auth.status.RefreshFailureCause
import io.github.jan.supabase.auth.status.SessionStatus
import io.github.jan.supabase.auth.user.UserSession
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

class AuthStatesTest {
    private val user = NetworkUser("u1", "a@b.c", "Сашко")
    private val session = UserSession(accessToken = "a", refreshToken = "r", expiresIn = 3600, tokenType = "bearer")
    private val authenticated = SessionStatus.Authenticated(session)
    private val offline = SessionStatus.RefreshFailure(RefreshFailureCause.NetworkError(java.io.IOException("offline")))

    private fun collect(vararg statuses: SessionStatus, stored: NetworkUser? = null): List<NetworkAuthState> {
        var out = emptyList<NetworkAuthState>()
        runTest { out = authStates(flowOf(*statuses), storedUser = { stored }, userOf = { user }).toList() }
        return out
    }

    @Test
    fun `застосунок згорнули — Supabase знову Initializing, але людина лишається в акаунті`() {
        assertEquals(
            listOf(NetworkAuthState.SignedIn(user)),
            collect(authenticated, SessionStatus.Initializing, authenticated),
        )
    }

    @Test
    fun `холодний старт без збереженої сесії — чекаємо, а не «увійди»`() {
        assertEquals(listOf(NetworkAuthState.Initializing), collect(SessionStatus.Initializing))
    }

    @Test
    fun `холодний старт зі збереженою сесією — одразу в акаунті`() {
        assertEquals(listOf(NetworkAuthState.SignedIn(user)), collect(SessionStatus.Initializing, stored = user))
    }

    @Test
    fun `офлайн не вдалося оновити токен — лишаємо в акаунті з локальними даними`() {
        assertEquals(listOf(NetworkAuthState.SignedIn(user)), collect(authenticated, offline))
        assertEquals(listOf(NetworkAuthState.SignedIn(user)), collect(SessionStatus.Initializing, offline, stored = user))
    }

    @Test
    fun `збій оновлення і сесії на пристрої немає — не увійшли`() {
        assertEquals(listOf(NetworkAuthState.Initializing, NetworkAuthState.SignedOut), collect(SessionStatus.Initializing, offline))
    }

    @Test
    fun `вихід забуває користувача`() {
        assertEquals(
            listOf(NetworkAuthState.SignedIn(user), NetworkAuthState.SignedOut, NetworkAuthState.Initializing),
            collect(authenticated, SessionStatus.NotAuthenticated(isSignOut = true), SessionStatus.Initializing),
        )
    }
}
