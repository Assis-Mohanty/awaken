package com.assismohanty.awaken.core.protect

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.assismohanty.awaken.core.data.di.ApplicationScope
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

/** What the receiver needs from the app graph. An entry point, because Kotlin can't call the abstract super of a Hilt receiver. */
@EntryPoint
@InstallIn(SingletonComponent::class)
interface ProtectBootEntryPoint {
    fun controller(): ProtectController

    @ApplicationScope
    fun scope(): CoroutineScope
}

/** After a restart or an app update, starts Pause again if it is on (GD §19.1). Does nothing else, reads nothing. */
class ProtectBootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_BOOT_COMPLETED && intent.action != Intent.ACTION_MY_PACKAGE_REPLACED) return
        val graph = EntryPointAccessors.fromApplication(context.applicationContext, ProtectBootEntryPoint::class.java)
        val pending = goAsync()
        graph.scope().launch {
            try {
                graph.controller().refresh()
            } finally {
                pending.finish()
            }
        }
    }
}
