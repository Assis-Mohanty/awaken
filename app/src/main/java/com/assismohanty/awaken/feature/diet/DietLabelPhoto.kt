package com.assismohanty.awaken.feature.diet

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.assismohanty.awaken.R
import com.assismohanty.awaken.core.designsystem.components.LabelCamera
import com.assismohanty.awaken.core.designsystem.components.LabelShutter
import com.assismohanty.awaken.core.designsystem.components.PrimaryButton
import com.assismohanty.awaken.core.designsystem.components.QuietButton
import com.assismohanty.awaken.core.designsystem.components.ScanFrame
import com.assismohanty.awaken.core.designsystem.components.ScreenBackground
import com.assismohanty.awaken.core.designsystem.components.SecondaryButton
import com.assismohanty.awaken.core.designsystem.lexicon.lexString
import com.assismohanty.awaken.core.designsystem.preview.PhonePreviews
import com.assismohanty.awaken.core.designsystem.theme.AwakenTheme
import com.assismohanty.awaken.core.designsystem.theme.Spacing

/** Photographing a nutrition table to fill in the label form (Diet v2, "add it from the label"). The photo is read on the phone. */
@Composable
fun LabelPhotoHost(state: LabelPhotoState, onEvent: (DietEvent) -> Unit) {
    val (access, askPermission, openSettings) = rememberCameraAccess()
    val shutter = remember { LabelShutter() }
    Dialog(onDismissRequest = { onEvent(DietEvent.CloseLabelPhoto) }, properties = DialogProperties(usePlatformDefaultWidth = false, dismissOnClickOutside = false)) {
        LabelPhotoScreen(
            state = state,
            access = access,
            camera = { modifier -> LabelCamera(shutter, modifier) },
            onTakePhoto = { shutter.capture { onEvent(DietEvent.LabelPhotoTaken(it)) } },
            onAskPermission = askPermission,
            onOpenSettings = openSettings,
            onEvent = onEvent,
        )
    }
}

@Composable
fun LabelPhotoScreen(
    state: LabelPhotoState,
    access: CameraAccess,
    camera: @Composable (Modifier) -> Unit,
    onTakePhoto: () -> Unit,
    onAskPermission: () -> Unit,
    onOpenSettings: () -> Unit,
    onEvent: (DietEvent) -> Unit,
) {
    val colors = AwakenTheme.colors
    ScreenBackground(modifier = Modifier.fillMaxSize()) {
        Column(modifier = Modifier.fillMaxSize()) {
            Row(modifier = Modifier.fillMaxWidth().padding(horizontal = Spacing.gutter, vertical = Spacing.xs), verticalAlignment = Alignment.CenterVertically) {
                Text(lexString(R.string.diet_label_photo_title), style = AwakenTheme.type.title, color = colors.textPrimary, modifier = Modifier.weight(1f).semantics { heading() })
                QuietButton(text = lexString(R.string.diet_label_photo_close), onClick = { onEvent(DietEvent.CloseLabelPhoto) })
            }
            when (access) {
                CameraAccess.READY -> {
                    Box(modifier = Modifier.weight(1f).fillMaxWidth()) { camera(Modifier.fillMaxSize()) }
                    Column(modifier = Modifier.fillMaxWidth().padding(Spacing.gutter), verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                        when (state) {
                            LabelPhotoState.Capturing -> {
                                Text(lexString(R.string.diet_label_photo_tip), style = AwakenTheme.type.body, color = colors.textSecondary)
                                PrimaryButton(text = lexString(R.string.diet_label_photo_take), onClick = onTakePhoto, modifier = Modifier.fillMaxWidth())
                            }
                            LabelPhotoState.Reading -> Text(lexString(R.string.diet_label_photo_reading), style = AwakenTheme.type.body, color = colors.textSecondary)
                            is LabelPhotoState.Failed -> {
                                Text(lexString(failureText(state.reason)), style = AwakenTheme.type.body, color = colors.textPrimary)
                                PrimaryButton(text = lexString(R.string.diet_label_photo_retry), onClick = { onEvent(DietEvent.LabelPhotoRetry) }, modifier = Modifier.fillMaxWidth())
                            }
                        }
                    }
                }
                CameraAccess.ASK -> NoCamera(R.string.diet_scan_ask) { PrimaryButton(text = lexString(R.string.diet_scan_allow), onClick = onAskPermission, modifier = Modifier.fillMaxWidth()) }
                CameraAccess.DENIED -> NoCamera(R.string.diet_scan_denied) { SecondaryButton(text = lexString(R.string.diet_scan_open_settings), onClick = onOpenSettings, modifier = Modifier.fillMaxWidth()) }
                CameraAccess.NO_CAMERA -> NoCamera(R.string.diet_scan_no_camera) {}
            }
        }
    }
}

@Composable
private fun NoCamera(text: Int, action: @Composable () -> Unit) {
    Column(modifier = Modifier.fillMaxSize().padding(Spacing.gutter), verticalArrangement = Arrangement.spacedBy(Spacing.md, Alignment.CenterVertically)) {
        Text(lexString(text), style = AwakenTheme.type.body, color = AwakenTheme.colors.textSecondary)
        action()
    }
}

private fun failureText(reason: LabelPhotoState.Reason): Int = when (reason) {
    LabelPhotoState.Reason.NOTHING_READ -> R.string.diet_label_photo_failed_nothing
    LabelPhotoState.Reason.NOT_A_TABLE -> R.string.diet_label_photo_failed_table
    LabelPhotoState.Reason.UNAVAILABLE -> R.string.diet_label_photo_failed_unavailable
}

// Previews: the camera itself needs a phone, so these show the frame only.

@PhonePreviews
@Composable
private fun LabelPhotoCapturingPreview() {
    AwakenTheme(reduceMotion = true) {
        LabelPhotoScreen(LabelPhotoState.Capturing, CameraAccess.READY, camera = { ScanFrame(it) }, onTakePhoto = {}, onAskPermission = {}, onOpenSettings = {}, onEvent = {})
    }
}

@Composable
private fun LabelPhotoPreview(state: LabelPhotoState, access: CameraAccess = CameraAccess.READY) {
    AwakenTheme(reduceMotion = true) {
        LabelPhotoScreen(state, access, camera = { ScanFrame(it) }, onTakePhoto = {}, onAskPermission = {}, onOpenSettings = {}, onEvent = {})
    }
}

@PhonePreviews
@Composable
private fun LabelPhotoReadingPreview() = LabelPhotoPreview(LabelPhotoState.Reading)

@PhonePreviews
@Composable
private fun LabelPhotoFailedPreview() = LabelPhotoPreview(LabelPhotoState.Failed(LabelPhotoState.Reason.NOT_A_TABLE))

@PhonePreviews
@Composable
private fun LabelPhotoAskPreview() = LabelPhotoPreview(LabelPhotoState.Capturing, CameraAccess.ASK)
