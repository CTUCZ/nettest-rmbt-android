package at.specure.util

import org.junit.Assert.assertEquals
import org.junit.Test

class HashUtilsTest {

    @Test
    fun sha256Hex_emptyString_returnsKnownDigest() {
        assertEquals(
            "e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855",
            sha256Hex("")
        )
    }

    @Test
    fun sha256Hex_knownVector_returnsKnownDigest() {
        // SHA-256("abc"), FIPS 180-2 test vector
        assertEquals(
            "ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad",
            sha256Hex("abc")
        )
    }

    @Test
    fun sha256Hex_requestHashShape_uuidPipeTimestamp() {
        val digest = sha256Hex("d5c8f279-1234-5678-9abc-def012345678|1719900000000")
        assertEquals(64, digest.length)
        assertEquals(digest.lowercase(), digest)
    }
}
