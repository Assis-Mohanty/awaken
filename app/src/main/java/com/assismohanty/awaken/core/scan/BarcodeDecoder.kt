package com.assismohanty.awaken.core.scan

import com.google.zxing.BarcodeFormat
import com.google.zxing.BinaryBitmap
import com.google.zxing.DecodeHintType
import com.google.zxing.MultiFormatReader
import com.google.zxing.PlanarYUVLuminanceSource
import com.google.zxing.common.HybridBinarizer

/** A code the camera read. [upcE] is true when the scanner reported the short UPC-E form (see `Barcode.canonical`). */
data class ScannedCode(val raw: String, val upcE: Boolean)

/**
 * Reads EAN-13, EAN-8, UPC-A and UPC-E from the luminance plane of one camera frame, on the phone (ZXing core, no network,
 * no Google services). A frame is tried as it comes and turned a quarter, because the sensor often delivers it sideways.
 * Not thread-safe: use one decoder per analysis thread.
 */
class BarcodeDecoder {
    private val reader = MultiFormatReader().apply {
        setHints(mapOf(DecodeHintType.POSSIBLE_FORMATS to listOf(BarcodeFormat.EAN_13, BarcodeFormat.EAN_8, BarcodeFormat.UPC_A, BarcodeFormat.UPC_E)))
    }

    /** [luma] holds [width] x [height] brightness bytes, row by row. Null when no valid code is in the frame. */
    fun decode(luma: ByteArray, width: Int, height: Int): ScannedCode? =
        read(luma, width, height) ?: read(turned(luma, width, height), height, width)

    private fun read(luma: ByteArray, width: Int, height: Int): ScannedCode? = try {
        val source = PlanarYUVLuminanceSource(luma, width, height, 0, 0, width, height, false)
        val result = reader.decodeWithState(BinaryBitmap(HybridBinarizer(source)))
        ScannedCode(result.text, upcE = result.barcodeFormat == BarcodeFormat.UPC_E)
    } catch (e: com.google.zxing.ReaderException) {
        null // no code in this frame: the normal case while the camera moves
    } finally {
        reader.reset()
    }

    private fun turned(luma: ByteArray, width: Int, height: Int): ByteArray {
        val out = ByteArray(luma.size)
        for (y in 0 until height) for (x in 0 until width) out[x * height + (height - 1 - y)] = luma[y * width + x]
        return out
    }
}
