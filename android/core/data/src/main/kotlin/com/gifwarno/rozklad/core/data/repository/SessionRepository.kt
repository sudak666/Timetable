package com.gifwarno.rozklad.core.data.repository

import com.gifwarno.rozklad.core.model.SessionState
import kotlinx.coroutines.flow.StateFlow

/** Хто увійшов і до якої сім'ї належить. */
interface SessionRepository {
    val session: StateFlow<SessionState>

    suspend fun signIn(email: String, password: String): Result<Unit>

    /** @return true — треба підтвердити пошту. */
    suspend fun signUp(email: String, password: String): Result<Boolean>

    /** «Забули пароль?»: лист на пошту. Успіх не означає, що такий акаунт існує. */
    suspend fun resetPassword(email: String): Result<Unit>

    /** «Змінити пароль» у профілі. */
    suspend fun changePassword(password: String): Result<Unit>

    suspend fun createFamily(parentName: String): Result<Unit>

    suspend fun joinFamily(code: String, name: String): Result<Unit>

    /** Повторити визначення сім'ї (після помилки мережі). */
    fun retry()

    suspend fun signOut()

    /** Відкриває вхід Google у браузері. Успіх — коли прийде deep link (completeGoogleSignIn). */
    suspend fun signInWithGoogle(): Result<Unit>

    /** Адреса повернення з браузера після входу Google — обмінюємо код на сесію. */
    fun completeGoogleSignIn(callbackUri: String)

    /** Помилка входу Google (показує екран входу), null — немає. */
    val oauthError: StateFlow<String?>

    fun clearOAuthError()

    /** Видалити акаунт назавжди. Потрібна мережа; у разі успіху всі локальні дані теж стираються. */
    suspend fun deleteAccount(): Result<Unit>
}
