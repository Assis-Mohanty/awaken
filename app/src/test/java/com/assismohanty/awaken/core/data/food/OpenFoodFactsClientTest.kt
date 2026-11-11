package com.assismohanty.awaken.core.data.food

import com.google.common.truth.Truth.assertThat
import com.sun.net.httpserver.HttpServer
import java.net.InetSocketAddress
import java.util.concurrent.CopyOnWriteArrayList
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Before
import org.junit.Test

/** The Open Food Facts client against a local server: what it asks for, what it accepts, and how it fails (ENGINE_DECISIONS 204). */
class OpenFoodFactsClientTest {
    private lateinit var server: HttpServer
    private val requests = CopyOnWriteArrayList<Pair<String, String?>>() // path and query, User-Agent
    private var status = 200
    private var body = ""

    private val barcode = "8906120107181"

    private val found = """
        {"code":"8906120107181","status":1,"status_verbose":"product found","product":{"code":"8906120107181","product_name":"Raw Sunflower Seeds",
        "brands":"Farmley, Other","serving_quantity":20,"nutriments":{"energy-kcal_100g":625,"energy_100g":2615,"proteins_100g":23.1,
        "carbohydrates_100g":20.1,"fat_100g":48,"sugars_100g":5.7,"saturated-fat_100g":4.6,"fiber_100g":10.1,"sodium_100g":0.0199}}}
    """.trimIndent()

    @Before
    fun start() {
        server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)
        server.createContext("/") { exchange ->
            requests += "${exchange.requestURI.path}?${exchange.requestURI.rawQuery}" to exchange.requestHeaders.getFirst("User-Agent")
            val bytes = body.toByteArray()
            exchange.sendResponseHeaders(status, bytes.size.toLong())
            exchange.responseBody.use { it.write(bytes) }
        }
        server.start()
    }

    @After
    fun stop() = server.stop(0)

    private fun client(base: String = "http://127.0.0.1:${server.address.port}") = OpenFoodFactsClient(Dispatchers.Unconfined, base, "AWAKEN/1.0")

    @Test
    fun aListedProductBecomesAFoodWithItsServingAndSodiumInMilligrams() = runBlocking<Unit> {
        body = found

        val result = client().lookup(barcode) as OnlineLookup.Found

        val food = result.food
        assertThat(food.id).isEqualTo("off:8906120107181")
        assertThat(food.name).isEqualTo("Raw Sunflower Seeds (Farmley)")
        assertThat(food.per100.values.getValue(com.assismohanty.awaken.engine.body.Nutrient.KCAL)).isEqualTo(625.0)
        assertThat(food.per100.values.getValue(com.assismohanty.awaken.engine.body.Nutrient.SODIUM)).isWithin(1e-6).of(19.9)
        assertThat(food.portions.single().grams).isEqualTo(20.0)
    }

    @Test
    fun theRequestCarriesOnlyTheBarcodeAndAnAppNameWithNoContactDetails() = runBlocking<Unit> {
        body = found
        client().lookup(barcode)

        val (target, agent) = requests.single()
        assertThat(target).startsWith("/api/v2/product/8906120107181.json?fields=")
        assertThat(target).doesNotContain("@")
        assertThat(agent).isEqualTo("AWAKEN/1.0")
        assertThat(agent).doesNotContain("@")
    }

    @Test
    fun anUnknownProductIsNotListed() = runBlocking<Unit> {
        body = """{"code":"8901058851885","status":0,"status_verbose":"product not found"}"""
        assertThat(client().lookup("8901058851885")).isEqualTo(OnlineLookup.NotListed)
    }

    @Test
    fun theRealServiceAnswersAnUnknownBarcodeWith404AndThatIsNotListedNotAnOutage() = runBlocking<Unit> {
        status = 404
        body = """{"code":"8901058851885","status":0,"status_verbose":"product not found"}"""
        assertThat(client().lookup("8901058851885")).isEqualTo(OnlineLookup.NotListed)
    }

    @Test
    fun aServiceErrorOrNoServerMeansUnreachable() = runBlocking<Unit> {
        status = 503
        body = "busy"
        assertThat(client().lookup(barcode)).isEqualTo(OnlineLookup.Unreachable)

        val deadPort = server.address.port
        server.stop(0)
        assertThat(client("http://127.0.0.1:$deadPort").lookup(barcode)).isEqualTo(OnlineLookup.Unreachable)
    }

    @Test
    fun numbersThatCannotBeRightAreUnusableNotShown() = runBlocking<Unit> {
        body = found.replace("\"proteins_100g\":23.1", "\"proteins_100g\":123.1")
        assertThat(client().lookup(barcode)).isEqualTo(OnlineLookup.Unusable)

        body = found.replace("\"energy-kcal_100g\":625", "\"energy-kcal_100g\":120")
        assertThat(client().lookup(barcode)).isEqualTo(OnlineLookup.Unusable)

        body = found.replace("\"carbohydrates_100g\":20.1,", "")
        assertThat(client().lookup(barcode)).isEqualTo(OnlineLookup.Unusable)
    }

    @Test
    fun aNamelessProductOrOneForAnotherCodeIsUnusable() = runBlocking<Unit> {
        body = found.replace("\"product_name\":\"Raw Sunflower Seeds\"", "\"product_name\":\"\"")
        assertThat(client().lookup(barcode)).isEqualTo(OnlineLookup.Unusable)

        body = found.replace("\"code\":\"8906120107181\",\"product_name\"", "\"code\":\"3017620422003\",\"product_name\"")
        assertThat(client().lookup(barcode)).isEqualTo(OnlineLookup.Unusable)
    }

    @Test
    fun kilojoulesAndSaltStandInWhenKilocaloriesAndSodiumAreMissing() = runBlocking<Unit> {
        body = found.replace("\"energy-kcal_100g\":625,", "").replace("\"sodium_100g\":0.0199", "\"salt_100g\":0.05")

        val food = (client().lookup(barcode) as OnlineLookup.Found).food

        assertThat(food.per100.values.getValue(com.assismohanty.awaken.engine.body.Nutrient.KCAL)).isWithin(0.01).of(625.0) // 2615 kJ / 4.184
        assertThat(food.per100.values.getValue(com.assismohanty.awaken.engine.body.Nutrient.SODIUM)).isWithin(0.01).of(20.0) // 0.05 g salt / 2.5
    }

    @Test
    fun aHugeReplyIsRefusedAndAnythingThatIsNotAThirteenDigitCodeNeverGoesOut() = runBlocking<Unit> {
        body = "x".repeat(300 * 1024)
        assertThat(client().lookup(barcode)).isEqualTo(OnlineLookup.Unreachable)

        requests.clear()
        assertThat(client().lookup("12345")).isEqualTo(OnlineLookup.NotListed)
        assertThat(client().lookup("../../etc/passwd")).isEqualTo(OnlineLookup.NotListed)
        assertThat(requests).isEmpty()
    }
}
