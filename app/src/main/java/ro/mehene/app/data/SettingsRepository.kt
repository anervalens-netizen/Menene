package ro.mehene.app.data

import android.content.Context
import androidx.datastore.core.handlers.ReplaceFileCorruptionHandler
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import java.io.IOException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map

private val Context.meheneSettingsDataStore by preferencesDataStore(
    name = "mehene_settings",
    corruptionHandler = ReplaceFileCorruptionHandler { emptyPreferences() },
)

class SettingsRepository(context: Context) {
    private val appContext = context.applicationContext
    private val safePreferences = appContext.meheneSettingsDataStore.data.catch { error ->
        if (error is IOException) emit(emptyPreferences()) else throw error
    }

    val playbackMode: Flow<PlaybackMode> = safePreferences.map { preferences ->
        preferences[KEY_PLAYBACK_MODE]
            ?.let { runCatching { PlaybackMode.valueOf(it) }.getOrNull() }
            ?: PlaybackMode.SINGLE
    }

    val preferredAudioLanguage: Flow<String> = safePreferences.map { preferences ->
        sanitizeLanguage(preferences[KEY_AUDIO_LANGUAGE])
    }

    suspend fun setPlaybackMode(mode: PlaybackMode) {
        appContext.meheneSettingsDataStore.edit { preferences ->
            preferences[KEY_PLAYBACK_MODE] = mode.name
        }
    }

    suspend fun setPreferredAudioLanguage(language: String) {
        appContext.meheneSettingsDataStore.edit { preferences ->
            preferences[KEY_AUDIO_LANGUAGE] = sanitizeLanguage(language)
        }
    }

    private fun sanitizeLanguage(language: String?): String {
        if (language != null && language.trim().isEmpty()) return ""
        val normalized = language.orEmpty().trim().lowercase()
        return normalized.takeIf {
            it.isNotEmpty() && it.length <= MAX_LANGUAGE_LENGTH && it.all(Char::isLetter)
        } ?: DEFAULT_AUDIO_LANGUAGE
    }

    companion object {
        private val KEY_PLAYBACK_MODE = stringPreferencesKey("playback_mode")
        private val KEY_AUDIO_LANGUAGE = stringPreferencesKey("preferred_audio_language")
        private const val MAX_LANGUAGE_LENGTH = 8
        const val DEFAULT_AUDIO_LANGUAGE = "ron"
    }
}
