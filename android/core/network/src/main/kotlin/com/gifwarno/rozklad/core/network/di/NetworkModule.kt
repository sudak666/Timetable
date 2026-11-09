package com.gifwarno.rozklad.core.network.di

import android.content.Context
import com.gifwarno.rozklad.core.network.BuildConfig
import com.gifwarno.rozklad.core.network.OAUTH_CALLBACK_HOST
import com.gifwarno.rozklad.core.network.RozkladNetworkDataSource
import com.gifwarno.rozklad.core.network.supabase.SupabaseNetworkDataSource
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.auth.Auth
import io.github.jan.supabase.auth.ExternalAuthAction
import io.github.jan.supabase.auth.FlowType
import io.github.jan.supabase.createSupabaseClient
import io.github.jan.supabase.postgrest.Postgrest
import io.ktor.client.engine.okhttp.OkHttp
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
internal object SupabaseModule {
    @Provides
    @Singleton
    fun supabase(@ApplicationContext context: Context): SupabaseClient = createSupabaseClient(
        supabaseUrl = BuildConfig.SUPABASE_URL,
        supabaseKey = BuildConfig.SUPABASE_KEY,
    ) {
        httpEngine = OkHttp.create()
        install(Auth) {
            alwaysAutoRefresh = true
            autoLoadFromStorage = true
            // Вхід через Google у браузері: PKCE (код + секрет на пристрої), повернення на <пакет>://login-callback.
            flowType = FlowType.PKCE
            scheme = context.packageName
            host = OAUTH_CALLBACK_HOST
            defaultExternalAuthAction = ExternalAuthAction.CustomTabs()
        }
        install(Postgrest)
    }
}

@Module
@InstallIn(SingletonComponent::class)
internal interface NetworkBindings {
    @Binds
    fun network(impl: SupabaseNetworkDataSource): RozkladNetworkDataSource
}
