package com.assismohanty.awaken.core.data.ai

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.assismohanty.awaken.core.data.ai.dto.ReportRequest
import com.assismohanty.awaken.engine.model.Stat
import com.assismohanty.awaken.engine.path.ConstraintsDto
import com.assismohanty.awaken.engine.path.PathDto
import com.assismohanty.awaken.engine.path.PathPlanRequest
import com.assismohanty.awaken.engine.path.PathWeekRequest
import com.assismohanty.awaken.engine.path.MilestoneDto
import com.assismohanty.awaken.engine.path.ProgressionSignal
import com.google.common.truth.Truth.assertThat
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.MockRequestHandleScope
import io.ktor.client.engine.mock.respond
import io.ktor.client.request.HttpRequestData
import io.ktor.client.request.HttpResponseData
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.content.OutgoingContent
import io.ktor.http.headersOf
import java.io.File
import java.io.IOException
import java.time.Instant
import java.util.UUID
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Test
import org.junit.runner.RunWith

/** Every proxy error code maps to an outcome (ARCH §3.8), and the bytes sent are the bytes hashed (ARCH §3.7). */
@RunWith(AndroidJUnit4::class)
class KtorPathOracleClientTest {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val context = ApplicationProvider.getApplicationContext<android.content.Context>()
    private val prefs = AiPrefsRepository(
        PreferenceDataStoreFactory.create(scope = scope) { File(context.filesDir, "ai-${UUID.randomUUID()}.preferences_pb") },
    )
    private val requests = mutableListOf<HttpRequestData>()

    @After
    fun tearDown() = scope.cancel()

    private val planRequest = PathPlanRequest(
        requestId = "0a1b2c3d-0000-4000-8000-0123456789ab",
        path = PathDto("Learn to juggle", "", "", Stat.STR, null, listOf("MON", "WED"), 1, 90),
        constraints = ConstraintsDto(emptyList(), emptyList()),
    )

    private val weekRequest = PathWeekRequest(
        requestId = "0a1b2c3d-0000-4000-8000-0123456789ac",
        path = planRequest.path,
        milestone = MilestoneDto("Foundations", "Basics.", "Three days."),
        progressionSignal = ProgressionSignal.UP,
        maxBlueprints = 2,
        lastWeek = emptyList(),
        constraints = planRequest.constraints,
    )

    private fun client(
        config: OracleConfig = OracleConfig("https://oracle.test", debugKey = "", cloudProjectNumber = 7),
        token: String? = "integrity-token",
        handler: suspend MockRequestHandleScope.(HttpRequestData) -> HttpResponseData,
    ): KtorPathOracleClient {
        val engine = MockEngine { request ->
            requests += request
            handler(request)
        }
        val integrity = object : IntegrityTokenProvider {
            override suspend fun token(requestHash: String): String? = token?.let { "$it:$requestHash" }
        }
        return KtorPathOracleClient(HttpClient(engine), config, integrity, prefs, OracleTrace(), Dispatchers.Unconfined)
    }

    private fun MockRequestHandleScope.json(status: HttpStatusCode, body: String, vararg headers: Pair<String, String>) = respond(
        content = body,
        status = status,
        headers = headersOf(HttpHeaders.ContentType to listOf("application/json"), *headers.map { it.first to listOf(it.second) }.toTypedArray()),
    )

    private suspend fun consented() = prefs.grantConsent(Instant.parse("2026-10-20T00:00:00Z"))

    private fun HttpRequestData.bodyBytes(): ByteArray = (body as OutgoingContent.ByteArrayContent).bytes()

    @Test
    fun aSuccessfulPlanCarriesTheRawJsonAndPromptVersion() = runTest {
        consented()
        val client = client { json(HttpStatusCode.OK, """{"promptVersion":"plan_path.v1","milestones":[]}""") }

        val outcome = client.planPath(planRequest)

        assertThat(outcome).isEqualTo(
            OracleOutcome.Success(OracleReply("plan_path.v1", """{"promptVersion":"plan_path.v1","milestones":[]}""")),
        )
    }

    @Test
    fun theHashCoversTheExactBytesSentAndTheHeadersAreSet() = runTest {
        val installId = consented()
        val client = client { json(HttpStatusCode.OK, """{"promptVersion":"week_quests.v1","blueprints":[],"suggestAdvance":false}""") }

        client.weekQuests(weekRequest, pathId = "path-1", manualRetry = true)

        val sent = requests.single()
        assertThat(sent.url.toString()).isEqualTo("https://oracle.test/v1/paths/week")
        assertThat(sent.headers["X-Install-Id"]).isEqualTo(installId)
        assertThat(sent.headers["X-Path-Id"]).isEqualTo("path-1")
        assertThat(sent.headers["X-Retry-Kind"]).isEqualTo("manual")
        assertThat(sent.headers["X-Debug-Key"]).isNull()
        // The token is bound to SHA-256(base64) of exactly the bytes that went out.
        val expectedHash = KtorPathOracleClient.requestHash(sent.bodyBytes())
        assertThat(sent.headers["X-Integrity-Token"]).isEqualTo("integrity-token:$expectedHash")
    }

