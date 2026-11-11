package com.assismohanty.awaken.feature.diet

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.core.content.ContextCompat
import com.assismohanty.awaken.R
import com.assismohanty.awaken.core.data.catalog.Food
import com.assismohanty.awaken.core.designsystem.components.PrimaryButton
import com.assismohanty.awaken.core.designsystem.components.QuietButton
import com.assismohanty.awaken.core.designsystem.components.ScreenBackground
import com.assismohanty.awaken.core.designsystem.components.ScanFrame
import com.assismohanty.awaken.core.designsystem.components.ScanViewfinder
import com.assismohanty.awaken.core.designsystem.components.SecondaryButton
import com.assismohanty.awaken.core.designsystem.lexicon.lexString
import com.assismohanty.awaken.core.designsystem.preview.PhonePreviews
import com.assismohanty.awaken.core.designsystem.theme.AwakenTheme
import com.assismohanty.awaken.core.designsystem.theme.Spacing
import com.assismohanty.awaken.core.domain.FoodOrigin
import com.assismohanty.awaken.core.domain.OnlineStatus
import com.assismohanty.awaken.core.scan.BarcodeDecoder
import com.assismohanty.awaken.engine.body.Nutrient
import com.assismohanty.awaken.engine.body.Nutrients

/** What the phone allows the scanner to do. */
enum class CameraAccess { NO_CAMERA, ASK, DENIED, READY }

/** The scanner over the diary: asks for the camera once, then reads codes from the live preview (Diet v2 plan section 6). */
@Composable
fun ScannerHost(scan: ScanState, onEvent: (DietEvent) -> Unit) {
    val context = LocalContext.current
    val hasCamera = remember { context.packageManager.hasSystemFeature(PackageManager.FEATURE_CAMERA_ANY) }
    var granted by remember { mutableStateOf(ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) }
    var asked by rememberSaveable { mutableStateOf(false) }
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { ok ->
        granted = ok
        asked = true
    }
    val decoder = remember { BarcodeDecoder() }
    val haptic = LocalHapticFeedback.current
    LaunchedEffect(scan) {
        if (scan is ScanState.Found || scan is ScanState.NotFound) haptic.performHapticFeedback(HapticFeedbackType.LongPress)
    }
    val access = when {
        !hasCamera -> CameraAccess.NO_CAMERA
        granted -> CameraAccess.READY
        asked -> CameraAccess.DENIED
        else -> CameraAccess.ASK
    }
    Dialog(onDismissRequest = { onEvent(DietEvent.CloseScanner) }, properties = DialogProperties(usePlatformDefaultWidth = false, dismissOnClickOutside = false)) {
        BarcodeScannerScreen(
            scan = scan,
            access = access,
            viewfinder = { modifier ->
                ScanViewfinder(onFrame = { luma, w, h -> decoder.decode(luma, w, h)?.let { onEvent(DietEvent.BarcodeRead(it)) } }, modifier = modifier)
            },
            onAskPermission = { launcher.launch(Manifest.permission.CAMERA) },
            onOpenSettings = { openAppSettings(context) },
            onEvent = onEvent,
        )
    }
}

private fun openAppSettings(context: Context) {
    context.startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", context.packageName, null)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
}

/** The scanner screen: the camera (or why there is none) above, and what the last scan found below. */
@Composable
fun BarcodeScannerScreen(
    scan: ScanState,
    access: CameraAccess,
    viewfinder: @Composable (Modifier) -> Unit,
    onAskPermission: () -> Unit,
    onOpenSettings: () -> Unit,
    onEvent: (DietEvent) -> Unit,
) {
    val colors = AwakenTheme.colors
    ScreenBackground(modifier = Modifier.fillMaxSize()) {
        Column(modifier = Modifier.fillMaxSize()) {
            Row(modifier = Modifier.fillMaxWidth().padding(horizontal = Spacing.gutter, vertical = Spacing.xs), verticalAlignment = Alignment.CenterVertically) {
                Text(lexString(R.string.diet_scan_title), style = AwakenTheme.type.title, color = colors.textPrimary, modifier = Modifier.weight(1f).semantics { heading() })
                QuietButton(text = lexString(R.string.diet_scan_close), onClick = { onEvent(DietEvent.CloseScanner) })
            }
            when (access) {
                CameraAccess.READY -> {
                    Box(modifier = Modifier.weight(1f).fillMaxWidth()) { viewfinder(Modifier.fillMaxSize()) }
                    ScanResultPanel(scan, onEvent)
                }
                CameraAccess.ASK -> Reason(R.string.diet_scan_ask) { PrimaryButton(text = lexString(R.string.diet_scan_allow), onClick = onAskPermission, modifier = Modifier.fillMaxWidth()) }
                CameraAccess.DENIED -> Reason(R.string.diet_scan_denied) { SecondaryButton(text = lexString(R.string.diet_scan_open_settings), onClick = onOpenSettings, modifier = Modifier.fillMaxWidth()) }
                CameraAccess.NO_CAMERA -> Reason(R.string.diet_scan_no_camera) {}
            }
        }
    }
}

