package com.assismohanty.awaken.core.data.health

import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

/** Release: the real Health Connect client. The debug twin wraps it with the "simulate steps" switch. */
@Module
@InstallIn(SingletonComponent::class)
abstract class HealthModule {
    @Binds
    abstract fun bindHealthGateway(impl: HealthConnectGateway): HealthGateway
}
