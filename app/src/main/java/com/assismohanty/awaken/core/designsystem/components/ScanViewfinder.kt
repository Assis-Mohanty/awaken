package com.assismohanty.awaken.core.designsystem.components

import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import com.assismohanty.awaken.core.designsystem.theme.AwakenTheme
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import timber.log.Timber

/**
 * The camera view for scanning a code (Diet v2 plan section 6.1): a live preview with a frame to aim at. Each camera frame's
 * brightness plane goes to [onFrame] (called on a background thread; width and height are the frame's own). Nothing is
 * recorded, saved or sent. The camera starts when this enters the screen and is released when it leaves.
 */
@Composable
fun ScanViewfinder(onFrame: (luma: ByteArray, width: Int, height: Int) -> Unit, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val latestOnFrame by rememberUpdatedState(onFrame)
    val previewView = androidx.compose.runtime.remember { PreviewView(context).apply { scaleType = PreviewView.ScaleType.FILL_CENTER } }

    DisposableEffect(lifecycleOwner, previewView) {
        val analysisExecutor: ExecutorService = Executors.newSingleThreadExecutor()
        val providerFuture = ProcessCameraProvider.getInstance(context)
        var provider: ProcessCameraProvider? = null
        providerFuture.addListener(
            {
                try {
                    val cameraProvider = providerFuture.get().also { provider = it }
                    val preview = Preview.Builder().build().also { it.surfaceProvider = previewView.surfaceProvider }
                    val analysis = ImageAnalysis.Builder()
                        .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                        .build()
                        .also { use ->
                            use.setAnalyzer(analysisExecutor) { image ->
                                try {
                                    latestOnFrame(image.lumaBytes(), image.width, image.height)
                                } finally {
                                    image.close()
                                }
                            }
                        }
                    cameraProvider.unbindAll()
                    cameraProvider.bindToLifecycle(lifecycleOwner, CameraSelector.DEFAULT_BACK_CAMERA, preview, analysis)
                } catch (e: Exception) {
                    Timber.w(e, "The camera could not start")
                }
            },
            ContextCompat.getMainExecutor(context),
        )
        onDispose {
            provider?.unbindAll()
            analysisExecutor.shutdown()
        }
    }

    Box(modifier = modifier) {
        AndroidView(factory = { previewView }, modifier = Modifier.fillMaxSize())
        ScanFrame(Modifier.fillMaxSize())
    }
}

/** The aiming frame on its own, so previews and tests can show the scanner without a camera. */
@Composable
fun ScanFrame(modifier: Modifier = Modifier) {
    val color = AwakenTheme.colors.textPrimary
    Canvas(modifier = modifier) {
        val width = size.width * FRAME_WIDTH_SHARE
        val height = width * FRAME_HEIGHT_SHARE
        drawRoundRect(
            color = color,
            topLeft = Offset((size.width - width) / 2, (size.height - height) / 2),
            size = Size(width, height),
            cornerRadius = CornerRadius(FRAME_CORNER),
            style = Stroke(width = FRAME_STROKE),
        )
    }
}

/** The brightness plane as a tight row-by-row array, whatever the row stride of the camera buffer. */
private fun ImageProxy.lumaBytes(): ByteArray {
    val plane = planes[0]
    val buffer = plane.buffer.apply { rewind() }
    val out = ByteArray(width * height)
    if (plane.rowStride == width) {
        buffer.get(out, 0, out.size.coerceAtMost(buffer.remaining()))
    } else {
        for (row in 0 until height) {
            buffer.position(row * plane.rowStride)
            buffer.get(out, row * width, width)
        }
    }
    return out
}

private const val FRAME_WIDTH_SHARE = 0.8f
private const val FRAME_HEIGHT_SHARE = 0.5f
private const val FRAME_CORNER = 24f
private const val FRAME_STROKE = 6f
