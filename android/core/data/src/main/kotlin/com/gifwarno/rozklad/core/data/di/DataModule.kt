package com.gifwarno.rozklad.core.data.di

import android.content.Context
import androidx.work.WorkManager
import com.gifwarno.rozklad.core.data.repository.DefaultSessionRepository
import com.gifwarno.rozklad.core.data.repository.FamilyRepository
import com.gifwarno.rozklad.core.data.repository.OfflineFirstFamilyRepository
import com.gifwarno.rozklad.core.data.repository.SessionRepository
import dagger.BindsOptionalOf
import dagger.Lazy
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import java.util.Optional
import javax.inject.Singleton
import kotlinx.serialization.json.Json

/**
 * Підміна джерел даних — лише debug-збірка (демо-режим з вигаданою сім'єю, без мережі).
 * У релізі не прив'язана: завжди справжні репозиторії.
 */
interface RepositoryOverride {
    /** null — без підміни. */
    fun session(): SessionRepository?

    fun family(): FamilyRepository?
}

@Module
@InstallIn(SingletonComponent::class)
internal interface DataBindings {
    @BindsOptionalOf
    fun repositoryOverride(): RepositoryOverride
}

@Module
@InstallIn(SingletonComponent::class)
internal object DataModule {
    @Provides
    @Singleton
    fun json(): Json = Json {
        ignoreUnknownKeys = true
        explicitNulls = true
        encodeDefaults = true
    }

    // Справжні репозиторії створюються ліниво: у демо-режимі їх не чіпаємо взагалі.
    @Provides
    @Singleton
    fun sessionRepository(override: Optional<RepositoryOverride>, real: Lazy<DefaultSessionRepository>): SessionRepository =
        override.orElse(null)?.session() ?: real.get()

    @Provides
    @Singleton
    fun familyRepository(override: Optional<RepositoryOverride>, real: Lazy<OfflineFirstFamilyRepository>): FamilyRepository =
        override.orElse(null)?.family() ?: real.get()

    @Provides
    fun workManager(@ApplicationContext context: Context): WorkManager = WorkManager.getInstance(context)
}
