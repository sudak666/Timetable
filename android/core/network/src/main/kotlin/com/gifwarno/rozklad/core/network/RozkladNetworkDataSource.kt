package com.gifwarno.rozklad.core.network

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

    suspend fun membership(userId: String): NetworkMembership?

    suspend fun createFamily(familyName: String, parentName: String)

    suspend fun joinFamily(code: String, name: String)

    /** Код для запрошення другого батька (видно лише батькам). */
    suspend fun parentCode(familyId: String): String?

    suspend fun snapshot(familyId: String): NetworkSnapshot

    suspend fun write(op: NetworkWrite): WriteResult
}
