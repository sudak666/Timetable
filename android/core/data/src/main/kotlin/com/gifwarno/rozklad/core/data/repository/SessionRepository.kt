package com.gifwarno.rozklad.core.data.repository

import com.gifwarno.rozklad.core.model.SessionState
import kotlinx.coroutines.flow.StateFlow

/** Хто увійшов і до якої сім'ї належить. */
interface SessionRepository {
    val session: StateFlow<SessionState>

    suspend fun signIn(email: String, password: String): Result<Unit>

    /** @return true — треба підтвердити пошту. */
    suspend fun signUp(email: String, password: String): Result<Boolean>

    suspend fun createFamily(parentName: String): Result<Unit>

    suspend fun joinFamily(code: String, name: String): Result<Unit>

    /** Повторити визначення сім'ї (після помилки мережі). */
    fun retry()

    suspend fun signOut()

    /** Видалити акаунт назавжди. Потрібна мережа; у разі успіху всі локальні дані теж стираються. */
    suspend fun deleteAccount(): Result<Unit>
}
