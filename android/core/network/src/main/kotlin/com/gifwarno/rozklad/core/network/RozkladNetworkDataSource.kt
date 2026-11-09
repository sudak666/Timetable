package com.gifwarno.rozklad.core.network

import com.gifwarno.rozklad.core.network.model.NetworkAlert
import com.gifwarno.rozklad.core.network.model.NetworkAlertLocation
import com.gifwarno.rozklad.core.network.model.NetworkAuthState
import com.gifwarno.rozklad.core.network.model.NetworkMembership
import com.gifwarno.rozklad.core.network.model.NetworkSnapshot
import com.gifwarno.rozklad.core.network.model.NetworkWrite
import com.gifwarno.rozklad.core.network.model.WriteResult
import kotlinx.coroutines.flow.Flow

/**
 * Межа між застосунком і бекендом. Репозиторії знають лише цей інтерфейс —
 * у тестах підставляється фейк, а Supabase можна замінити без змін у UI.
 */
interface RozkladNetworkDataSource {
    val authState: Flow<NetworkAuthState>

    suspend fun signIn(email: String, password: String)

    /** @return true, якщо потрібно підтвердити пошту перед входом. */
    suspend fun signUp(email: String, password: String): Boolean

    suspend fun signOut()

    /** Відкриває вхід Google у браузері; результат прийде deep link'ом на <пакет>://login-callback. */
    suspend fun signInWithGoogle()

    /** Завершує вхід Google: обмінює одноразовий код із адреси повернення на сесію. */
    suspend fun completeOAuth(callbackUri: String)

    /**
     * Безповоротно видаляє акаунт на сервері (RPC school_delete_account) і локальну сесію.
     * Якщо користувач — останній з батьків, сервер видаляє і сім'ю.
     */
    suspend fun deleteAccount()

    /**
     * Прив'язати токен FCM цього пристрою до поточного користувача (RPC school_register_fcm).
     * [alerts] — чи надсилати на цей пристрій push про повітряні тривоги.
     */
    suspend fun registerPushToken(token: String, alerts: Boolean)

    /** Прибрати токен із сервера (RLS дозволяє лише свої). */
    suspend fun unregisterPushToken(token: String)

    suspend fun membership(userId: String): NetworkMembership?

    suspend fun createFamily(familyName: String, parentName: String)

    suspend fun joinFamily(code: String, name: String)

    /** Код для запрошення другого батька (видно лише батькам). */
    suspend fun parentCode(familyId: String): String?

    suspend fun snapshot(familyId: String): NetworkSnapshot

    suspend fun write(op: NetworkWrite): WriteResult

    /**
     * Довідник місць для тривог: [oblastUid] == null — області та міста зі спецстатусом,
     * інакше — райони й громади цієї області.
     */
    suspend fun alertLocations(oblastUid: Int?): List<NetworkAlertLocation>

    /** Обрати місце для тривог сім'ї (лише батьки — так дозволяє RLS); null — вимкнути. */
    suspend fun setAlertRegion(familyId: String, uid: Int?)

    /** Місце і поточний стан тривоги — легкий запит для оновлення плашки. */
    suspend fun alert(uid: Int): NetworkAlert?
}

/** Хост адреси повернення після входу через Google: <пакет застосунку>://login-callback. */
const val OAUTH_CALLBACK_HOST = "login-callback"
