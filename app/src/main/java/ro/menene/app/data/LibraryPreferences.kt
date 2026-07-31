package ro.menene.app.data

import android.content.Context

class LibraryPreferences(context: Context) {
    private val preferences = context.getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE)

    var libraryUri: String?
        get() = preferences.getString(KEY_LIBRARY_URI, null)
        set(value) = preferences.edit().putString(KEY_LIBRARY_URI, value).apply()

    var kioskEnabled: Boolean
        get() = preferences.getBoolean(KEY_KIOSK_ENABLED, false)
        set(value) = preferences.edit().putBoolean(KEY_KIOSK_ENABLED, value).apply()

    var resumeKioskAfterExternalActivity: Boolean
        get() = preferences.getBoolean(KEY_RESUME_KIOSK, false)
        set(value) = preferences.edit().putBoolean(KEY_RESUME_KIOSK, value).apply()

    companion object {
        private const val PREFERENCES_NAME = "menene_preferences"
        private const val KEY_LIBRARY_URI = "library_uri"
        private const val KEY_KIOSK_ENABLED = "kiosk_enabled"
        private const val KEY_RESUME_KIOSK = "resume_kiosk"
    }
}
