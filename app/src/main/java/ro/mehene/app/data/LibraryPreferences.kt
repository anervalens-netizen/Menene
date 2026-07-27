package ro.mehene.app.data

import android.content.Context

class LibraryPreferences(context: Context) {
    private val preferences = context.getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE)

    var libraryUri: String?
        get() = preferences.getString(KEY_LIBRARY_URI, null)
        set(value) = preferences.edit().putString(KEY_LIBRARY_URI, value).apply()

    var parentPin: String
        get() = preferences.getString(KEY_PARENT_PIN, DEFAULT_PARENT_PIN) ?: DEFAULT_PARENT_PIN
        set(value) = preferences.edit().putString(KEY_PARENT_PIN, value).apply()

    var kioskEnabled: Boolean
        get() = preferences.getBoolean(KEY_KIOSK_ENABLED, true)
        set(value) = preferences.edit().putBoolean(KEY_KIOSK_ENABLED, value).apply()

    companion object {
        private const val PREFERENCES_NAME = "mehene_preferences"
        private const val KEY_LIBRARY_URI = "library_uri"
        private const val KEY_PARENT_PIN = "parent_pin"
        private const val KEY_KIOSK_ENABLED = "kiosk_enabled"
        const val DEFAULT_PARENT_PIN = "2468"
    }
}