    @Test
    fun automaticWeekCallsDoNotClaimToBeManual() = runTest {
        consented()
        val client = client { json(HttpStatusCode.OK, """{"promptVersion":"week_quests.v1"}""") }

        client.weekQuests(weekRequest, pathId = "path-1", manualRetry = false)

        assertThat(requests.single().headers["X-Retry-Kind"]).isNull()
    }

    @Test
    fun theRequestBodyKeepsExplicitNullsTheContractWants() = runTest {
        consented()
        val client = client { json(HttpStatusCode.OK, """{"promptVersion":"plan_path.v1"}""") }

        client.planPath(planRequest)

        val body = requests.single().bodyBytes().decodeToString()
        assertThat(body).contains("\"secondaryStat\":null")
        assertThat(body).contains("\"requestId\":\"0a1b2c3d-0000-4000-8000-0123456789ab\"")
    }

    @Test
    fun debugBuildsSendTheDebugKeyInsteadOfAnIntegrityToken() = runTest {
        consented()
        val client = client(config = OracleConfig("https://oracle.test", debugKey = "dbg", cloudProjectNumber = 0)) {
            json(HttpStatusCode.OK, """{"promptVersion":"plan_path.v1"}""")
        }

        client.planPath(planRequest)

        assertThat(requests.single().headers["X-Debug-Key"]).isEqualTo("dbg")
        assertThat(requests.single().headers["X-Integrity-Token"]).isNull()
    }

    @Test
    fun everyProxyErrorCodeMapsToItsOutcome() = runTest {
        consented()
        val cases = mapOf(
            400 to ("invalid_request" to OracleOutcome.InvalidRequest),
            401 to ("integrity_failed" to OracleOutcome.IntegrityFailed),
            502 to ("model_invalid_output" to OracleOutcome.ModelInvalidOutput),
            503 to ("budget_exhausted" to OracleOutcome.BudgetExhausted),
            500 to ("internal" to OracleOutcome.ServerError),
        )
        cases.forEach { (status, expected) ->
            val client = client { json(HttpStatusCode.fromValue(status), """{"error":"${expected.first}","message":"x"}""") }
            assertThat(client.planPath(planRequest)).isEqualTo(expected.second)
        }
    }

    @Test
    fun rateLimitingKeepsTheRetryAfter() = runTest {
        consented()
        val client = client { json(HttpStatusCode.TooManyRequests, """{"error":"rate_limited","message":"x"}""", HttpHeaders.RetryAfter to "3600") }

        assertThat(client.planPath(planRequest)).isEqualTo(OracleOutcome.RateLimited(3600))
    }

    @Test
    fun networkFailuresAreOffline() = runTest {
        consented()
        val client = client { throw IOException("no route to host") }

        assertThat(client.planPath(planRequest)).isEqualTo(OracleOutcome.Offline)
    }

    @Test
    fun aSuccessWithoutAPromptVersionIsAServerError() = runTest {
        consented()
        val client = client { json(HttpStatusCode.OK, """{"milestones":[]}""") }

        assertThat(client.planPath(planRequest)).isEqualTo(OracleOutcome.ServerError)
    }

    @Test
    fun nothingIsSentWithoutConsentOrAnEndpoint() = runTest {
        val noConsent = client { json(HttpStatusCode.OK, "{}") }
        assertThat(noConsent.planPath(planRequest)).isEqualTo(OracleOutcome.Unavailable)

        consented()
        val noEndpoint = client(config = OracleConfig("", "", 0)) { json(HttpStatusCode.OK, "{}") }
        assertThat(noEndpoint.planPath(planRequest)).isEqualTo(OracleOutcome.Unavailable)

        assertThat(requests).isEmpty()
    }

    @Test
    fun noIntegrityTokenMeansIntegrityFailedWithoutASend() = runTest {
        consented()
        val client = client(token = null) { json(HttpStatusCode.OK, "{}") }

        assertThat(client.planPath(planRequest)).isEqualTo(OracleOutcome.IntegrityFailed)
        assertThat(requests).isEmpty()
    }

    @Test
    fun reportsGoToTheReportsRoute() = runTest {
        consented()
        val client = client { respond("", HttpStatusCode.Accepted) }

        val outcome = client.report(ReportRequest("week_quests.v1", "unsafe", "Title", "Description"))

        assertThat(outcome).isEqualTo(OracleOutcome.Success(Unit))
        assertThat(requests.single().url.encodedPath).isEqualTo("/v1/reports")
    }

    @Test
    fun theRequestHashIsStandardBase64OfSha256() {
        // SHA-256("hello") = 2cf24d...9824 → base64 with padding, as proxy/src/integrity.ts requestHashOf computes it.
        assertThat(KtorPathOracleClient.requestHash("hello".encodeToByteArray()))
            .isEqualTo("LPJNul+wow4m6DsqxbninhsWHlwfp0JecwQzYpOLmCQ=")
        runBlocking { }
    }
}
