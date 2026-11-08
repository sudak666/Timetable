package com.gifwarno.rozklad.core.model

/** Усе, що потрібно екрану: хто я, яка сім'я, яку дитину дивимось. */
data class FamilyContext(
    val user: UserAccount,
    val role: Role,
    val familyId: String,
    val data: FamilyData,
    /** Дитина, чиї оцінки й домашку показуємо (для дитини — вона сама). */
    val child: Member?,
    val sync: SyncStatus,
) {
    val isParent: Boolean get() = role == Role.PARENT
    val me: Member? get() = data.members.firstOrNull { it.userId == user.id }
}
