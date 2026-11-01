package com.assismohanty.awaken.reelcounter.demo

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import com.assismohanty.awaken.reelcounter.ReelCounterRoot

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { ReelCounterRoot() }
    }
}
