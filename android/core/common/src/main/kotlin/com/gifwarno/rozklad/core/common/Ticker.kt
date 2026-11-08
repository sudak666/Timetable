package com.gifwarno.rozklad.core.common

import java.time.Clock
import java.time.LocalDateTime
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow

/** Поточний час щосекунди, вирівняно по межі секунди — таймер уроку не «стрибає». */
fun Clock.ticks(periodMillis: Long = 1_000): Flow<LocalDateTime> = flow {
    while (true) {
        emit(LocalDateTime.now(this@ticks))
        delay(periodMillis - millis() % periodMillis)
    }
}
