package com.assismohanty.awaken.feature.reelcounter

import androidx.compose.runtime.Composable
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import com.assismohanty.awaken.reelcounter.inspector.InspectorHooks
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject

@HiltViewModel
class ReelInspectorHostViewModel @Inject constructor(val inspector: InspectorHooks) : ViewModel()

/** The Reel Counter detector inspector. Only debug builds have one; the release build shows nothing. */
@Composable
fun ReelInspectorEntry(onBack: () -> Unit, viewModel: ReelInspectorHostViewModel = hiltViewModel()) {
    viewModel.inspector.screen?.invoke(onBack)
}
