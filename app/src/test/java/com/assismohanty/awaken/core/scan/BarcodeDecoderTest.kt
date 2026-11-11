package com.assismohanty.awaken.core.scan

import com.google.common.truth.Truth.assertThat
import com.google.zxing.BarcodeFormat
import com.google.zxing.MultiFormatWriter
import com.google.zxing.common.BitMatrix
import org.junit.Test

/** The decoder reads real barcodes, drawn by ZXing's own encoder, in the orientations a phone camera delivers them. */
class BarcodeDecoderTest {
    private val decoder = BarcodeDecoder()

    /** A frame: the code on a white background with a quiet margin, as brightness bytes (0 black, 255 white). */
    private fun frame(text: String, format: BarcodeFormat, sideways: Boolean = false): Triple<ByteArray, Int, Int> {
        val matrix: BitMatrix = MultiFormatWriter().encode(text, format, CODE_WIDTH, CODE_HEIGHT)
        val w = matrix.width + 2 * MARGIN
        val h = matrix.height + 2 * MARGIN
        val upright = ByteArray(w * h) { 255.toByte() }
        for (y in 0 until matrix.height) for (x in 0 until matrix.width) if (matrix[x, y]) upright[(y + MARGIN) * w + x + MARGIN] = 0
        if (!sideways) return Triple(upright, w, h)
        val turned = ByteArray(upright.size)
        for (y in 0 until h) for (x in 0 until w) turned[x * h + y] = upright[y * w + x]
        return Triple(turned, h, w)
    }

    @Test
    fun anEan13IsReadUpright() {
        val (luma, w, h) = frame("5901234123457", BarcodeFormat.EAN_13)
        assertThat(decoder.decode(luma, w, h)).isEqualTo(ScannedCode("5901234123457", upcE = false))
    }

    @Test
    fun aSidewaysFrameIsReadToo() {
        val (luma, w, h) = frame("5901234123457", BarcodeFormat.EAN_13, sideways = true)
        assertThat(decoder.decode(luma, w, h)?.raw).isEqualTo("5901234123457")
    }

    @Test
    fun upcAAndEan8AreRead() {
        val (a, aw, ah) = frame("036000291452", BarcodeFormat.UPC_A)
        assertThat(decoder.decode(a, aw, ah)?.raw).isAnyOf("036000291452", "0036000291452")
        val (e, ew, eh) = frame("96385074", BarcodeFormat.EAN_8)
        assertThat(decoder.decode(e, ew, eh)).isEqualTo(ScannedCode("96385074", upcE = false))
    }

    @Test
    fun aUpcEIsFlaggedSoTheShortFormCanBeExpanded() {
        val (luma, w, h) = frame("01234565", BarcodeFormat.UPC_E)
        assertThat(decoder.decode(luma, w, h)?.upcE).isTrue()
    }

    @Test
    fun anEmptyFrameFindsNothingAndTheDecoderKeepsWorking() {
        assertThat(decoder.decode(ByteArray(200 * 100) { 255.toByte() }, 200, 100)).isNull()
        val (luma, w, h) = frame("5901234123457", BarcodeFormat.EAN_13)
        assertThat(decoder.decode(luma, w, h)).isNotNull()
    }

    private companion object {
        const val CODE_WIDTH = 300
        const val CODE_HEIGHT = 120
        const val MARGIN = 30
    }
}
