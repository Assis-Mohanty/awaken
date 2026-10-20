package com.assismohanty.awaken.core.data.ai

import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/** Release: always the real Path Oracle client. The debug twin can swap in `FakePathOracle`. */
@Module
@InstallIn(SingletonComponent::class)
object OracleModule {
    @Provides
    @Singleton
    fun providePathOracleClient(impl: KtorPathOracleClient): PathOracleClient = impl
}
