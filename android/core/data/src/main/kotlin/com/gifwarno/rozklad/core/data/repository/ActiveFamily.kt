package com.gifwarno.rozklad.core.data.repository

import com.gifwarno.rozklad.core.datastore.UserPreferencesDataSource
import com.gifwarno.rozklad.core.model.FamilyContext
import com.gifwarno.rozklad.core.model.Role
import com.gifwarno.rozklad.core.model.SessionState
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf

/** Спільна точка для всіх екранів: поточна сім'я + вибрана дитина + стан синхронізації. */
@OptIn(ExperimentalCoroutinesApi::class)
@Singleton
class ActiveFamily @Inject constructor(
    private val session: SessionRepository,
    private val families: FamilyRepository,
    private val preferences: UserPreferencesDataSource,
) {
    /** null — ще немає локальних даних (перше завантаження) або не увійшли. */
    val context: Flow<FamilyContext?> = session.session.flatMapLatest { s ->
        if (s !is SessionState.Ready) {
            flowOf(null)
        } else {
            combine(families.observe(s.familyId), preferences.preferences, families.syncStatus) { data, prefs, sync ->
                data ?: return@combine null
                val child = when (s.role) {
                    Role.CHILD -> data.members.firstOrNull { it.userId == s.user.id }
                    Role.PARENT -> data.children.firstOrNull { it.userId == prefs.selectedChildId } ?: data.children.firstOrNull()
                }
                FamilyContext(s.user, s.role, s.familyId, data, child, sync)
            }
        }
    }.distinctUntilChanged()

    suspend fun selectChild(childId: String) = preferences.setSelectedChild(childId)
}
