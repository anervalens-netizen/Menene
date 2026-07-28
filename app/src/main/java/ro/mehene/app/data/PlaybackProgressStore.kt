package ro.mehene.app.data

import android.content.Context
import java.security.MessageDigest

class PlaybackProgressStore(context: Context) {
    private val preferences = context.getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE)

    fun savedPosition(playbackKey: String): Long {
        if (preferences.getBoolean(completedKey(playbackKey), false)) return 0L
        return preferences.getLong(positionKey(playbackKey), 0L)
    }

    fun progress(playbackKey: String): EpisodeProgress {
        if (preferences.getBoolean(completedKey(playbackKey), false)) {
            return EpisodeProgress(EpisodePlaybackState.COMPLETED, 1f)
        }
        return PlaybackProgressPolicy.evaluate(
            positionMs = preferences.getLong(positionKey(playbackKey), 0L),
            durationMs = preferences.getLong(durationKey(playbackKey), 0L),
        )
    }

    fun save(playbackKey: String, positionMs: Long, durationMs: Long) {
        if (durationMs <= 0L) return
        val evaluated = PlaybackProgressPolicy.evaluate(positionMs, durationMs)
        preferences.edit()
            .putLong(
                positionKey(playbackKey),
                if (evaluated.state == EpisodePlaybackState.IN_PROGRESS) positionMs.coerceAtLeast(0L) else 0L,
            )
            .putLong(durationKey(playbackKey), durationMs)
            .putBoolean(completedKey(playbackKey), evaluated.state == EpisodePlaybackState.COMPLETED)
            .apply()
    }

    fun markCompleted(playbackKey: String, durationMs: Long) {
        preferences.edit()
            .putLong(positionKey(playbackKey), 0L)
            .putLong(durationKey(playbackKey), durationMs.coerceAtLeast(0L))
            .putBoolean(completedKey(playbackKey), true)
            .apply()
    }

    private fun positionKey(playbackKey: String) = "position_${digest(playbackKey)}"
    private fun durationKey(playbackKey: String) = "duration_${digest(playbackKey)}"
    private fun completedKey(playbackKey: String) = "completed_${digest(playbackKey)}"

    private fun digest(value: String): String {
        val bytes = MessageDigest.getInstance("SHA-256").digest(value.toByteArray())
        return bytes.take(12).joinToString(separator = "") { "%02x".format(it) }
    }

    companion object {
        private const val PREFERENCES_NAME = "mehene_playback"
    }
}
