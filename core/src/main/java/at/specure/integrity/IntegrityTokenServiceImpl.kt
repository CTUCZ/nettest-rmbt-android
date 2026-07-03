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
        val projectNumber = cloudProjectNumber ?: return
        if (tokenProvider != null) return
        try {
            integrityManagerProvider()
                .prepareIntegrityToken(
                    PrepareIntegrityTokenRequest.builder()
                        .setCloudProjectNumber(projectNumber)
                        .build()
                )
                .addOnSuccessListener(directExecutor) { tokenProvider = it }
                .addOnFailureListener(directExecutor) {
                    Timber.w(it, "Integrity token provider warm-up failed")
                }
        } catch (e: Exception) {
            Timber.w(e, "Integrity token provider warm-up failed")
        }
    }

    override suspend fun requestToken(requestHash: String, timeoutMillis: Long): IntegrityTokenResult {
        val projectNumber = cloudProjectNumber ?: return IntegrityTokenResult.Disabled
        return withTimeoutOrNull(timeoutMillis) {
            requestTokenInternal(projectNumber, requestHash)
        } ?: IntegrityTokenResult.Failure(IntegrityError.TIMEOUT, null)
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
            val prepareTask = integrityManagerProvider()
                .prepareIntegrityToken(
                    PrepareIntegrityTokenRequest.builder()
                        .setCloudProjectNumber(projectNumber)
                        .build()
                )
            // Cache the provider even when the awaiting coroutine has been cancelled by the
            // caller's timeout — a late successful prepare then speeds up the next measurement.
            prepareTask.addOnSuccessListener(directExecutor) { tokenProvider = it }
            val provider = prepareTask.await()
            tokenProvider = provider
            PrepareOutcome.Ready(provider)
        } catch (e: CancellationException) {
            throw e // let withTimeoutOrNull handle the timeout cancellation
        } catch (e: Exception) {
            Timber.w(e, "Integrity token provider preparation failed")
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
