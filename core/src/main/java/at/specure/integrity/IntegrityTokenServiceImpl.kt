package at.specure.integrity

import at.specure.config.Config
import com.google.android.gms.tasks.Task
import com.google.android.play.core.integrity.StandardIntegrityException
import com.google.android.play.core.integrity.StandardIntegrityManager
import com.google.android.play.core.integrity.StandardIntegrityManager.PrepareIntegrityTokenRequest
import com.google.android.play.core.integrity.StandardIntegrityManager.StandardIntegrityTokenProvider
import com.google.android.play.core.integrity.StandardIntegrityManager.StandardIntegrityTokenRequest
import com.google.android.play.core.integrity.model.StandardIntegrityErrorCode
import java.util.concurrent.Executor
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeoutOrNull
import timber.log.Timber

/** Logcat tag for the whole Play Integrity flow — filter with `adb logcat -s IntegrityAPI`. */
private const val LOG_TAG = "IntegrityAPI"

/**
 * [IntegrityTokenService] backed by the Play Integrity standard request API.
 *
 * The token provider is prepared off the critical path ([warmUp]) and kept in
 * memory; [requestToken] self-heals a missing or invalidated provider within
 * its timeout budget. The manager is supplied lazily so unit tests can inject
 * a fake and so that Play services classes are not touched when the feature
 * is disabled.
 */
