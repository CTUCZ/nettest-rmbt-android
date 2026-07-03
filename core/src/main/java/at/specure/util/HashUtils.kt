package at.specure.util

import java.security.MessageDigest

/**
 * Returns the lowercase hex encoded SHA-256 digest of the UTF-8 bytes of [input].
 */
fun sha256Hex(input: String): String =
    MessageDigest.getInstance("SHA-256")
        .digest(input.toByteArray(Charsets.UTF_8))
        .joinToString("") { "%02x".format(it) }
