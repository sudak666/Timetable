package com.gifwarno.rozklad.core.push

import android.content.Context
import com.google.firebase.FirebaseApp
import com.google.firebase.FirebaseOptions
import com.google.firebase.crashlytics.FirebaseCrashlytics

/**
 * Firebase без google-services.json: параметри — з BuildConfig (android/gradle.properties, rozklad.firebase.*).
 * Порожні параметри → Firebase вимкнено повністю (push і звіти про збої), збірка працює.
 */
object FirebaseSetup {
    internal fun options(): FirebaseOptions? {
        val values = listOf(
            BuildConfig.FIREBASE_API_KEY,
            BuildConfig.FIREBASE_APP_ID,
            BuildConfig.FIREBASE_PROJECT_ID,
            BuildConfig.FIREBASE_SENDER_ID,
        )
        if (values.any { it.isBlank() }) return null
        return FirebaseOptions.Builder()
            .setApiKey(BuildConfig.FIREBASE_API_KEY)
            .setApplicationId(BuildConfig.FIREBASE_APP_ID)
            .setProjectId(BuildConfig.FIREBASE_PROJECT_ID)
            .setGcmSenderId(BuildConfig.FIREBASE_SENDER_ID)
            .build()
    }

    /**
     * Викликати першим у Application.onCreate — тоді Crashlytics ловить і збої під час старту.
     * Звіти про збої — лише з release-збірок: налагоджувальні збої розробника не засмічують консоль.
     * Ідентифікатор користувача в звіти не додаємо (лише модель пристрою, версія Android, стек викликів).
     * @return false — збірка без Firebase.
     */
    fun init(context: Context): Boolean {
        val options = options() ?: return false
        if (FirebaseApp.getApps(context).isEmpty()) FirebaseApp.initializeApp(context, options)
        FirebaseCrashlytics.getInstance().isCrashlyticsCollectionEnabled = !BuildConfig.DEBUG
        return true
    }
}
