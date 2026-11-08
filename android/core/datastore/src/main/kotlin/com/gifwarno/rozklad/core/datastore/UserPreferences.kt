package com.gifwarno.rozklad.core.datastore

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

enum class ThemeMode { SYSTEM, LIGHT, DARK }

data class UserPreferences(
    /** Дитина, яку зараз дивиться батько (у дитини — завжди вона сама). */
    val selectedChildId: String?,
    val themeMode: ThemeMode,
)

/** Налаштування пристрою (не сім'ї): що вибрано, яка тема. Не містить секретів. */
@Singleton
class UserPreferencesDataSource @Inject constructor(private val store: DataStore<Preferences>) {
    val preferences: Flow<UserPreferences> = store.data.map { p ->
        UserPreferences(
            selectedChildId = p[SELECTED_CHILD],
            themeMode = p[THEME]?.let { runCatching { ThemeMode.valueOf(it) }.getOrNull() } ?: ThemeMode.SYSTEM,
        )
    }

    suspend fun setSelectedChild(id: String?) {
        store.edit { if (id == null) it.remove(SELECTED_CHILD) else it[SELECTED_CHILD] = id }
    }

    suspend fun setThemeMode(mode: ThemeMode) {
        store.edit { it[THEME] = mode.name }
    }

    /** Остання відома сім'я користувача — щоб застосунок відкривався офлайн. */
    val cachedMembership: Flow<CachedMembership?> = store.data.map { p ->
        val user = p[MEMBER_USER] ?: return@map null
        val family = p[MEMBER_FAMILY] ?: return@map null
        val role = p[MEMBER_ROLE] ?: return@map null
        CachedMembership(user, family, role)
    }

    suspend fun setCachedMembership(m: CachedMembership?) {
        store.edit {
            if (m == null) {
                it.remove(MEMBER_USER)
                it.remove(MEMBER_FAMILY)
                it.remove(MEMBER_ROLE)
            } else {
                it[MEMBER_USER] = m.userId
                it[MEMBER_FAMILY] = m.familyId
                it[MEMBER_ROLE] = m.role
            }
        }
    }

    suspend fun clear() {
        store.edit { it.clear() }
    }

    private companion object {
        val SELECTED_CHILD = stringPreferencesKey("selected_child")
        val THEME = stringPreferencesKey("theme_mode")
        val MEMBER_USER = stringPreferencesKey("member_user")
        val MEMBER_FAMILY = stringPreferencesKey("member_family")
        val MEMBER_ROLE = stringPreferencesKey("member_role")
    }
}

data class CachedMembership(val userId: String, val familyId: String, val role: String)

private val Context.userPreferences: DataStore<Preferences> by preferencesDataStore(name = "user_preferences")

@Module
@InstallIn(SingletonComponent::class)
object DataStoreModule {
    @Provides
    @Singleton
    fun preferencesDataStore(@ApplicationContext context: Context): DataStore<Preferences> = context.userPreferences
}
