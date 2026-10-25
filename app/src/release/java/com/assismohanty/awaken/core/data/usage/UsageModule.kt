package com.assismohanty.awaken.core.data.usage

import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

/** Release: the real usage-stats reader. The debug twin wraps it with the "simulate usage" switch. */
@Module
@InstallIn(SingletonComponent::class)
abstract class UsageModule {
    @Binds
    abstract fun bindUsageGateway(impl: AndroidUsageGateway): UsageGateway
}
