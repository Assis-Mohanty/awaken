package com.assismohanty.awaken.feature.diet

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
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
import com.assismohanty.awaken.core.data.catalog.Food
import com.assismohanty.awaken.core.data.catalog.FoodSource
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
import com.assismohanty.awaken.engine.body.Nutrient
import com.assismohanty.awaken.engine.body.Nutrients

/** Photographing a meal to get food suggestions (photo food scanner, plan 5.1). The photo is looked at on the phone and dropped. */
@Composable
fun DishPhotoHost(state: DishPhotoState, onEvent: (DietEvent) -> Unit) {
    val (access, askPermission, openSettings) = rememberCameraAccess()
    val shutter = remember { LabelShutter() }
    // The system photo picker needs no storage permission.
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { onEvent(DietEvent.DishPhotoPicked(it)) }
    Dialog(onDismissRequest = { onEvent(DietEvent.CloseDishPhoto) }, properties = DialogProperties(usePlatformDefaultWidth = false, dismissOnClickOutside = false)) {
        DishPhotoScreen(
            state = state,
            access = access,
            camera = { modifier -> LabelCamera(shutter, modifier) },
            onTakePhoto = { shutter.capture { onEvent(DietEvent.DishPhotoTaken(it)) } },
            onPickPhoto = { picker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) },
            onAskPermission = askPermission,
            onOpenSettings = openSettings,
            onEvent = onEvent,
        )
    }
}

@Composable
fun DishPhotoScreen(
    state: DishPhotoState,
    access: CameraAccess,
    camera: @Composable (Modifier) -> Unit,
    onTakePhoto: () -> Unit,
    onPickPhoto: () -> Unit,
    onAskPermission: () -> Unit,
    onOpenSettings: () -> Unit,
    onEvent: (DietEvent) -> Unit,
) {
    val colors = AwakenTheme.colors
    val added = (state as? DishPhotoState.Suggested)?.added ?: 0
    ScreenBackground(modifier = Modifier.fillMaxSize()) {
        Column(modifier = Modifier.fillMaxSize()) {
            Row(modifier = Modifier.fillMaxWidth().padding(horizontal = Spacing.gutter, vertical = Spacing.xs), verticalAlignment = Alignment.CenterVertically) {
                Text(lexString(R.string.diet_dish_photo_title), style = AwakenTheme.type.title, color = colors.textPrimary, modifier = Modifier.weight(1f).semantics { heading() })
                QuietButton(text = lexString(if (added > 0) R.string.diet_dish_photo_done else R.string.diet_label_photo_close), onClick = { onEvent(DietEvent.CloseDishPhoto) })
            }
            when (state) {
                DishPhotoState.Capturing -> Capture(access, camera, onTakePhoto, onPickPhoto, onAskPermission, onOpenSettings)
                DishPhotoState.Looking -> Message(R.string.diet_dish_photo_looking) {}
                is DishPhotoState.Suggested -> Suggestions(state, onEvent)
                DishPhotoState.Unsure -> Message(R.string.diet_dish_photo_unsure) {
                    PrimaryButton(text = lexString(R.string.diet_dish_photo_search), onClick = { onEvent(DietEvent.CloseDishPhoto) }, modifier = Modifier.fillMaxWidth())
                    SecondaryButton(text = lexString(R.string.diet_label_photo_retry), onClick = { onEvent(DietEvent.DishPhotoRetry) }, modifier = Modifier.fillMaxWidth())
                }
                DishPhotoState.Failed -> Message(R.string.diet_dish_photo_failed) {
                    PrimaryButton(text = lexString(R.string.diet_label_photo_retry), onClick = { onEvent(DietEvent.DishPhotoRetry) }, modifier = Modifier.fillMaxWidth())
                    SecondaryButton(text = lexString(R.string.diet_dish_photo_search), onClick = { onEvent(DietEvent.CloseDishPhoto) }, modifier = Modifier.fillMaxWidth())
                }
            }
        }
    }
}

