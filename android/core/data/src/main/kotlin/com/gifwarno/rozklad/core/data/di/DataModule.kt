package com.gifwarno.rozklad.core.data.di

import android.content.Context
import androidx.work.WorkManager
import com.gifwarno.rozklad.core.data.repository.DefaultSessionRepository
import com.gifwarno.rozklad.core.data.repository.FamilyRepository
import com.gifwarno.rozklad.core.data.repository.OfflineFirstFamilyRepository
import com.gifwarno.rozklad.core.data.repository.SessionRepository
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton
import kotlinx.serialization.json.Json

@Module
@InstallIn(SingletonComponent::class)
internal interface DataBindings {
    @Binds
    fun sessionRepository(impl: DefaultSessionRepository): SessionRepository

    @Binds
    fun familyRepository(impl: OfflineFirstFamilyRepository): FamilyRepository
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

    @Provides
    fun workManager(@ApplicationContext context: Context): WorkManager = WorkManager.getInstance(context)
}
