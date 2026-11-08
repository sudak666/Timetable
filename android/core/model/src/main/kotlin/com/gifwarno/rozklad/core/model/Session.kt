package com.gifwarno.rozklad.core.model

/** Куди веде застосунок: вхід → вибір сім'ї → головний екран. */
sealed interface SessionState {
    data object Loading : SessionState

    data object SignedOut : SessionState

    data class NoFamily(val user: UserAccount) : SessionState

    /** Увійшли, але сім'ю не з'ясувати (немає мережі й кешу). */
    data class Unavailable(val user: UserAccount, val message: String) : SessionState

    data class Ready(val user: UserAccount, val familyId: String, val role: Role) : SessionState
}

/** Стан синхронізації з сервером — показуємо користувачу чесно. */
data class SyncStatus(val pendingChanges: Int, val lastError: String?)
