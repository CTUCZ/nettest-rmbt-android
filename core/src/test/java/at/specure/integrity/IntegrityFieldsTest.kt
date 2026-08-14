package at.specure.integrity

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class IntegrityFieldsTest {

    @Test
    fun success_putsTokenAndTimestamp() {
        val values = JSONObject()
        appendIntegrityFields(values, 1719900000000L, IntegrityTokenResult.Success("opaque-token"))
        assertEquals("opaque-token", values.getString("integrity_token"))
        assertEquals(1719900000000L, values.getLong("integrity_timestamp"))
        assertFalse(values.has("integrity_error"))
        assertFalse(values.has("integrity_error_detail"))
    }

    @Test
    fun failure_putsErrorNameAndDetail() {
        val values = JSONObject()
        appendIntegrityFields(
            values,
            1719900000000L,
            IntegrityTokenResult.Failure(IntegrityError.REQUEST_FAILED, "STANDARD_ERROR_-9_CANNOT_BIND_TO_SERVICE")
        )
        assertEquals("REQUEST_FAILED", values.getString("integrity_error"))
        assertEquals("STANDARD_ERROR_-9_CANNOT_BIND_TO_SERVICE", values.getString("integrity_error_detail"))
        assertFalse(values.has("integrity_token"))
        assertFalse(values.has("integrity_timestamp"))
    }

    @Test
    fun failure_withoutDetail_omitsDetailField() {
        val values = JSONObject()
        appendIntegrityFields(values, 1L, IntegrityTokenResult.Failure(IntegrityError.TIMEOUT, null))
        assertEquals("TIMEOUT", values.getString("integrity_error"))
        assertFalse(values.has("integrity_error_detail"))
    }

    @Test
    fun failure_detailLongerThan200Chars_isTruncated() {
        val values = JSONObject()
        appendIntegrityFields(values, 1L, IntegrityTokenResult.Failure(IntegrityError.PREPARE_FAILED, "x".repeat(300)))
        assertEquals(200, values.getString("integrity_error_detail").length)
    }

    @Test
    fun disabled_putsNothing() {
        val values = JSONObject()
        appendIntegrityFields(values, 1L, IntegrityTokenResult.Disabled)
        assertEquals(0, values.length())
    }
}