@Composable
private fun Capture(
    access: CameraAccess,
    camera: @Composable (Modifier) -> Unit,
    onTakePhoto: () -> Unit,
    onPickPhoto: () -> Unit,
    onAskPermission: () -> Unit,
    onOpenSettings: () -> Unit,
) {
    val pick = @Composable { SecondaryButton(text = lexString(R.string.diet_dish_photo_pick), onClick = onPickPhoto, modifier = Modifier.fillMaxWidth()) }
    when (access) {
        CameraAccess.READY -> Column {
            Box(modifier = Modifier.weight(1f).fillMaxWidth()) { camera(Modifier.fillMaxSize()) }
            Column(modifier = Modifier.fillMaxWidth().padding(Spacing.gutter), verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                Text(lexString(R.string.diet_dish_photo_tip), style = AwakenTheme.type.body, color = AwakenTheme.colors.textSecondary)
                PrimaryButton(text = lexString(R.string.diet_dish_photo_take), onClick = onTakePhoto, modifier = Modifier.fillMaxWidth())
                pick()
            }
        }
        CameraAccess.ASK -> Message(R.string.diet_scan_ask) {
            PrimaryButton(text = lexString(R.string.diet_scan_allow), onClick = onAskPermission, modifier = Modifier.fillMaxWidth())
            pick()
        }
        CameraAccess.DENIED -> Message(R.string.diet_scan_denied) {
            SecondaryButton(text = lexString(R.string.diet_scan_open_settings), onClick = onOpenSettings, modifier = Modifier.fillMaxWidth())
            pick()
        }
        CameraAccess.NO_CAMERA -> Message(R.string.diet_scan_no_camera) { pick() }
    }
}

@Composable
private fun Suggestions(state: DishPhotoState.Suggested, onEvent: (DietEvent) -> Unit) {
    val colors = AwakenTheme.colors
    Column(
        modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(Spacing.gutter),
        verticalArrangement = Arrangement.spacedBy(Spacing.sm),
    ) {
        Text(lexString(if (state.added > 0) R.string.diet_dish_photo_another else R.string.diet_dish_photo_suggested), style = AwakenTheme.type.body, color = colors.textPrimary)
        state.foods.forEachIndexed { i, food ->
            if (i > 0) HorizontalDivider(color = colors.divider, thickness = AwakenTheme.dimens.hairline)
            FoodRow(food, onClick = { onEvent(DietEvent.PickDish(i)) })
        }
        SecondaryButton(text = lexString(R.string.diet_dish_photo_none), onClick = { onEvent(DietEvent.NoneOfThese) }, modifier = Modifier.fillMaxWidth())
        if (state.added > 0) PrimaryButton(text = lexString(R.string.diet_dish_photo_done), onClick = { onEvent(DietEvent.CloseDishPhoto) }, modifier = Modifier.fillMaxWidth())
    }
}

@Composable
private fun Message(text: Int, actions: @Composable () -> Unit) {
    Column(modifier = Modifier.fillMaxSize().padding(Spacing.gutter), verticalArrangement = Arrangement.spacedBy(Spacing.md, Alignment.CenterVertically)) {
        Text(lexString(text), style = AwakenTheme.type.body, color = AwakenTheme.colors.textSecondary)
        actions()
    }
}

// Previews: the camera needs a phone, so these show the frame only. The foods are made up for the picture.

private fun previewFood(id: String, name: String, kcal: Double) = Food(
    id = id, name = name, source = FoodSource.AWAKEN, per100 = Nutrients(mapOf(Nutrient.KCAL to kcal, Nutrient.PROTEIN to 6.0, Nutrient.CARBS to 18.0, Nutrient.FAT to 4.0)),
)

private val previewFoods = listOf(previewFood("p1", "Dal tadka", 128.0), previewFood("p2", "Dal fry", 140.0), previewFood("p3", "Rice, plain, cooked", 130.0))

@PhonePreviews
@Composable
private fun DishPhotoCapturingPreview() {
    AwakenTheme(reduceMotion = true) {
        DishPhotoScreen(DishPhotoState.Capturing, CameraAccess.READY, camera = { ScanFrame(it) }, onTakePhoto = {}, onPickPhoto = {}, onAskPermission = {}, onOpenSettings = {}, onEvent = {})
    }
}

@Composable
private fun DishPhotoPreview(state: DishPhotoState, access: CameraAccess = CameraAccess.READY) {
    AwakenTheme(reduceMotion = true) {
        DishPhotoScreen(state, access, camera = { ScanFrame(it) }, onTakePhoto = {}, onPickPhoto = {}, onAskPermission = {}, onOpenSettings = {}, onEvent = {})
    }
}

@PhonePreviews
@Composable
private fun DishPhotoAskPreview() = DishPhotoPreview(DishPhotoState.Capturing, CameraAccess.ASK)

@PhonePreviews
@Composable
private fun DishPhotoSuggestedPreview() = DishPhotoPreview(DishPhotoState.Suggested(previewFoods))

@PhonePreviews
@Composable
private fun DishPhotoAnotherPreview() = DishPhotoPreview(DishPhotoState.Suggested(previewFoods, added = 1))

@PhonePreviews
@Composable
private fun DishPhotoUnsurePreview() = DishPhotoPreview(DishPhotoState.Unsure)

@PhonePreviews
@Composable
private fun DishPhotoFailedPreview() = DishPhotoPreview(DishPhotoState.Failed)
