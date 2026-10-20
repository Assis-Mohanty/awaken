package com.assismohanty.awaken.core.data.ai

/** What the Oracle answered, with every proxy error code mapped (ARCH §3.8) and the network failures folded in. */
sealed interface OracleOutcome<out T> {
    data class Success<T>(val value: T) : OracleOutcome<T>

    /** No connection, DNS failure or timeout: worth retrying later. */
    data object Offline : OracleOutcome<Nothing>

    /** `429 rate_limited`; [retryAfterSeconds] is the proxy's `Retry-After` when it sent one. */
    data class RateLimited(val retryAfterSeconds: Long?) : OracleOutcome<Nothing>

    /** `503 budget_exhausted`: the app falls back silently to templates. */
    data object BudgetExhausted : OracleOutcome<Nothing>

    /** `401 integrity_failed`, or no integrity token could be obtained. */
    data object IntegrityFailed : OracleOutcome<Nothing>

    /** `400 invalid_request`: our own request was rejected; retrying the same bytes cannot help. */
    data object InvalidRequest : OracleOutcome<Nothing>

    /** `502 model_invalid_output`: the model failed twice; a later retry may work. */
    data object ModelInvalidOutput : OracleOutcome<Nothing>

    /** Any other server failure (5xx, unreadable body). */
    data object ServerError : OracleOutcome<Nothing>

    /** No consent, no install ID or no configured endpoint: nothing was sent. */
    data object Unavailable : OracleOutcome<Nothing>
}

/** A usable reply: the prompt version it was written with and the exact JSON for `PathValidator` (invariant 5). */
data class OracleReply(val promptVersion: String, val rawJson: String)
