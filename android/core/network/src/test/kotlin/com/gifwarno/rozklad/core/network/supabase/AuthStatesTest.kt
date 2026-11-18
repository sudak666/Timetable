package com.gifwarno.rozklad.core.network.supabase

import com.gifwarno.rozklad.core.network.model.NetworkAuthState
import com.gifwarno.rozklad.core.network.model.NetworkUser
import io.github.jan.supabase.auth.status.RefreshFailureCause
import io.github.jan.supabase.auth.status.SessionStatus
import io.github.jan.supabase.auth.user.UserSession
import kotlin.time.Duration.Companion.seconds
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
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

    // Запит до даних (сім'я, знімок, записи) — лише після справжньої сесії, а не за збереженим користувачем.

    @Test
    fun `сесія вже є — запит іде одразу`() = runTest {
        assertTrue(awaitSignedIn(MutableStateFlow<SessionStatus>(authenticated), 20.seconds))
    }

    @Test
    fun `сесія ще піднімається зі сховища — запит чекає її, а не йде без токена`() = runTest {
        val status = MutableStateFlow<SessionStatus>(SessionStatus.Initializing)
        val ready = async { awaitSignedIn(status, 20.seconds) }
        delay(1.seconds)
        assertFalse(ready.isCompleted)
        status.value = authenticated
        assertTrue(ready.await())
    }

    @Test
    fun `токен не оновився і потім оновився — дочекались`() = runTest {
        val status = MutableStateFlow<SessionStatus>(offline)
        val ready = async { awaitSignedIn(status, 20.seconds) }
        delay(5.seconds)
        status.value = authenticated
        assertTrue(ready.await())
    }

    @Test
    fun `офлайн і сесії так і немає — запиту не буде`() = runTest {
        assertFalse(awaitSignedIn(MutableStateFlow<SessionStatus>(offline), 20.seconds))
        assertFalse(awaitSignedIn(MutableStateFlow<SessionStatus>(SessionStatus.Initializing), 20.seconds))
    }

    @Test
    fun `вийшли — запиту не буде`() = runTest {
        assertFalse(awaitSignedIn(MutableStateFlow<SessionStatus>(SessionStatus.NotAuthenticated(isSignOut = true)), 20.seconds))
    }
}
