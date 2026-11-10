package com.gifwarno.rozklad.core.common

import dagger.BindsOptionalOf
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import java.time.Clock
import java.util.Optional
import javax.inject.Qualifier
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob

@Qualifier
@Retention(AnnotationRetention.RUNTIME)
annotation class Dispatcher(val value: RozkladDispatchers)

enum class RozkladDispatchers { Default, IO }

/** Скоуп усього застосунку — для роботи, що має пережити екран (синхронізація). */
@Qualifier
@Retention(AnnotationRetention.RUNTIME)
annotation class ApplicationScope

@Module
@InstallIn(SingletonComponent::class)
object CommonModule {
    @Provides
    @Dispatcher(RozkladDispatchers.IO)
    fun ioDispatcher(): CoroutineDispatcher = Dispatchers.IO

    @Provides
    @Dispatcher(RozkladDispatchers.Default)
    fun defaultDispatcher(): CoroutineDispatcher = Dispatchers.Default

    @Provides
    @Singleton
    @ApplicationScope
    fun applicationScope(@Dispatcher(RozkladDispatchers.Default) dispatcher: CoroutineDispatcher): CoroutineScope =
        CoroutineScope(SupervisorJob() + dispatcher)

    /** Годинник інжектиться — у тестах підставляємо фіксований час, у демо-режимі (debug) — свій. */
    @Provides
    fun clock(override: Optional<ClockOverride>): Clock = override.orElse(null)?.clock() ?: Clock.systemDefaultZone()
}

/** Підміна годинника — лише debug-збірка (демо-режим). У релізі не прив'язана: системний час. */
interface ClockOverride {
    /** null — без підміни. */
    fun clock(): Clock?
}

@Module
@InstallIn(SingletonComponent::class)
internal interface ClockOverrideSlot {
    @BindsOptionalOf
    fun clockOverride(): ClockOverride
}
