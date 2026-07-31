package ro.menene.app.kiosk

import android.app.Activity
import android.app.ActivityManager
import android.app.admin.DevicePolicyManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.os.Build
import android.os.UserManager
import android.provider.Settings
import android.util.Log
import android.view.View
import android.view.WindowManager
import ro.menene.app.MainActivity
import ro.menene.app.data.LibraryPreferences

enum class KioskState {
    DISABLED,
    FULLSCREEN_ONLY,
    SCREEN_PINNING,
    LOCK_TASK_ACTIVE,
    DEVICE_OWNER_READY,
}

object KioskController {
    private const val TAG = "MeneneKiosk"

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

    fun state(context: Context): KioskState {
        if (!LibraryPreferences(context).kioskEnabled) return KioskState.DISABLED
        val activityManager = context.getSystemService(ActivityManager::class.java)
        return when (activityManager.lockTaskModeState) {
            ActivityManager.LOCK_TASK_MODE_LOCKED -> KioskState.LOCK_TASK_ACTIVE
            ActivityManager.LOCK_TASK_MODE_PINNED -> KioskState.SCREEN_PINNING
            else -> if (isDeviceOwner(context)) KioskState.DEVICE_OWNER_READY else KioskState.FULLSCREEN_ONLY
        }
    }

    fun prepareAndEnter(activity: Activity) {
        applyImmersive(activity)
        if (!LibraryPreferences(activity).kioskEnabled) return
        if (isDeviceOwner(activity) && configureDeviceOwner(activity)) enterLockTask(activity)
    }

    fun enableKiosk(activity: Activity) {
        LibraryPreferences(activity).kioskEnabled = true
        if (isDeviceOwner(activity)) {
            if (configureDeviceOwner(activity)) enterLockTask(activity)
        } else {
            requestScreenPinning(activity)
        }
        applyImmersive(activity)
    }

    fun prepareForExternalActivity(activity: Activity) {
        if (isLocked(activity)) {
            runCatching { activity.stopLockTask() }
                .onFailure { Log.e(TAG, "Unable to stop lock task for external activity", it) }
        }
    }

    fun openAndroidSettingsTemporarily(activity: Activity) {
        prepareForExternalActivity(activity)
        activity.startActivity(Intent(Settings.ACTION_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }

    fun isDeviceOwner(context: Context): Boolean =
        context.getSystemService(DevicePolicyManager::class.java).isDeviceOwnerApp(context.packageName)

    fun isLocked(context: Context): Boolean =
        context.getSystemService(ActivityManager::class.java).lockTaskModeState != ActivityManager.LOCK_TASK_MODE_NONE

    fun configureDeviceOwner(context: Context): Boolean {
        val manager = context.getSystemService(DevicePolicyManager::class.java)
        if (!manager.isDeviceOwnerApp(context.packageName)) return false
        val admin = adminComponent(context)
        val homeAlias = homeAliasComponent(context)
        return runCatching {
            context.packageManager.setComponentEnabledSetting(
                homeAlias,
                PackageManager.COMPONENT_ENABLED_STATE_ENABLED,
                PackageManager.DONT_KILL_APP,
            )
            manager.setLockTaskPackages(admin, arrayOf(context.packageName))
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                manager.setLockTaskFeatures(admin, DevicePolicyManager.LOCK_TASK_FEATURE_NONE)
            }
            manager.addUserRestriction(admin, UserManager.DISALLOW_CREATE_WINDOWS)
            val homeFilter = IntentFilter(Intent.ACTION_MAIN).apply {
                addCategory(Intent.CATEGORY_HOME)
                addCategory(Intent.CATEGORY_DEFAULT)
            }
            manager.addPersistentPreferredActivity(admin, homeFilter, homeAlias)
        }.onFailure { Log.e(TAG, "Unable to configure Device Owner kiosk", it) }.isSuccess
    }

    fun enterLockTask(activity: Activity): Boolean {
        val manager = activity.getSystemService(DevicePolicyManager::class.java)
        if (!manager.isLockTaskPermitted(activity.packageName)) return false
        if (isLocked(activity)) return true
        return runCatching { activity.startLockTask() }
            .onFailure { Log.e(TAG, "Unable to enter lock task", it) }
            .isSuccess
    }

    fun requestScreenPinning(activity: Activity): Boolean {
        if (isLocked(activity)) return true
        return runCatching { activity.startLockTask() }
            .onFailure { Log.e(TAG, "Unable to request screen pinning", it) }
            .isSuccess
    }

    fun disableKiosk(activity: Activity) {
        LibraryPreferences(activity).kioskEnabled = false
        if (isLocked(activity)) {
            runCatching { activity.stopLockTask() }
                .onFailure { Log.e(TAG, "Unable to stop lock task", it) }
        }
        val manager = activity.getSystemService(DevicePolicyManager::class.java)
        if (!manager.isDeviceOwnerApp(activity.packageName)) return
        val admin = adminComponent(activity)
        runCatching {
            manager.clearPackagePersistentPreferredActivities(admin, activity.packageName)
            manager.setLockTaskPackages(admin, emptyArray())
            manager.clearUserRestriction(admin, UserManager.DISALLOW_CREATE_WINDOWS)
            activity.packageManager.setComponentEnabledSetting(
                homeAliasComponent(activity),
                PackageManager.COMPONENT_ENABLED_STATE_DISABLED,
                PackageManager.DONT_KILL_APP,
            )
        }.onFailure { Log.e(TAG, "Unable to disable Device Owner kiosk", it) }
    }

    fun adminComponent(context: Context): ComponentName =
        ComponentName(context, MeneneDeviceAdminReceiver::class.java)

    private fun homeAliasComponent(context: Context): ComponentName =
        ComponentName(context.packageName, "${context.packageName}.MeneneHomeActivity")
}
