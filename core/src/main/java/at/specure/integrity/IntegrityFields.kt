package at.specure.integrity

import at.rtr.rmbt.client.helper.Config as RmbtConfig
import org.json.JSONObject

private const val MAX_ERROR_DETAIL_LENGTH = 200

/**
 * Appends the Play Integrity fields of the /testRequest body according to the
 * API contract: exactly one of integrity_token / integrity_error is present,
 * integrity_timestamp accompanies the token, [IntegrityTokenResult.Disabled]
 * adds nothing.
 */
internal fun appendIntegrityFields(
    additionalValues: JSONObject,
    integrityTimestamp: Long,
    result: IntegrityTokenResult
) {
    when (result) {
        is IntegrityTokenResult.Success -> additionalValues
            .put(RmbtConfig.INTEGRITY_TOKEN, result.token)
            .put(RmbtConfig.INTEGRITY_TIMESTAMP, integrityTimestamp)
        is IntegrityTokenResult.Failure -> {
            additionalValues.put(RmbtConfig.INTEGRITY_ERROR, result.error.name)
            result.detail?.let {
                additionalValues.put(RmbtConfig.INTEGRITY_ERROR_DETAIL, it.take(MAX_ERROR_DETAIL_LENGTH))
            }
        }
        IntegrityTokenResult.Disabled -> Unit
    }
}