@Composable
private fun Reason(text: Int, action: @Composable () -> Unit) {
    Column(modifier = Modifier.fillMaxSize().padding(Spacing.gutter), verticalArrangement = Arrangement.spacedBy(Spacing.md, Alignment.CenterVertically)) {
        Text(lexString(text), style = AwakenTheme.type.body, color = AwakenTheme.colors.textSecondary)
        action()
    }
}

@Composable
private fun ScanResultPanel(scan: ScanState, onEvent: (DietEvent) -> Unit) {
    val colors = AwakenTheme.colors
    Column(modifier = Modifier.fillMaxWidth().padding(Spacing.gutter), verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
        when (scan) {
            ScanState.Scanning -> Text(lexString(R.string.diet_scan_aim), style = AwakenTheme.type.body, color = colors.textSecondary)
            ScanState.Looking -> Text(lexString(R.string.diet_scan_looking), style = AwakenTheme.type.body, color = colors.textSecondary)
            ScanState.Invalid -> {
                Text(lexString(R.string.diet_scan_invalid), style = AwakenTheme.type.body, color = colors.textPrimary)
                SecondaryButton(text = lexString(R.string.diet_scan_again), onClick = { onEvent(DietEvent.ScanAgain) }, modifier = Modifier.fillMaxWidth())
            }
            is ScanState.Found -> {
                FoundCard(scan.food)
                when (scan.origin) {
                    FoodOrigin.PACK -> Text(lexString(R.string.diet_scan_from_pack), style = AwakenTheme.type.caption, color = colors.textMuted)
                    FoodOrigin.ONLINE -> Text(lexString(R.string.diet_scan_from_online), style = AwakenTheme.type.caption, color = colors.textMuted)
                    FoodOrigin.OWN -> Unit
                }
                PrimaryButton(text = lexString(R.string.diet_scan_add), onClick = { onEvent(DietEvent.ScanAccept) }, modifier = Modifier.fillMaxWidth())
                QuietButton(text = lexString(R.string.diet_scan_again), onClick = { onEvent(DietEvent.ScanAgain) })
            }
            is ScanState.NotFound -> {
                Text(lexString(R.string.diet_scan_code, scan.barcode), style = AwakenTheme.type.caption, color = colors.textMuted)
                if (scan.online == OnlineStatus.NEEDS_CONSENT) {
                    Text(lexString(R.string.diet_scan_online_ask_title), style = AwakenTheme.type.title, color = colors.textPrimary, modifier = Modifier.semantics { heading() })
                    Text(lexString(R.string.diet_scan_online_ask), style = AwakenTheme.type.body, color = colors.textPrimary)
                    PrimaryButton(text = lexString(R.string.diet_scan_online_allow), onClick = { onEvent(DietEvent.ScanAllowOnline) }, modifier = Modifier.fillMaxWidth())
                    SecondaryButton(text = lexString(R.string.diet_scan_online_not_now), onClick = { onEvent(DietEvent.ScanDeclineOnline) }, modifier = Modifier.fillMaxWidth())
                } else {
                    Text(lexString(notFoundText(scan.online)), style = AwakenTheme.type.body, color = colors.textPrimary)
                    PrimaryButton(text = lexString(R.string.diet_scan_from_label), onClick = { onEvent(DietEvent.ScanCreate) }, modifier = Modifier.fillMaxWidth())
                    QuietButton(text = lexString(R.string.diet_scan_again), onClick = { onEvent(DietEvent.ScanAgain) })
                }
            }
        }
    }
}

