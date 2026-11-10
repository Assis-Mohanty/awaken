package com.assismohanty.awaken.reelcounter.data

import kotlinx.coroutines.flow.Flow

/** Whether a focus session or Lockdown is running (the host knows; Reel Counter never reads the gate itself). */
interface ReelSessionGate {
    val sessionRunning: Flow<Boolean>
}
