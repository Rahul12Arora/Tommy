package com.companionstudio.nyx.setup

import android.Manifest
import android.app.AppOpsManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.PowerManager
import android.os.Process
import android.provider.Settings
import androidx.core.app.NotificationManagerCompat

/** The four things S-03 asks for, in screen order (US-01). */
enum class SetupPermission { USAGE_ACCESS, OVERLAY, NOTIFICATIONS, BATTERY }

/** Which of them are granted right now. */
data class PermissionStatus(val granted: Set<SetupPermission>) {
    /** In screen order; empty when everything is granted. */
    val missing: List<SetupPermission> get() = SetupPermission.entries.filterNot { it in granted }
}

/** Reads the current state from the system. Re-read on every resume, so a row ticks itself after a trip to Settings. */
class PermissionChecks(private val context: Context) {

    fun current(): PermissionStatus = PermissionStatus(SetupPermission.entries.filter(::isGranted).toSet())

    fun isGranted(permission: SetupPermission): Boolean = when (permission) {
        SetupPermission.USAGE_ACCESS -> hasUsageAccess()
        SetupPermission.OVERLAY -> Settings.canDrawOverlays(context)
        SetupPermission.NOTIFICATIONS -> NotificationManagerCompat.from(context).areNotificationsEnabled()
        SetupPermission.BATTERY ->
            context.getSystemService(PowerManager::class.java).isIgnoringBatteryOptimizations(context.packageName)
    }

    /** The system screen where the user grants [permission]. Notifications on Android 13+ ask with a dialog first. */
    fun settingsIntent(permission: SetupPermission): Intent = when (permission) {
        SetupPermission.USAGE_ACCESS -> Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS)
        // Since Android 11 this always opens the full list and ignores a package: URI, so none is passed
        SetupPermission.OVERLAY -> Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION)
        SetupPermission.NOTIFICATIONS ->
            Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
        // The settings list, not the one-tap request dialog (ARCHITECTURE §10)
        SetupPermission.BATTERY -> Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS)
    }

    /** This app's own page in Settings, where phone brands keep autostart and per-app battery options. */
    fun appDetailsIntent(): Intent =
        Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", context.packageName, null))

    private fun hasUsageAccess(): Boolean {
        val appOps = context.getSystemService(AppOpsManager::class.java)
        val mode = appOps.unsafeCheckOpNoThrow(AppOpsManager.OPSTR_GET_USAGE_STATS, Process.myUid(), context.packageName)
        // MODE_DEFAULT means the permission itself decides
        if (mode == AppOpsManager.MODE_DEFAULT) {
            return context.checkSelfPermission(Manifest.permission.PACKAGE_USAGE_STATS) == PackageManager.PERMISSION_GRANTED
        }
        return mode == AppOpsManager.MODE_ALLOWED
    }
}