class IntegrityTokenServiceImpl(
    private val config: Config,
    private val integrityManagerProvider: () -> StandardIntegrityManager
) : IntegrityTokenService {

    @Volatile
    private var tokenProvider: StandardIntegrityTokenProvider? = null

    // Listener callbacks run on the completing thread; the default (main thread)
    // executor is not available in JVM unit tests and is not needed here.
    private val directExecutor = Executor { it.run() }

    private val cloudProjectNumber: Long?
        get() = config.cloudProjectNumber.toLongOrNull()

    override fun warmUp() {
        val projectNumber = cloudProjectNumber
        if (projectNumber == null) {
            Timber.tag(LOG_TAG).i("Warm-up skipped — CLOUD_PROJECT_NUMBER not configured, integrity disabled")
            return
        }
        if (tokenProvider != null) {
            Timber.tag(LOG_TAG).d("Warm-up skipped — token provider already prepared")
            return
        }
        Timber.tag(LOG_TAG).i("Warm-up: preparing token provider (cloudProjectNumber=%d)", projectNumber)
        try {
            integrityManagerProvider()
                .prepareIntegrityToken(
                    PrepareIntegrityTokenRequest.builder()
                        .setCloudProjectNumber(projectNumber)
                        .build()
                )
                .addOnSuccessListener(directExecutor) {
                    tokenProvider = it
                    Timber.tag(LOG_TAG).i("Warm-up: token provider ready")
                }
                .addOnFailureListener(directExecutor) {
                    Timber.tag(LOG_TAG).w(it, "Warm-up: token provider preparation failed")
                }
        } catch (e: Exception) {
            Timber.tag(LOG_TAG).w(e, "Warm-up: token provider preparation failed")
        }
    }

    override suspend fun requestToken(requestHash: String, timeoutMillis: Long): IntegrityTokenResult {
        val projectNumber = cloudProjectNumber
        if (projectNumber == null) {
            Timber.tag(LOG_TAG).i("Token request skipped — CLOUD_PROJECT_NUMBER not configured, no integrity fields will be sent")
            return IntegrityTokenResult.Disabled
        }
        Timber.tag(LOG_TAG).i(
            "Token request started (requestHash=%s, timeoutMs=%d, providerCached=%s)",
            requestHash,
            timeoutMillis,
            tokenProvider != null
        )
        val startedAtMillis = System.currentTimeMillis()
        val result = withTimeoutOrNull(timeoutMillis) {
            requestTokenInternal(projectNumber, requestHash)
        } ?: IntegrityTokenResult.Failure(IntegrityError.TIMEOUT, null)
        val elapsedMillis = System.currentTimeMillis() - startedAtMillis
        when (result) {
            is IntegrityTokenResult.Success -> Timber.tag(LOG_TAG).i(
                "Token obtained in %d ms (length=%d, prefix=%s…)",
                elapsedMillis,
                result.token.length,
                result.token.take(16)
            )
            is IntegrityTokenResult.Failure -> Timber.tag(LOG_TAG).w(
                "Token request failed in %d ms: error=%s, detail=%s",
                elapsedMillis,
                result.error,
                result.detail
            )
            IntegrityTokenResult.Disabled -> Unit
        }
        return result
    }

    private suspend fun requestTokenInternal(projectNumber: Long, requestHash: String): IntegrityTokenResult {
        val provider = tokenProvider
            ?: when (val prepared = prepare(projectNumber)) {
                is PrepareOutcome.Ready -> prepared.provider
                is PrepareOutcome.Failed -> return prepared.failure
            }
        return try {
            IntegrityTokenResult.Success(provider.requestToken(requestHash))
        } catch (e: CancellationException) {
            throw e // let withTimeoutOrNull handle the timeout cancellation
        } catch (e: Exception) {
            if (e.standardErrorCode() == StandardIntegrityErrorCode.INTEGRITY_TOKEN_PROVIDER_INVALID) {
                Timber.tag(LOG_TAG).w("Token provider invalidated by Play services — re-preparing and retrying once")
                tokenProvider = null
                when (val prepared = prepare(projectNumber)) {
                    is PrepareOutcome.Ready -> {
                        try {
                            return IntegrityTokenResult.Success(prepared.provider.requestToken(requestHash))
                        } catch (retryError: CancellationException) {
                            throw retryError
                        } catch (retryError: Exception) {
                            return retryError.toFailure(IntegrityError.REQUEST_FAILED)
                        }
                    }
                    is PrepareOutcome.Failed -> return prepared.failure
                }
            }
            e.toFailure(IntegrityError.REQUEST_FAILED)
        }
    }

    private suspend fun prepare(projectNumber: Long): PrepareOutcome {
        return try {
            Timber.tag(LOG_TAG).i("Preparing token provider on demand (cloudProjectNumber=%d)", projectNumber)
            val prepareTask = integrityManagerProvider()
                .prepareIntegrityToken(
                    PrepareIntegrityTokenRequest.builder()
                        .setCloudProjectNumber(projectNumber)
                        .build()
                )
            // Cache the provider even when the awaiting coroutine has been cancelled by the
            // caller's timeout — a late successful prepare then speeds up the next measurement.
            prepareTask.addOnSuccessListener(directExecutor) {
                tokenProvider = it
                Timber.tag(LOG_TAG).d("Token provider cached from prepare listener")
            }
            val provider = prepareTask.await()
            tokenProvider = provider
            Timber.tag(LOG_TAG).i("Token provider prepared")
            PrepareOutcome.Ready(provider)
        } catch (e: CancellationException) {
            throw e // let withTimeoutOrNull handle the timeout cancellation
        } catch (e: Exception) {
            Timber.tag(LOG_TAG).w(e, "Token provider preparation failed")
            PrepareOutcome.Failed(e.toFailure(IntegrityError.PREPARE_FAILED))
        }
    }

    private suspend fun StandardIntegrityTokenProvider.requestToken(requestHash: String): String =
        request(
            StandardIntegrityTokenRequest.builder()
                .setRequestHash(requestHash)
                .build()
        ).await().token()

    private suspend fun <T> Task<T>.await(): T = suspendCancellableCoroutine { continuation ->
        addOnSuccessListener(directExecutor) { continuation.resume(it) }
        addOnFailureListener(directExecutor) { continuation.resumeWithException(it) }
    }

    private fun Exception.standardErrorCode(): Int? = (this as? StandardIntegrityException)?.errorCode

    private fun Exception.toFailure(defaultError: IntegrityError): IntegrityTokenResult.Failure {
        val code = standardErrorCode()
            ?: return IntegrityTokenResult.Failure(defaultError, this::class.java.simpleName)
        val error = if (code in NOT_AVAILABLE_CODES) IntegrityError.NOT_AVAILABLE else defaultError
        return IntegrityTokenResult.Failure(error, "STANDARD_ERROR_${code}_${errorCodeName(code)}")
    }

    private fun errorCodeName(code: Int): String = when (code) {
        StandardIntegrityErrorCode.API_NOT_AVAILABLE -> "API_NOT_AVAILABLE"
        StandardIntegrityErrorCode.APP_NOT_INSTALLED -> "APP_NOT_INSTALLED"
        StandardIntegrityErrorCode.APP_UID_MISMATCH -> "APP_UID_MISMATCH"
        StandardIntegrityErrorCode.CANNOT_BIND_TO_SERVICE -> "CANNOT_BIND_TO_SERVICE"
        StandardIntegrityErrorCode.CLIENT_TRANSIENT_ERROR -> "CLIENT_TRANSIENT_ERROR"
        StandardIntegrityErrorCode.CLOUD_PROJECT_NUMBER_IS_INVALID -> "CLOUD_PROJECT_NUMBER_IS_INVALID"
        StandardIntegrityErrorCode.GOOGLE_SERVER_UNAVAILABLE -> "GOOGLE_SERVER_UNAVAILABLE"
        StandardIntegrityErrorCode.INTEGRITY_TOKEN_PROVIDER_INVALID -> "INTEGRITY_TOKEN_PROVIDER_INVALID"
        StandardIntegrityErrorCode.INTERNAL_ERROR -> "INTERNAL_ERROR"
        StandardIntegrityErrorCode.NETWORK_ERROR -> "NETWORK_ERROR"
        StandardIntegrityErrorCode.PLAY_SERVICES_NOT_FOUND -> "PLAY_SERVICES_NOT_FOUND"
        StandardIntegrityErrorCode.PLAY_SERVICES_VERSION_OUTDATED -> "PLAY_SERVICES_VERSION_OUTDATED"
        StandardIntegrityErrorCode.PLAY_STORE_NOT_FOUND -> "PLAY_STORE_NOT_FOUND"
        StandardIntegrityErrorCode.PLAY_STORE_VERSION_OUTDATED -> "PLAY_STORE_VERSION_OUTDATED"
        StandardIntegrityErrorCode.REQUEST_HASH_TOO_LONG -> "REQUEST_HASH_TOO_LONG"
        StandardIntegrityErrorCode.TOO_MANY_REQUESTS -> "TOO_MANY_REQUESTS"
        else -> "UNKNOWN"
    }

    private sealed class PrepareOutcome {
        class Ready(val provider: StandardIntegrityTokenProvider) : PrepareOutcome()
        class Failed(val failure: IntegrityTokenResult.Failure) : PrepareOutcome()
    }

    private companion object {
        val NOT_AVAILABLE_CODES = setOf(
            StandardIntegrityErrorCode.API_NOT_AVAILABLE,
            StandardIntegrityErrorCode.PLAY_STORE_NOT_FOUND,
            StandardIntegrityErrorCode.PLAY_SERVICES_NOT_FOUND,
            StandardIntegrityErrorCode.PLAY_STORE_VERSION_OUTDATED,
            StandardIntegrityErrorCode.PLAY_SERVICES_VERSION_OUTDATED,
            StandardIntegrityErrorCode.CANNOT_BIND_TO_SERVICE
        )
    }
}
