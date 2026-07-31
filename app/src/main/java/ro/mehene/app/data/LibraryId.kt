package ro.mehene.app.data

import java.security.MessageDigest

object LibraryId {
    const val LEGACY = "legacy"

    fun fromRootUri(rootUri: String): String = MessageDigest.getInstance("SHA-256")
        .digest(rootUri.toByteArray(Charsets.UTF_8))
        .joinToString(separator = "") { "%02x".format(it) }
        .take(32)
}
