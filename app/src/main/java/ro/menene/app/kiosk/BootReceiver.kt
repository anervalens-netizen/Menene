package ro.menene.app.kiosk

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import ro.menene.app.MainActivity
import ro.menene.app.data.LibraryPreferences

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) {
        if (intent?.action != Intent.ACTION_BOOT_COMPLETED) return
        val preferences = LibraryPreferences(context)
        if (!preferences.kioskEnabled || preferences.libraryUri == null) return
        if (!KioskController.isDeviceOwner(context)) return

        if (!KioskController.configureDeviceOwner(context)) return
        val launchIntent = Intent(context, MainActivity::class.java).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
        }
        runCatching { context.startActivity(launchIntent) }
            .onFailure { Log.e(TAG, "Unable to launch Menene after boot", it) }
    }

    companion object {
        private const val TAG = "MeneneBoot"
    }
}