private fun notFoundText(status: OnlineStatus): Int = when (status) {
    OnlineStatus.DECLINED -> R.string.diet_scan_declined
    OnlineStatus.NOT_LISTED -> R.string.diet_scan_not_listed
    OnlineStatus.UNUSABLE -> R.string.diet_scan_unusable
    OnlineStatus.UNREACHABLE -> R.string.diet_scan_unreachable
    OnlineStatus.NEEDS_CONSENT -> R.string.diet_scan_not_found
}

/** The product, per 100 g and, when the label gave a serving, per serving side by side. */
@Composable
private fun FoundCard(food: Food) {
    val colors = AwakenTheme.colors
    Text(food.name, style = AwakenTheme.type.title, color = colors.textPrimary, maxLines = 2)
    Row(horizontalArrangement = Arrangement.spacedBy(Spacing.md)) {
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(Spacing.xxs)) {
            Text(lexString(R.string.diet_scan_per_100), style = AwakenTheme.type.label, color = colors.accent)
            Text(macros(food.per100), style = AwakenTheme.type.caption, color = colors.textSecondary)
        }
        food.portions.firstOrNull()?.let { serving ->
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(Spacing.xxs)) {
                Text(lexString(R.string.diet_scan_per_serving, formatAmount(serving.grams)), style = AwakenTheme.type.label, color = colors.accent)
                Text(macros(food.per100.forGrams(serving.grams)), style = AwakenTheme.type.caption, color = colors.textSecondary)
            }
        }
    }
}

@Composable
private fun macros(n: Nutrients): String =
    lexString(R.string.diet_scan_macros, formatAmount(n[Nutrient.KCAL]), formatAmount(n[Nutrient.PROTEIN]), formatAmount(n[Nutrient.CARBS]), formatAmount(n[Nutrient.FAT]))

// Previews: the camera itself needs a phone, so these show the frame only.

private val previewFood = Food(
    "pack:5901234123457", "Digestive biscuits (Acme)", com.assismohanty.awaken.core.data.catalog.FoodSource.CUSTOM,
    Nutrients(mapOf(Nutrient.KCAL to 480.0, Nutrient.PROTEIN to 7.0, Nutrient.CARBS to 64.0, Nutrient.FAT to 21.0)),
    listOf(com.assismohanty.awaken.core.data.catalog.Portion("1 serving", 30.0)),
)

@PhonePreviews
@Composable
private fun ScannerScanningPreview() = ScannerPreview(ScanState.Scanning)

@Composable
private fun ScannerPreview(scan: ScanState, access: CameraAccess = CameraAccess.READY) {
    AwakenTheme(reduceMotion = true) {
        BarcodeScannerScreen(scan, access, viewfinder = { ScanFrame(it) }, onAskPermission = {}, onOpenSettings = {}, onEvent = {})
    }
}

@PhonePreviews
@Composable
private fun ScannerFoundPreview() = ScannerPreview(ScanState.Found(previewFood, FoodOrigin.PACK))

@PhonePreviews
@Composable
private fun ScannerNotFoundPreview() = ScannerPreview(ScanState.NotFound("5901234123457", OnlineStatus.NOT_LISTED))

@PhonePreviews
@Composable
private fun ScannerInvalidPreview() = ScannerPreview(ScanState.Invalid)

@PhonePreviews
@Composable
private fun ScannerAskPreview() = ScannerPreview(ScanState.Scanning, CameraAccess.ASK)

@PhonePreviews
@Composable
private fun ScannerDeniedPreview() = ScannerPreview(ScanState.Scanning, CameraAccess.DENIED)

@PhonePreviews
@Composable
private fun ScannerNoCameraPreview() = ScannerPreview(ScanState.Scanning, CameraAccess.NO_CAMERA)

@PhonePreviews
@Composable
private fun ScannerOnlineAskPreview() = ScannerPreview(ScanState.NotFound("5901234123457", OnlineStatus.NEEDS_CONSENT))

@PhonePreviews
@Composable
private fun ScannerOnlineFoundPreview() = ScannerPreview(ScanState.Found(previewFood, FoodOrigin.ONLINE))

@PhonePreviews
@Composable
private fun ScannerUnreachablePreview() = ScannerPreview(ScanState.NotFound("5901234123457", OnlineStatus.UNREACHABLE))
