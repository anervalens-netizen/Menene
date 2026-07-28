package ro.mehene.app.data

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.meheneSettingsDataStore by preferencesDataStore(name = "mehene_settings")

class SettingsRepository(private val context: Context) {
    val playbackMode: Flow<PlaybackMode> = context.meheneSettingsDataStore.data.map { preferences ->
        preferences[KEY_PLAYBACK_MODE]
            ?.let { runCatching { PlaybackMode.valueOf(it) }.getOrNull() }
            ?: PlaybackMode.SINGLE
    }

    val preferredAudioLanguage: Flow<String> = context.meheneSettingsDataStore.data.map { preferences ->
        preferences[KEY_AUDIO_LANGUAGE] ?: DEFAULT_AUDIO_LANGUAGE
    }

    suspend fun setPlaybackMode(mode: PlaybackMode) {
        context.meheneSettingsDataStore.edit { preferences ->
            preferences[KEY_PLAYBACK_MODE] = mode.name
        }
    }

    suspend fun setPreferredAudioLanguage(language: String) {
        context.meheneSettingsDataStore.edit { preferences ->
            preferences[KEY_AUDIO_LANGUAGE] = language.trim()
        }
    }

    companion object {
        private val KEY_PLAYBACK_MODE = stringPreferencesKey("playback_mode")
        private val KEY_AUDIO_LANGUAGE = stringPreferencesKey("preferred_audio_language")
        const val DEFAULT_AUDIO_LANGUAGE = "ron"
    }
}
