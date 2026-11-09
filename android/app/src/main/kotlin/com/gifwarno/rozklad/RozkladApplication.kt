package com.gifwarno.rozklad

import android.app.Application
import androidx.hilt.work.HiltWorkerFactory
import androidx.work.Configuration
import com.gifwarno.rozklad.core.push.PushManager
import com.gifwarno.rozklad.feature.widget.WidgetUpdater
import dagger.hilt.android.HiltAndroidApp
import javax.inject.Inject

@HiltAndroidApp
class RozkladApplication :
    Application(),
    Configuration.Provider {
    @Inject lateinit var workerFactory: HiltWorkerFactory

    @Inject lateinit var widgetUpdater: WidgetUpdater

    @Inject lateinit var pushManager: PushManager

    override fun onCreate() {
        super.onCreate()
        widgetUpdater.start()
        pushManager.start()
    }

    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder().setWorkerFactory(workerFactory).build()
}
