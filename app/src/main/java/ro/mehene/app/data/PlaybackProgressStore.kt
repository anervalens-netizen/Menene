package ro.mehene.app.data

import android.content.Context
import java.security.MessageDigest

class PlaybackProgressStore(context: Context) {
    private val preferences = context.getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE)

    fun savedPosition(mediaUri: String): Long = preferences.getLong(positionKey(mediaUri), 0L)

    fun progress(mediaUri: String): Float {
        val duration = preferences.getLong(durationKey(mediaUri), 0L)
        if (duration <= 0L) return 0f
        return (savedPosition(mediaUri).toFloat() / duration.toFloat()).coerceIn(0f, 1f)
    }

    fun save(mediaUri: String, positionMs: Long, durationMs: Long) {
        if (durationMs <= 0L) return
        val completed = positionMs >= (durationMs * 0.95f)
        preferences.edit()
            .putLong(positionKey(mediaUri), if (completed) 0L else positionMs.coerceAtLeast(0L))
            .putLong(durationKey(mediaUri), durationMs)
            .apply()
    }

    fun markCompleted(mediaUri: String, durationMs: Long) {
        preferences.edit()
            .putLong(positionKey(mediaUri), 0L)
            .putLong(durationKey(mediaUri), durationMs.coerceAtLeast(0L))
            .apply()
    }

    private fun positionKey(mediaUri: String) = "position_${digest(mediaUri)}"
    private fun durationKey(mediaUri: String) = "duration_${digest(mediaUri)}"

    private fun digest(value: String): String {
        val bytes = MessageDigest.getInstance("SHA-256").digest(value.toByteArray())
        return bytes.take(12).joinToString(separator = "") { "%02x".format(it) }
    }

    companion object {
        private const val PREFERENCES_NAME = "mehene_playback"
    }
}
