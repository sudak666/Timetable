package com.gifwarno.rozklad.core.push

import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.flow.Flow

/** Те, що бачить екран налаштувань. Окремий інтерфейс — щоб ViewModel тестувалися без Firebase. */
interface PushSettings {
    /** Чи зібрано застосунок із параметрами Firebase (інакше перемикач не показуємо). */
    val isAvailable: Boolean
    val enabled: Flow<Boolean>
    val lastError: Flow<String?>

    suspend fun setEnabled(enabled: Boolean)

    /** Android 13+ вимагає дозволу POST_NOTIFICATIONS. */
    fun hasPermission(): Boolean
}

@Module
@InstallIn(SingletonComponent::class)
internal interface PushModule {
    @Binds
    fun settings(impl: PushManager): PushSettings
}
