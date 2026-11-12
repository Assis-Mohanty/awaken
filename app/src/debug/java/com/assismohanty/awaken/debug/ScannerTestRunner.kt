package com.assismohanty.awaken.debug

import android.content.Context
import android.graphics.BitmapFactory
import com.assismohanty.awaken.core.data.di.IoDispatcher
import com.assismohanty.awaken.core.domain.BodyLog
import com.assismohanty.awaken.core.scan.DishClassifier
import com.assismohanty.awaken.engine.body.DishScan
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import javax.inject.Inject
import kotlin.math.max
import kotlin.math.sqrt
import kotlin.time.measureTimedValue
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext

/**
 * The photo scanner's go / no-go run (docs/diet/SCANNER_TEST.md, GD 20.6). Debug builds only. Reads
 * `Android/data/<app>/files/scanner-test/expected.csv` (`file,food_id` per line) and the photos beside it, runs the
 * model on each, writes `results.csv` next to them and returns the verdict. Nothing leaves the phone.
 */
class ScannerTestRunner @Inject constructor(
    @ApplicationContext private val context: Context,
    private val classifier: DishClassifier,
    private val bodyLog: BodyLog,
    @IoDispatcher private val ioDispatcher: CoroutineDispatcher,
) {
    suspend fun run(): String = withContext(ioDispatcher) {
        val (scans, hits) = bodyLog.photoScanTally()
        val tally = "Scanner use on this phone: $scans photo scans, a suggested food chosen in $hits."
        val dir = context.getExternalFilesDir(DIR) ?: return@withContext "No app storage folder. $tally"
        val expected = File(dir, EXPECTED)
        if (!classifier.available()) return@withContext "No model in this build (assets/scanner/dishes.tflite). $tally"
        if (!expected.exists()) return@withContext "Put the photos and expected.csv in ${dir.absolutePath} first. $tally"

        val labelFoods = classifier.labelFoods()
        val photos = mutableListOf<DishScan.TestPhoto>()
        val rows = mutableListOf("file,expected,top3_labels,top3_scores,suggested_foods,millis")
        val missing = mutableListOf<String>()
        for (line in expected.readLines().map { it.trim() }.filter { it.isNotEmpty() && !it.startsWith("file,") }) {
            val (name, foodId) = line.split(',', limit = 2).map { it.trim() }.let { it[0] to it.getOrElse(1) { "" } }
            val bitmap = File(dir, name).takeIf { it.exists() }?.let { decode(it) }
            if (bitmap == null) {
                missing += name
                continue
            }
            val (guesses, took) = measureTimedValue { classifier.top(bitmap) }
            bitmap.recycle()
            val list = guesses.orEmpty()
            val ms = took.inWholeMilliseconds
            photos += DishScan.TestPhoto(foodId, list, ms)
            val suggested = (DishScan.suggest(list, labelFoods) as? DishScan.Suggestions.Foods)?.foodIds.orEmpty()
            rows += listOf(name, foodId, list.joinToString(" ") { it.label }, list.joinToString(" ") { "%.3f".format(it.score) }, suggested.joinToString(" "), ms.toString())
                .joinToString(",")
        }
        File(dir, RESULTS).writeText(rows.joinToString("\n", postfix = "\n"))
        val v = DishScan.judge(photos, labelFoods)
        buildString {
            appendLine(if (v.ships) "GO: every limit in GD 20.6 holds." else "NO-GO: at least one limit in GD 20.6 fails.")
            appendLine("${v.photos} photos (${v.inSet} of dishes in the label set); results.csv written to ${dir.absolutePath}")
            appendLine("Top-3 hits, in set: ${pct(v.hitRateInSet)} (need ${pct(DishScan.Ship.MIN_HIT_RATE_IN_SET)}) ${interval(v.hitRateInSet, v.inSet)}")
            appendLine("Top-3 hits, all: ${pct(v.hitRateOverall)} (need ${pct(DishScan.Ship.MIN_HIT_RATE_OVERALL)}) ${interval(v.hitRateOverall, v.photos)}")
            appendLine("Median ${v.medianMillis} ms (under ${DishScan.Ship.MEDIAN_UNDER_MILLIS}), slowest ${v.slowestMillis} ms (under ${DishScan.Ship.SLOWEST_UNDER_MILLIS})")
            appendLine("Confident guesses on dishes outside the set: ${pct(v.confidentOutOfSet)} (at most ${pct(DishScan.Ship.MAX_CONFIDENT_OUT_OF_SET)})")
            if (missing.isNotEmpty()) appendLine("Skipped, file missing or unreadable: ${missing.joinToString()}")
            append(tally)
        }
    }

    /** Shrinks on decode to about the classifier's working size, so 200 phone photos never sit in memory at full size. */
    private fun decode(file: File) = runCatching {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(file.absolutePath, bounds)
        var sample = 1
        while (max(bounds.outWidth, bounds.outHeight) / (sample * 2) >= DECODE_SIDE) sample *= 2
        BitmapFactory.decodeFile(file.absolutePath, BitmapFactory.Options().apply { inSampleSize = sample })
    }.getOrNull()

    private fun pct(rate: Double?) = rate?.let { "%.1f %%".format(it * 100) } ?: "n/a"

    /** A 95 % range for the true rate, shown so a result near a limit is read with care (plan 5.5); not part of the verdict. */
    private fun interval(rate: Double?, n: Int): String {
        if (rate == null || n == 0) return ""
        val margin = Z95 * sqrt(rate * (1 - rate) / n)
        return "(95 %% range %s to %s)".format(pct((rate - margin).coerceAtLeast(0.0)), pct((rate + margin).coerceAtMost(1.0)))
    }

    private companion object {
        const val DIR = "scanner-test"
        const val EXPECTED = "expected.csv"
        const val RESULTS = "results.csv"
        const val DECODE_SIDE = 448
        const val Z95 = 1.96
    }
}
