package com.assismohanty.awaken.core.data.ai

import com.assismohanty.awaken.core.data.ai.dto.ReportRequest
import com.assismohanty.awaken.core.data.di.IoDispatcher
import com.assismohanty.awaken.engine.path.PathPlanRequest
import com.assismohanty.awaken.engine.path.PathWeekRequest
import io.ktor.client.HttpClient
import io.ktor.client.network.sockets.ConnectTimeoutException
import io.ktor.client.network.sockets.SocketTimeoutException
import io.ktor.client.plugins.HttpRequestTimeoutException
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.content.ByteArrayContent
import io.ktor.http.isSuccess
import java.io.IOException
import java.security.MessageDigest
import java.util.Base64
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext
import kotlinx.serialization.KSerializer
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import timber.log.Timber

/**
 * The real Path Oracle client (ARCH §3.7). The body is serialized to bytes first and those exact bytes are hashed and
 * sent, so the Play Integrity `requestHash` always matches what the proxy receives (it recomputes SHA-256 of the raw
 * body, base64). Network code lives only in `core/data/ai` (CLAUDE.md invariant 8).
 */
@Singleton
class KtorPathOracleClient @Inject constructor(
    private val http: HttpClient,
    private val config: OracleConfig,
    private val integrity: IntegrityTokenProvider,
    private val prefs: AiPrefsRepository,
    private val trace: OracleTrace,
    @IoDispatcher private val io: CoroutineDispatcher,
) : PathOracleClient {

    override suspend fun planPath(request: PathPlanRequest): OracleOutcome<OracleReply> =
        send(ROUTE_PLAN, PathPlanRequest.serializer(), request, extraHeaders = emptyMap()).also { outcome ->
            if (outcome is OracleOutcome.Success) trace.recordPlan(outcome.value.rawJson)
        }

    override suspend fun weekQuests(request: PathWeekRequest, pathId: String, manualRetry: Boolean): OracleOutcome<OracleReply> {
        val extra = buildMap {
            put(HEADER_PATH_ID, pathId)
            if (manualRetry) put(HEADER_RETRY_KIND, "manual")
        }
        return send(ROUTE_WEEK, PathWeekRequest.serializer(), request, extra)
    }

    override suspend fun report(report: ReportRequest): OracleOutcome<Unit> =
        when (val outcome = send(ROUTE_REPORT, ReportRequest.serializer(), report, extraHeaders = emptyMap(), expectBody = false)) {
            is OracleOutcome.Success -> OracleOutcome.Success(Unit)
            OracleOutcome.Offline -> OracleOutcome.Offline
            is OracleOutcome.RateLimited -> outcome
            OracleOutcome.BudgetExhausted -> OracleOutcome.BudgetExhausted
            OracleOutcome.IntegrityFailed -> OracleOutcome.IntegrityFailed
            OracleOutcome.InvalidRequest -> OracleOutcome.InvalidRequest
            OracleOutcome.ModelInvalidOutput -> OracleOutcome.ModelInvalidOutput
            OracleOutcome.ServerError -> OracleOutcome.ServerError
            OracleOutcome.Unavailable -> OracleOutcome.Unavailable
        }

    private suspend fun <T> send(
        route: String,
        serializer: KSerializer<T>,
        body: T,
        extraHeaders: Map<String, String>,
        expectBody: Boolean = true,
    ): OracleOutcome<OracleReply> = withContext(io) {
        if (!config.isConfigured) return@withContext OracleOutcome.Unavailable
        val installId = prefs.installId() ?: return@withContext OracleOutcome.Unavailable

        val bytes = json.encodeToString(serializer, body).encodeToByteArray()
        val auth = if (config.debugKey.isNotEmpty()) {
            HEADER_DEBUG_KEY to config.debugKey
        } else {
            val token = integrity.token(requestHash(bytes)) ?: return@withContext OracleOutcome.IntegrityFailed
            HEADER_INTEGRITY_TOKEN to token
        }

        try {
            val response = http.post(config.baseUrl.trimEnd('/') + route) {
                header(HEADER_INSTALL_ID, installId)
                header(auth.first, auth.second)
                extraHeaders.forEach { (name, value) -> header(name, value) }
                setBody(ByteArrayContent(bytes, ContentType.Application.Json))
            }
            map(response, expectBody)
        } catch (e: CancellationException) {
            throw e
        } catch (e: HttpRequestTimeoutException) {
            OracleOutcome.Offline
        } catch (e: ConnectTimeoutException) {
            OracleOutcome.Offline
        } catch (e: SocketTimeoutException) {
            OracleOutcome.Offline
        } catch (e: IOException) {
            OracleOutcome.Offline
        }
    }

    private suspend fun map(response: HttpResponse, expectBody: Boolean): OracleOutcome<OracleReply> {
        val text = response.bodyAsText()
        if (response.status.isSuccess()) {
            if (!expectBody) return OracleOutcome.Success(OracleReply(promptVersion = "", rawJson = ""))
            val promptVersion = parseObject(text)?.get("promptVersion")?.let { (it as? JsonPrimitive)?.contentOrNull }
            return if (promptVersion != null) OracleOutcome.Success(OracleReply(promptVersion, text)) else OracleOutcome.ServerError
        }
        // The proxy names every failure in `error`; the status alone is the fallback for anything else (ARCH §3.8).
        val code = parseObject(text)?.get("error")?.let { (it as? JsonPrimitive)?.contentOrNull }
        val status = response.status.value
        Timber.w("Oracle answered %d (%s)", status, code)
        return when {
            code == "invalid_request" || status == 400 -> OracleOutcome.InvalidRequest
            code == "integrity_failed" || status == 401 -> OracleOutcome.IntegrityFailed
            code == "rate_limited" || status == 429 ->
                OracleOutcome.RateLimited(response.headers[HttpHeaders.RetryAfter]?.toLongOrNull())
            code == "budget_exhausted" || status == 503 -> OracleOutcome.BudgetExhausted
            code == "model_invalid_output" || status == 502 -> OracleOutcome.ModelInvalidOutput
            else -> OracleOutcome.ServerError
        }
    }

    private fun parseObject(text: String): JsonObject? = try {
        json.parseToJsonElement(text).jsonObject
    } catch (e: IllegalArgumentException) {
        null
    }

    companion object {
        const val ROUTE_PLAN = "/v1/paths/plan"
        const val ROUTE_WEEK = "/v1/paths/week"
        const val ROUTE_REPORT = "/v1/reports"
        const val HEADER_INSTALL_ID = "X-Install-Id"
        const val HEADER_INTEGRITY_TOKEN = "X-Integrity-Token"
        const val HEADER_DEBUG_KEY = "X-Debug-Key"
        const val HEADER_PATH_ID = "X-Path-Id"
        const val HEADER_RETRY_KIND = "X-Retry-Kind"

        // The wire contract wants explicit nulls (e.g. secondaryStat) and the engine DTOs rely on defaults.
        val json = Json {
            encodeDefaults = true
            explicitNulls = true
            ignoreUnknownKeys = true
        }

        /** SHA-256 of the exact request bytes, standard base64 with padding: what the proxy recomputes (proxy/src/integrity.ts). */
        fun requestHash(bytes: ByteArray): String =
            Base64.getEncoder().encodeToString(MessageDigest.getInstance("SHA-256").digest(bytes))
    }
}
