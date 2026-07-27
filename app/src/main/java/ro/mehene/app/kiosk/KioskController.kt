package ro.mehene.app.kiosk

import android.app.Activity
import android.app.ActivityManager
import android.app.admin.DevicePolicyManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Build
import android.provider.Settings
import android.view.View
import android.view.WindowManager
import ro.mehene.app.MainActivity
import ro.mehene.app.data.LibraryPreferences

object KioskController {
    fun applyImmersive(activity: Activity) {
        activity.window.addFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN)
        @Suppress("DEPRECATION")
        activity.window.decorView.systemUiVisibility =
            View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY or
                View.SYSTEM_UI_FLAG_FULLSCREEN or
                View.SYSTEM_UI_FLAG_HIDE_NAVIGATION or
                View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN or
                View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION or
                View.SYSTEM_UI_FLAG_LAYOUT_STABLE
    }

    fun prepareAndEnter(activity: Activity) {
        applyImmersive(activity)
        val preferences = LibraryPreferences(activity)
        if (!preferences.kioskEnabled) return

        if (isDeviceOwner(activity)) {
            configureDeviceOwner(activity)
            enterLockTask(activity)
        }
    }

    fun isDeviceOwner(context: Context): Boolean {
        val manager = context.getSystemService(DevicePolicyManager::class.java)
        return manager.isDeviceOwnerApp(context.packageName)
    }

    fun isLocked(context: Context): Boolean {
        val activityManager = context.getSystemService(ActivityManager::class.java)
        return activityManager.lockTaskModeState != ActivityManager.LOCK_TASK_MODE_NONE
    }

    fun configureDeviceOwner(context: Context) {
        val manager = context.getSystemService(DevicePolicyManager::class.java)
        if (!manager.isDeviceOwnerApp(context.packageName)) return
        val admin = adminComponent(context)

        runCatching {
            manager.setLockTaskPackages(admin, arrayOf(context.packageName))
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                manager.setLockTaskFeatures(admin, DevicePolicyManager.LOCK_TASK_FEATURE_NONE)
            }
            val homeFilter = IntentFilter(Intent.ACTION_MAIN).apply {
                addCategory(Intent.CATEGORY_HOME)
                addCategory(Intent.CATEGORY_DEFAULT)
            }
            manager.addPersistentPreferredActivity(
                admin,
                homeFilter,
                ComponentName(context, MainActivity::class.java),
            )
        }
    }

    fun enterLockTask(activity: Activity) {
        val manager = activity.getSystemService(DevicePolicyManager::class.java)
        if (!manager.isLockTaskPermitted(activity.packageName)) return
        if (isLocked(activity)) return
        runCatching { activity.startLockTask() }
    }

    fun requestScreenPinning(activity: Activity) {
        if (isLocked(activity)) return
        runCatching { activity.startLockTask() }
    }

    fun disableKiosk(activity: Activity) {
        if (isLocked(activity)) runCatching { activity.stopLockTask() }
        val manager = activity.getSystemService(DevicePolicyManager::class.java)
        if (!manager.isDeviceOwnerApp(activity.packageName)) return
        val admin = adminComponent(activity)
        runCatching {
            manager.clearPackagePersistentPreferredActivities(admin, activity.packageName)
            manager.setLockTaskPackages(admin, emptyArray())
        }
    }

    fun openAndroidSettings(activity: Activity) {
        disableKiosk(activity)
        val intent = Intent(Settings.ACTION_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        activity.startActivity(intent)
    }

    fun adminComponent(context: Context): ComponentName =
        ComponentName(context, MeheneDeviceAdminReceiver::class.java)
}
