package com.assismohanty.awaken.testing

import android.graphics.Bitmap
import com.assismohanty.awaken.core.scan.LabelReader
import com.assismohanty.awaken.engine.body.OcrWord

/** A reader that returns the words the test gives it and remembers the photos it was shown (nothing is really read). */
class FakeLabelReader : LabelReader {
    /** What the next read returns; null means the reader could not run. */
    var words: List<OcrWord>? = emptyList()
    val photos = mutableListOf<Bitmap>()

    override suspend fun read(photo: Bitmap): List<OcrWord>? {
        photos += photo
        return words
    }

    companion object {
        /** A small nutrition table (per 100 g and per serve (30 g) columns) as the reader would return it. */
        fun biscuitTable(): List<OcrWord> {
            fun w(text: String, left: Int, row: Int, width: Int = 60) = OcrWord(text, left, 20 + row * 40, left + width, 44 + row * 40, 95)
            fun row(r: Int, name: String, a: String, b: String) = listOf(w(name, 10, r, 220), w(a, 380, r), w(b, 560, r))
            return listOf(w("Per", 380, 0, 30), w("100", 414, 0, 36), w("g", 454, 0, 16), w("Per", 560, 0, 30), w("serve", 594, 0, 56), w("(30", 654, 0, 36), w("g)", 694, 0, 16)) +
                row(1, "Energy (kcal)", "480", "144") + row(2, "Protein (g)", "7.0", "2.1") + row(3, "Carbohydrate (g)", "64.0", "19.2") +
                row(4, "Total Fat (g)", "21.0", "6.3") + row(5, "Sodium (mg)", "400", "120")
        }
    }
}
