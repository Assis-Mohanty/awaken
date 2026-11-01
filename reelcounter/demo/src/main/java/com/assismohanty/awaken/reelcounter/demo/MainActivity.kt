package com.assismohanty.awaken.reelcounter.demo

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.Modifier
import com.assismohanty.awaken.reelcounter.ui.ReelCounterNavGraph
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        // Phase 3 replaces MaterialTheme with ReelTheme.
        setContent { MaterialTheme { ReelCounterNavGraph(Modifier.safeDrawingPadding()) } }
    }
}
