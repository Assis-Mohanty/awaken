package com.assismohanty.awaken.core.data.food

import com.assismohanty.awaken.BuildConfig
import com.assismohanty.awaken.core.data.catalog.Food
import com.assismohanty.awaken.core.data.catalog.FoodSource
import com.assismohanty.awaken.core.data.catalog.Portion
import com.assismohanty.awaken.core.data.di.IoDispatcher
import com.assismohanty.awaken.engine.body.LabelMath
import com.assismohanty.awaken.engine.body.LabelPlausibility
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import timber.log.Timber

/** What the online list said about one barcode. */
sealed interface OnlineLookup {
    data class Found(val food: Food) : OnlineLookup

    /** The list does not have this barcode. */
    data object NotListed : OnlineLookup

    /** The list has it, but with no name or no numbers that can be believed ([LabelPlausibility]). */
    data object Unusable : OnlineLookup

    /** No answer: no connection, a timeout, or the service had a problem. */
    data object Unreachable : OnlineLookup
}

/** A place to look a barcode up when the phone does not know it. */
fun interface OnlineFoodSource {
    suspend fun lookup(barcode: String): OnlineLookup
}

/**
 * Open Food Facts product lookup (docs/diet/PLAN.md section 6.2, option b; ENGINE_DECISIONS #204). One HTTPS request per
 * scan, only when the hunter has switched online lookup on, and the only thing sent is the 13-digit barcode. No account, no
 * key, no contact details: the request identifies the app by name and version only. The reply is capped in size and judged
 * by `LabelPlausibility` before anything is shown. Data (c) Open Food Facts contributors, ODbL.
 */
