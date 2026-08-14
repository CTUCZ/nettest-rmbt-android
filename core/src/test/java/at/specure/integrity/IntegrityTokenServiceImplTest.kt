package at.specure.integrity

import at.specure.config.Config
import com.google.android.gms.tasks.TaskCompletionSource
import com.google.android.gms.tasks.Tasks
import com.google.android.play.core.integrity.StandardIntegrityException
import com.google.android.play.core.integrity.StandardIntegrityManager
import com.google.android.play.core.integrity.StandardIntegrityManager.StandardIntegrityToken
import com.google.android.play.core.integrity.StandardIntegrityManager.StandardIntegrityTokenProvider
import com.google.android.play.core.integrity.model.StandardIntegrityErrorCode
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class IntegrityTokenServiceImplTest {

    private val config: Config = mockk(relaxed = true)
    private val manager: StandardIntegrityManager = mockk()

    private fun service() = IntegrityTokenServiceImpl(config) { manager }

    @Test
    fun requestToken_withoutCloudProjectNumber_returnsDisabled() = runBlocking {
        every { config.cloudProjectNumber } returns ""
        val result = service().requestToken("hash", 1000)
        assertEquals(IntegrityTokenResult.Disabled, result)
    }

    @Test
    fun requestToken_nonNumericCloudProjectNumber_returnsDisabled() = runBlocking {
        every { config.cloudProjectNumber } returns "not-a-number"
        val result = service().requestToken("hash", 1000)
        assertEquals(IntegrityTokenResult.Disabled, result)
    }

    @Test
    fun requestToken_prepareFails_returnsPrepareFailed() = runBlocking {
        every { config.cloudProjectNumber } returns "123456"
        every { manager.prepareIntegrityToken(any()) } returns Tasks.forException(RuntimeException("boom"))
        val result = service().requestToken("hash", 1000)
        assertTrue(result is IntegrityTokenResult.Failure)
        assertEquals(IntegrityError.PREPARE_FAILED, (result as IntegrityTokenResult.Failure).error)
    }

    @Test
    fun requestToken_providerReturnsToken_returnsSuccess() = runBlocking {
        every { config.cloudProjectNumber } returns "123456"
        val token: StandardIntegrityToken = mockk()
        every { token.token() } returns "opaque-token"
        val provider: StandardIntegrityTokenProvider = mockk()
        every { provider.request(any()) } returns Tasks.forResult(token)
        every { manager.prepareIntegrityToken(any()) } returns Tasks.forResult(provider)
        val result = service().requestToken("hash", 1000)
        assertEquals(IntegrityTokenResult.Success("opaque-token"), result)
    }

    @Test
    fun requestToken_prepareNeverCompletes_returnsTimeout() = runBlocking {
        every { config.cloudProjectNumber } returns "123456"
        every { manager.prepareIntegrityToken(any()) } returns TaskCompletionSource<StandardIntegrityTokenProvider>().task
        val result = service().requestToken("hash", 100)
        assertTrue(result is IntegrityTokenResult.Failure)
        assertEquals(IntegrityError.TIMEOUT, (result as IntegrityTokenResult.Failure).error)
    }

    @Test
    fun requestToken_requestFails_returnsRequestFailed() = runBlocking {
        every { config.cloudProjectNumber } returns "123456"
        val provider: StandardIntegrityTokenProvider = mockk()
        every { provider.request(any()) } returns Tasks.forException(RuntimeException("request boom"))
        every { manager.prepareIntegrityToken(any()) } returns Tasks.forResult(provider)
        val result = service().requestToken("hash", 1000)
        assertTrue(result is IntegrityTokenResult.Failure)
        assertEquals(IntegrityError.REQUEST_FAILED, (result as IntegrityTokenResult.Failure).error)
    }

    @Test
    fun requestToken_providerInvalid_retriesWithFreshProviderAndReturnsSuccess() = runBlocking {
        every { config.cloudProjectNumber } returns "123456"

        val providerInvalidException: StandardIntegrityException = mockk(relaxed = true) {
            every { errorCode } returns StandardIntegrityErrorCode.INTEGRITY_TOKEN_PROVIDER_INVALID
        }
        val staleProvider: StandardIntegrityTokenProvider = mockk()
        every { staleProvider.request(any()) } returns Tasks.forException(providerInvalidException)

        val freshToken: StandardIntegrityToken = mockk()
        every { freshToken.token() } returns "fresh-token"
        val freshProvider: StandardIntegrityTokenProvider = mockk()
        every { freshProvider.request(any()) } returns Tasks.forResult(freshToken)

        every { manager.prepareIntegrityToken(any()) } returnsMany listOf(
            Tasks.forResult(staleProvider),
            Tasks.forResult(freshProvider)
        )

        val result = service().requestToken("hash", 1000)

        assertEquals(IntegrityTokenResult.Success("fresh-token"), result)
        verify(exactly = 2) { manager.prepareIntegrityToken(any()) }
    }

    @Test
    fun requestToken_providerInvalid_retryAlsoFails_returnsRequestFailed() = runBlocking {
        every { config.cloudProjectNumber } returns "123456"

        val providerInvalidException: StandardIntegrityException = mockk(relaxed = true) {
            every { errorCode } returns StandardIntegrityErrorCode.INTEGRITY_TOKEN_PROVIDER_INVALID
        }
        val staleProvider: StandardIntegrityTokenProvider = mockk()
        every { staleProvider.request(any()) } returns Tasks.forException(providerInvalidException)

        val freshProvider: StandardIntegrityTokenProvider = mockk()
        every { freshProvider.request(any()) } returns Tasks.forException(RuntimeException("fresh boom"))

        every { manager.prepareIntegrityToken(any()) } returnsMany listOf(
            Tasks.forResult(staleProvider),
            Tasks.forResult(freshProvider)
        )

        val result = service().requestToken("hash", 1000)

        assertTrue(result is IntegrityTokenResult.Failure)
        assertEquals(IntegrityError.REQUEST_FAILED, (result as IntegrityTokenResult.Failure).error)
        verify(exactly = 2) { manager.prepareIntegrityToken(any()) }
    }

    @Test
    fun requestToken_prepareFailsWithApiNotAvailable_returnsNotAvailable() = runBlocking {
        every { config.cloudProjectNumber } returns "123456"

        val notAvailableException: StandardIntegrityException = mockk(relaxed = true) {
            every { errorCode } returns StandardIntegrityErrorCode.API_NOT_AVAILABLE
        }
        every { manager.prepareIntegrityToken(any()) } returns Tasks.forException(notAvailableException)

        val result = service().requestToken("hash", 1000)

        assertTrue(result is IntegrityTokenResult.Failure)
        val failure = result as IntegrityTokenResult.Failure
        assertEquals(IntegrityError.NOT_AVAILABLE, failure.error)
        assertTrue(failure.detail?.startsWith("STANDARD_ERROR_") == true)
    }

    @Test
    fun requestToken_providerInvalid_reprepareFails_returnsPrepareFailure() = runBlocking {
        every { config.cloudProjectNumber } returns "123456"

        val providerInvalidException: StandardIntegrityException = mockk(relaxed = true) {
            every { errorCode } returns StandardIntegrityErrorCode.INTEGRITY_TOKEN_PROVIDER_INVALID
        }
        val staleProvider: StandardIntegrityTokenProvider = mockk()
        every { staleProvider.request(any()) } returns Tasks.forException(providerInvalidException)

        every { manager.prepareIntegrityToken(any()) } returnsMany listOf(
            Tasks.forResult(staleProvider),
            Tasks.forException(RuntimeException("re-prepare boom"))
        )

        val result = service().requestToken("hash", 1000)

        assertTrue(result is IntegrityTokenResult.Failure)
        assertEquals(IntegrityError.PREPARE_FAILED, (result as IntegrityTokenResult.Failure).error)
        verify(exactly = 2) { manager.prepareIntegrityToken(any()) }
    }

    @Test
    fun requestToken_prepareFailsWithCannotBindToService_returnsNotAvailable() = runBlocking {
        every { config.cloudProjectNumber } returns "123456"

        val cannotBindException: StandardIntegrityException = mockk(relaxed = true) {
            every { errorCode } returns StandardIntegrityErrorCode.CANNOT_BIND_TO_SERVICE
        }
        every { manager.prepareIntegrityToken(any()) } returns Tasks.forException(cannotBindException)

        val result = service().requestToken("hash", 1000)

        assertTrue(result is IntegrityTokenResult.Failure)
        assertEquals(IntegrityError.NOT_AVAILABLE, (result as IntegrityTokenResult.Failure).error)
    }

    @Test
    fun warmUp_cachesProvider_requestTokenSkipsPrepare() = runBlocking {
        every { config.cloudProjectNumber } returns "123456"

        val token: StandardIntegrityToken = mockk()
        every { token.token() } returns "warm-token"
        val provider: StandardIntegrityTokenProvider = mockk()
        every { provider.request(any()) } returns Tasks.forResult(token)
        every { manager.prepareIntegrityToken(any()) } returns Tasks.forResult(provider)

        val svc = service()
        svc.warmUp()
        val result = svc.requestToken("hash", 1000)

        assertEquals(IntegrityTokenResult.Success("warm-token"), result)
        verify(exactly = 1) { manager.prepareIntegrityToken(any()) }
    }
}
