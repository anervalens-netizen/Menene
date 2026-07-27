package ro.mehene.app.kiosk

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import ro.mehene.app.MainActivity
import ro.mehene.app.data.LibraryPreferences

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) {
        if (intent?.action != Intent.ACTION_BOOT_COMPLETED) return
        if (!LibraryPreferences(context).kioskEnabled) return

        val launchIntent = Intent(context, MainActivity::class.java).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
        }
        runCatching { context.startActivity(launchIntent) }
    }
}
