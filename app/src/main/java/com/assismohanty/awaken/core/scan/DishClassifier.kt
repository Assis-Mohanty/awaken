package com.assismohanty.awaken.core.scan

import android.content.Context
import android.graphics.Bitmap
import com.assismohanty.awaken.core.data.di.IoDispatcher
import com.assismohanty.awaken.engine.body.DishScan
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import java.nio.ByteBuffer
import java.nio.ByteOrder
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.tensorflow.lite.DataType
import org.tensorflow.lite.Interpreter
import timber.log.Timber

/** Names the dishes in a photo (Diet photo scanner, plan section 5). Labels and scores only: no number reaches the diary. */
interface DishClassifier {
    /** True when a model ships in this build. With none, the Photo button is never shown. */
    suspend fun available(): Boolean

    /** Each label's foods in the catalog, as `assets/scanner/labels.json` lists them; empty with no model. */
    suspend fun labelFoods(): Map<String, List<String>>

    /** The [n] best labels for [photo], or null when the model could not run. The photo is not kept. */
    suspend fun top(photo: Bitmap, n: Int = DishScan.MAX_SUGGESTIONS): List<DishScan.Guess>?
}

/**
 * LiteRT on the phone (plan 5.2; ENGINE_DECISIONS #206): `assets/scanner/dishes.tflite` and `labels.json`
 * (`{"labels": [...], "foods": {"label": ["food id", ...]}}`, the labels in the model's output order). **No model ships
 * today**, so [available] is false and the scanner stays hidden whatever the flag says. CPU only, one photo at a time.
 * The model is read as a stream (assets are compressed in the APK, so `openFd` would fail on a phone; ENGINE_DECISIONS #205).
 */
@Singleton
class LiteRtDishClassifier @Inject constructor(
    @ApplicationContext private val context: Context,
    @IoDispatcher private val ioDispatcher: CoroutineDispatcher,
) : DishClassifier {
    private class Loaded(val interpreter: Interpreter, val labels: List<String>, val foods: Map<String, List<String>>)

    private val lock = Mutex()
    private var loaded: Loaded? = null
    private var tried = false

    override suspend fun available(): Boolean = model() != null

    override suspend fun labelFoods(): Map<String, List<String>> = model()?.foods.orEmpty()

    override suspend fun top(photo: Bitmap, n: Int): List<DishScan.Guess>? = withContext(ioDispatcher) {
        lock.withLock {
            val m = load() ?: return@withLock null
            runCatching { classify(m, photo, n) }.onFailure { Timber.e(it, "Dish classifier failed") }.getOrNull()
        }
    }

    private suspend fun model(): Loaded? = withContext(ioDispatcher) { lock.withLock { load() } }

    /** Called under [lock]. A missing or broken model is tried once, then the scanner is simply absent. */
    private fun load(): Loaded? {
        if (tried) return loaded
        tried = true
        loaded = runCatching {
            val files = context.assets.list(DIR).orEmpty()
            if (MODEL !in files || LABELS !in files) return null
            val bytes = context.assets.open("$DIR/$MODEL").use { it.readBytes() }
            val buffer = ByteBuffer.allocateDirect(bytes.size).order(ByteOrder.nativeOrder()).put(bytes).also { it.rewind() }
            val json = Json.parseToJsonElement(context.assets.open("$DIR/$LABELS").bufferedReader().use { it.readText() }).jsonObject
            val labels = json.getValue("labels").jsonArray.map { it.jsonPrimitive.content }
            val foods = json["foods"]?.jsonObject.orEmpty().mapValues { (_, v) -> v.jsonArray.map { it.jsonPrimitive.content } }
            val interpreter = Interpreter(buffer, Interpreter.Options().setNumThreads(THREADS))
            check(interpreter.getOutputTensor(0).shape().last() == labels.size) { "labels.json does not match the model's outputs" }
            Loaded(interpreter, labels, foods)
        }.onFailure { Timber.e(it, "Dish model could not load") }.getOrNull()
        return loaded
    }

    private fun classify(m: Loaded, photo: Bitmap, n: Int): List<DishScan.Guess> {
        val input = m.interpreter.getInputTensor(0)
        val (height, width) = input.shape().let { it[1] to it[2] }
        val scaled = Bitmap.createScaledBitmap(photo, width, height, true)
        val pixels = IntArray(width * height).also { scaled.getPixels(it, 0, width, 0, 0, width, height) }
        if (scaled !== photo) scaled.recycle()
        val type = input.dataType()
        val inBuffer = ByteBuffer.allocateDirect(width * height * 3 * type.byteSize()).order(ByteOrder.nativeOrder())
        for (p in pixels) {
            for (channel in intArrayOf(p shr 16 and 0xFF, p shr 8 and 0xFF, p and 0xFF)) {
                // shortcut: EfficientNet-Lite input conventions (raw bytes when quantised, (v - 127) / 128 when float); read the model's metadata if a different model family is used.
                when (type) {
                    DataType.UINT8 -> inBuffer.put(channel.toByte())
                    DataType.INT8 -> inBuffer.put((channel - 128).toByte())
                    else -> inBuffer.putFloat((channel - 127f) / 128f)
                }
            }
        }
        inBuffer.rewind()
        val output = m.interpreter.getOutputTensor(0)
        val outBuffer = ByteBuffer.allocateDirect(m.labels.size * output.dataType().byteSize()).order(ByteOrder.nativeOrder())
        m.interpreter.run(inBuffer, outBuffer)
        outBuffer.rewind()
        val q = output.quantizationParams()
        val scores = DoubleArray(m.labels.size) {
            when (output.dataType()) {
                DataType.UINT8 -> ((outBuffer.get().toInt() and 0xFF) - q.zeroPoint) * q.scale.toDouble()
                DataType.INT8 -> (outBuffer.get().toInt() - q.zeroPoint) * q.scale.toDouble()
                else -> outBuffer.getFloat().toDouble()
            }
        }
        return scores.indices.sortedByDescending { scores[it] }.take(n).map { DishScan.Guess(m.labels[it], scores[it]) }
    }

    private companion object {
        const val DIR = "scanner"
        const val MODEL = "dishes.tflite"
        const val LABELS = "labels.json"
        const val THREADS = 2
    }
}

@Module
@InstallIn(SingletonComponent::class)
abstract class DishClassifierModule {
    @Binds
    abstract fun bindDishClassifier(classifier: LiteRtDishClassifier): DishClassifier
}
