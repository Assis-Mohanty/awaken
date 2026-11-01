package com.assismohanty.awaken.reelcounter.inspector

import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class InspectorModule {
    @Binds
    @Singleton
    abstract fun hooks(impl: DebugInspectorHooks): InspectorHooks
}
