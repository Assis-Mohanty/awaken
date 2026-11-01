package com.assismohanty.awaken.reelcounter.service

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import javax.inject.Inject
import javax.inject.Singleton

/** Whether the accessibility service is currently connected in this process. */
@Singleton
class ServiceStatus @Inject constructor() {
    private val _connected = MutableStateFlow(false)
    val connected: StateFlow<Boolean> = _connected

    fun setConnected(value: Boolean) {
        _connected.value = value
    }
}
