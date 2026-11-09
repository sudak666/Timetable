package com.gifwarno.rozklad.core.data.repository

import com.gifwarno.rozklad.core.common.ApplicationScope
import com.gifwarno.rozklad.core.common.Dispatcher
import com.gifwarno.rozklad.core.common.RozkladDispatchers
import com.gifwarno.rozklad.core.data.UserMessage
import com.gifwarno.rozklad.core.data.mapper.roleOf
import com.gifwarno.rozklad.core.data.sync.SyncManager
import com.gifwarno.rozklad.core.database.RozkladDatabase
import com.gifwarno.rozklad.core.datastore.CachedMembership
import com.gifwarno.rozklad.core.datastore.UserPreferencesDataSource
import com.gifwarno.rozklad.core.model.SessionState
import com.gifwarno.rozklad.core.model.UserAccount
import com.gifwarno.rozklad.core.network.RozkladNetworkDataSource
import com.gifwarno.rozklad.core.network.model.NetworkAuthState
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@OptIn(ExperimentalCoroutinesApi::class)
@Singleton
internal class DefaultSessionRepository @Inject constructor(
    private val network: RozkladNetworkDataSource,
    private val preferences: UserPreferencesDataSource,
    private val database: RozkladDatabase,
    private val sync: SyncManager,
    @param:Dispatcher(RozkladDispatchers.IO) private val io: CoroutineDispatcher,
    @param:ApplicationScope private val scope: CoroutineScope,
) : SessionRepository {
    private val _oauthError = MutableStateFlow<String?>(null)
    override val oauthError: StateFlow<String?> = _oauthError.asStateFlow()

    override fun clearOAuthError() {
        _oauthError.value = null
    }

    override suspend fun signInWithGoogle() = call { network.signInWithGoogle() }

    override fun completeGoogleSignIn(callbackUri: String) {
        scope.launch {
            call { network.completeOAuth(callbackUri) }.onFailure { _oauthError.value = it.message ?: "Не вдалося увійти через Google" }
        }
    }

    private val reload = MutableStateFlow(0)

    override val session: StateFlow<SessionState> = combine(network.authState, reload) { auth, _ -> auth }
        .flatMapLatest { auth ->
            when (auth) {
                NetworkAuthState.Initializing -> flowOf(SessionState.Loading)
                NetworkAuthState.SignedOut -> flowOf(SessionState.SignedOut)
                is NetworkAuthState.SignedIn -> resolveFamily(UserAccount(auth.user.id, auth.user.email, auth.user.fullName))
            }
        }
        .distinctUntilChanged()
        .stateIn(scope, SharingStarted.Eagerly, SessionState.Loading)

    /** Спершу кеш (миттєвий старт і офлайн), потім уточнення з сервера. */
    private fun resolveFamily(user: UserAccount): Flow<SessionState> = flow {
        val cached = preferences.cachedMembership.first()?.takeIf { it.userId == user.id }
        emit(cached?.let { SessionState.Ready(user, it.familyId, roleOf(it.role)) } ?: SessionState.Loading)
        try {
            val m = withContext(io) { network.membership(user.id) }
            if (m == null) {
                preferences.setCachedMembership(null)
                emit(SessionState.NoFamily(user))
            } else {
                preferences.setCachedMembership(CachedMembership(user.id, m.familyId, m.role))
                emit(SessionState.Ready(user, m.familyId, roleOf(m.role)))
            }
        } catch (e: CancellationException) {
            throw e
        } catch (@Suppress("TooGenericExceptionCaught") e: Exception) {
            if (cached == null) emit(SessionState.Unavailable(user, UserMessage.of(e)))
        }
    }

    private suspend fun <T> call(block: suspend () -> T): Result<T> = withContext(io) {
        try {
            Result.success(block())
        } catch (e: CancellationException) {
            throw e
        } catch (@Suppress("TooGenericExceptionCaught") e: Exception) {
            Result.failure(IllegalStateException(UserMessage.of(e), e))
        }
    }

    override suspend fun signIn(email: String, password: String) = call { network.signIn(email.trim(), password) }

    override suspend fun signUp(email: String, password: String) = call { network.signUp(email.trim(), password) }

    override suspend fun createFamily(parentName: String) = call {
        val name = parentName.trim()
        network.createFamily(familyName = "Сім’я $name", parentName = name)
    }.onSuccess { retry() }

    override suspend fun joinFamily(code: String, name: String) = call {
        network.joinFamily(code.trim().uppercase(), name.trim())
    }.onSuccess { retry() }

    override fun retry() = reload.update { it + 1 }

    override suspend fun signOut() {
        sync.cancel()
        withContext(io) {
            // Спершу відв'язуємо пристрій від сповіщень, поки ще є сесія (інакше RLS не дасть видалити токен).
            preferences.pushToken.first()?.let { token -> runCatching { network.unregisterPushToken(token) } }
            runCatching { network.signOut() } // офлайн — локальна сесія все одно очищається
        }
        clearLocal()
    }

    override suspend fun deleteAccount(): Result<Unit> {
        // Спершу сервер: якщо він не підтвердив, локально нічого не чіпаємо.
        val r = call { network.deleteAccount() }
        if (r.isSuccess) {
            sync.cancel()
            clearLocal()
        }
        return r
    }

    private suspend fun clearLocal() {
        withContext(io) { database.clearAllTables() }
        preferences.clear()
    }
}
