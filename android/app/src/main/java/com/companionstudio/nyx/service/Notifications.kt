package com.companionstudio.nyx.service

import android.Manifest
import android.app.Notification
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.util.Log
import androidx.core.app.NotificationChannelCompat
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.companionstudio.nyx.MainActivity
import com.companionstudio.nyx.R
import com.companionstudio.nyx.setup.SetupPermission

/** Channels and the notifications the service posts (§14.4): N-01 pet running, N-03 permission lost. */
object Notifications {
    const val PET_RUNNING_ID = 1
    private const val PERMISSION_LOST_ID = 3
    private const val CHANNEL_PET = "pet"
    private const val CHANNEL_ALERTS = "alerts"
    private const val TAG = "Nyx"

    /** Safe to call on every start: creating an existing channel changes nothing. */
    fun createChannels(context: Context) {
        val manager = NotificationManagerCompat.from(context)
        manager.createNotificationChannel(
            NotificationChannelCompat.Builder(CHANNEL_PET, NotificationManagerCompat.IMPORTANCE_LOW)
                .setName(context.getString(R.string.notification_channel_pet))
                .build(),
        )
        manager.createNotificationChannel(
            NotificationChannelCompat.Builder(CHANNEL_ALERTS, NotificationManagerCompat.IMPORTANCE_DEFAULT)
                .setName(context.getString(R.string.notification_channel_alerts))
                .build(),
        )
    }

    /** N-01. Android requires it while the service runs; tapping it opens Home. */
    fun petRunning(context: Context): Notification =
        NotificationCompat.Builder(context, CHANNEL_PET)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(context.getString(R.string.app_name))
            .setContentText(context.getString(R.string.notification_pet_running))
            .setOngoing(true)
            .setContentIntent(openApp(context, MainActivity.intent(context, openPermissions = false), requestCode = 1))
            .build()

    /** N-03: what stopped working, in the same words as S-03; tapping it opens S-03 to fix it (US-26). */
    fun showPermissionLost(context: Context, missing: List<SetupPermission>) {
        val text = missing.joinToString(" ") { context.getString(withoutIt(it)) } + " " +
            context.getString(R.string.notification_permission_lost_fix)
        val notification = NotificationCompat.Builder(context, CHANNEL_ALERTS)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(context.getString(R.string.notification_permission_lost_title))
            .setContentText(text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .setAutoCancel(true)
            .setContentIntent(openApp(context, MainActivity.intent(context, openPermissions = true), requestCode = 3))
            .build()
        if (context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
            Log.w(TAG, "Permission-lost notification not shown: notifications are off")
            return
        }
        NotificationManagerCompat.from(context).notify(PERMISSION_LOST_ID, notification)
    }

    fun cancelPermissionLost(context: Context) {
        NotificationManagerCompat.from(context).cancel(PERMISSION_LOST_ID)
    }

    private fun withoutIt(permission: SetupPermission): Int = when (permission) {
        SetupPermission.USAGE_ACCESS -> R.string.permission_usage_without
        SetupPermission.OVERLAY -> R.string.permission_overlay_without
        SetupPermission.NOTIFICATIONS -> R.string.permission_notifications_without
        SetupPermission.BATTERY -> R.string.permission_battery_without
    }

    private fun openApp(context: Context, intent: Intent, requestCode: Int): PendingIntent =
        PendingIntent.getActivity(
            context,
            requestCode,
            intent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
}
