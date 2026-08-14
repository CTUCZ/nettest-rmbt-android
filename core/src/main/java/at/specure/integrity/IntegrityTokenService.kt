package at.specure.integrity

/**
 * Provides Google Play Integrity standard-request tokens for the measurement
 * initialization call (/testRequest). See the shared specification for the
 * API contract and the enforcement flow.
 */
interface IntegrityTokenService {

    /**
     * Prepares the standard integrity token provider off the critical path.
     * Safe to call repeatedly; no-op when the provider is ready or the feature
     * is disabled (no cloud project number configured).
     */
    fun warmUp()

    /**
     * Requests an integrity token bound to [requestHash]. Returns within
     * [timeoutMillis] at the latest. Never throws.
     */
    suspend fun requestToken(requestHash: String, timeoutMillis: Long): IntegrityTokenResult
}

sealed class IntegrityTokenResult {

    /** Token obtained; send it as integrity_token together with integrity_timestamp. */
    data class Success(val token: String) : IntegrityTokenResult()

    /** Token could not be obtained; send integrity_error (+ optional detail). */
    data class Failure(val error: IntegrityError, val detail: String?) : IntegrityTokenResult()

    /** Feature disabled (no cloud project number) — send no integrity fields at all. */
    object Disabled : IntegrityTokenResult()
}

/** Values of the integrity_error request field, fixed by the API contract. */
enum class IntegrityError { NOT_AVAILABLE, PREPARE_FAILED, REQUEST_FAILED, TIMEOUT }