@Singleton
class OpenFoodFactsClient internal constructor(
    private val ioDispatcher: CoroutineDispatcher,
    private val baseUrl: String,
    private val userAgent: String,
) : OnlineFoodSource {

    @Inject
    constructor(@IoDispatcher ioDispatcher: CoroutineDispatcher) : this(ioDispatcher, BASE_URL, "AWAKEN/${BuildConfig.VERSION_NAME}")

    override suspend fun lookup(barcode: String): OnlineLookup {
        if (!BARCODE.matches(barcode)) return OnlineLookup.NotListed
        return withContext(ioDispatcher) {
            try {
                val body = fetch(barcode) ?: return@withContext OnlineLookup.Unreachable
                parse(barcode, body)
            } catch (e: IOException) {
                Timber.i("Online food lookup failed: %s", e.javaClass.simpleName)
                OnlineLookup.Unreachable
            } catch (e: IllegalArgumentException) {
                Timber.i("Online food lookup returned something unreadable")
                OnlineLookup.Unusable
            }
        }
    }

    /** The response text, or null when the service answered with an error. */
    private fun fetch(barcode: String): String? {
        val connection = URL("$baseUrl/api/v2/product/$barcode.json?fields=$FIELDS").openConnection() as HttpURLConnection
        try {
            connection.connectTimeout = TIMEOUT_MS
            connection.readTimeout = TIMEOUT_MS
            connection.requestMethod = "GET"
            connection.setRequestProperty("User-Agent", userAgent)
            connection.setRequestProperty("Accept", "application/json")
            // The service answers an unknown barcode with 404, which is an answer ("not listed"), not a failure.
            if (connection.responseCode == HttpURLConnection.HTTP_NOT_FOUND) return NOT_LISTED_REPLY
            if (connection.responseCode != HttpURLConnection.HTTP_OK) return null
            return connection.inputStream.use { stream ->
                // Read at most MAX_BYTES (one byte more means the reply is too big); not InputStream.readNBytes, which needs Android 13.
                val out = java.io.ByteArrayOutputStream()
                val buffer = ByteArray(READ_CHUNK)
                while (true) {
                    val n = stream.read(buffer)
                    if (n < 0) break
                    out.write(buffer, 0, n)
                    if (out.size() > MAX_BYTES) throw IOException("reply too large")
                }
                out.toString(Charsets.UTF_8.name())
            }
        } finally {
            connection.disconnect()
        }
    }

    private fun parse(barcode: String, text: String): OnlineLookup {
        val root = Json.parseToJsonElement(text).jsonObject
        if (root["status"]?.jsonPrimitive?.intOrNull != 1) return OnlineLookup.NotListed
        val product = root["product"]?.jsonObject ?: return OnlineLookup.NotListed
        val code = product["code"]?.jsonPrimitive?.content ?: root["code"]?.jsonPrimitive?.content
        // A reply about a different product must never be shown for this one (leading zeros differ between UPC and EAN forms).
        if (code == null || code.trimStart('0') != barcode.trimStart('0')) return OnlineLookup.Unusable

        val name = (product.text("product_name") ?: product.text("product_name_en"))?.replace(WHITESPACE, " ")?.trim()
        if (name == null || name.length !in MIN_NAME..MAX_NAME) return OnlineLookup.Unusable
        val brand = product.text("brands")?.substringBefore(',')?.trim()?.take(MAX_BRAND)?.takeIf { it.isNotEmpty() }

        val n = product["nutriments"] as? JsonObject ?: return OnlineLookup.Unusable
        val kcal = n.number("energy-kcal_100g") ?: n.number("energy_100g")?.let(LabelMath::kcalFromKj)
        val sodiumMg = n.number("sodium_100g")?.let { it * MG_PER_G } ?: n.number("salt_100g")?.let(LabelMath::sodiumMgFromSalt)
        val verdict = LabelPlausibility.check(
            LabelPlausibility.Label(
                kcal = kcal, protein = n.number("proteins_100g"), carbs = n.number("carbohydrates_100g"), fat = n.number("fat_100g"),
                sugars = n.number("sugars_100g"), satFat = n.number("saturated-fat_100g"), fiber = n.number("fiber_100g"), sodiumMg = sodiumMg,
            ),
        )
        val per100 = (verdict as? LabelPlausibility.Verdict.Accepted)?.per100 ?: return OnlineLookup.Unusable

        val serving = product["serving_quantity"]?.let { (it as? JsonPrimitive)?.doubleOrNull ?: (it as? JsonPrimitive)?.content?.toDoubleOrNull() }
        return OnlineLookup.Found(
            Food(
                id = "$ID_PREFIX$barcode",
                name = if (brand == null) name else "$name ($brand)",
                source = FoodSource.CUSTOM,
                per100 = per100,
                portions = listOfNotNull(serving?.takeIf { it > 0.0 && it <= MAX_SERVING_GRAMS }?.let { Portion(SERVING_LABEL, it) }),
            ),
        )
    }

    private fun JsonObject.text(key: String): String? = (this[key] as? JsonPrimitive)?.content?.takeIf { it.isNotBlank() }

    /** A number, whether the service wrote it as a number or as text. */
    private fun JsonObject.number(key: String): Double? = (this[key] as? JsonPrimitive)?.let { it.doubleOrNull ?: it.content.toDoubleOrNull() }

    companion object {
        const val BASE_URL = "https://world.openfoodfacts.org"
        const val ID_PREFIX = "off:"
        const val SERVING_LABEL = "1 serving"
        private val BARCODE = Regex("""\d{13}""")
        private val WHITESPACE = Regex("""\s+""")
        private const val NOT_LISTED_REPLY = """{"status":0}"""
        private const val FIELDS = "code,product_name,product_name_en,brands,serving_quantity,nutriments"
        private const val TIMEOUT_MS = 8_000
        private const val MAX_BYTES = 256 * 1024
        private const val READ_CHUNK = 8 * 1024
        private const val MIN_NAME = 2
        private const val MAX_NAME = 120
        private const val MAX_BRAND = 40
        private const val MAX_SERVING_GRAMS = 1_000.0
        private const val MG_PER_G = 1_000.0
    }
}
