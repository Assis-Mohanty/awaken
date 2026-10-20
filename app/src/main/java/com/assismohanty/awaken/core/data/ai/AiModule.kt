package com.assismohanty.awaken.core.data.ai

import com.assismohanty.awaken.BuildConfig
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import io.ktor.client.HttpClient
import io.ktor.client.engine.okhttp.OkHttp
import io.ktor.client.plugins.HttpTimeout
import javax.inject.Singleton

/**
 * AI Paths wiring (ARCH §4). Which [PathOracleClient] the app uses is decided by the `OracleModule` in each build type's
 * source set (debug can swap in a fake; release is always the real client).
 */
@Module
@InstallIn(SingletonComponent::class)
abstract class AiModule {
    @Binds
    abstract fun bindIntegrityTokenProvider(impl: PlayIntegrityTokenProvider): IntegrityTokenProvider

    companion object {
        /** ARCH §3.7: connect 10 s, total 45 s (model latency is the long pole). */
        const val CONNECT_TIMEOUT_MS = 10_000L
        const val REQUEST_TIMEOUT_MS = 45_000L

        @Provides
        @Singleton
        fun provideOracleConfig(): OracleConfig = OracleConfig(
            baseUrl = BuildConfig.ORACLE_BASE_URL,
            debugKey = BuildConfig.ORACLE_DEBUG_KEY,
            cloudProjectNumber = BuildConfig.PLAY_CLOUD_PROJECT_NUMBER,
        )

        @Provides
        @Singleton
        fun provideHttpClient(): HttpClient = HttpClient(OkHttp) {
            // The client only ever sends the bytes it built; error statuses are mapped by KtorPathOracleClient.
            expectSuccess = false
            install(HttpTimeout) {
                connectTimeoutMillis = CONNECT_TIMEOUT_MS
                requestTimeoutMillis = REQUEST_TIMEOUT_MS
            }
        }
    }
}
