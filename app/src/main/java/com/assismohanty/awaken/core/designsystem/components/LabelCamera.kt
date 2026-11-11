package com.assismohanty.awaken.core.designsystem.components

import android.graphics.Bitmap
import android.graphics.Matrix
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.ImageProxy
import androidx.camera.view.CameraController
import androidx.camera.view.LifecycleCameraController
import androidx.camera.view.PreviewView
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import timber.log.Timber

/** The shutter of a [LabelCamera]: ask it for a photo. Does nothing (gives null) while no camera is showing. */
class LabelShutter {
    internal var take: ((Bitmap?) -> Unit) -> Unit = { it(null) }

    /** Takes one photo and hands it to [onResult], upright; null when the camera could not take it. */
    fun capture(onResult: (Bitmap?) -> Unit) = take(onResult)
}

/**
 * A live camera view for photographing a nutrition table (Diet v2, "add it from the label"). The photo goes to [LabelShutter]'s
 * caller and nowhere else: it is not saved to the phone and not sent. The camera starts when this enters the screen and is
 * released when it leaves.
 */
@Composable
fun LabelCamera(shutter: LabelShutter, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val controller = remember { LifecycleCameraController(context).apply { setEnabledUseCases(CameraController.IMAGE_CAPTURE) } }

    DisposableEffect(lifecycleOwner, controller) {
        controller.bindToLifecycle(lifecycleOwner)
        shutter.take = { callback ->
            controller.takePicture(
                ContextCompat.getMainExecutor(context),
                object : ImageCapture.OnImageCapturedCallback() {
                    override fun onCaptureSuccess(image: ImageProxy) {
                        val photo = try {
                            image.toBitmap().upright(image.imageInfo.rotationDegrees)
                        } catch (e: RuntimeException) {
                            Timber.w(e, "The photo could not be read")
                            null
                        } finally {
                            image.close()
                        }
                        callback(photo)
                    }

                    override fun onError(exception: ImageCaptureException) {
                        Timber.w(exception, "The camera could not take the photo")
                        callback(null)
                    }
                },
            )
        }
        onDispose {
            shutter.take = { it(null) }
            controller.unbind()
        }
    }

    AndroidView(
        factory = { PreviewView(it).apply { this.controller = controller; scaleType = PreviewView.ScaleType.FILL_CENTER } },
        modifier = modifier,
    )
}

private fun Bitmap.upright(rotationDegrees: Int): Bitmap {
    if (rotationDegrees == 0) return this
    val rotated = Bitmap.createBitmap(this, 0, 0, width, height, Matrix().apply { postRotate(rotationDegrees.toFloat()) }, true)
    if (rotated !== this) recycle()
    return rotated
}
