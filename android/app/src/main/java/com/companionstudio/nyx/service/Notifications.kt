package com.companionstudio.nyx.service

import android.Manifest
import android.app.Notification
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.text.format.DateFormat
import android.util.Log
import androidx.core.app.NotificationChannelCompat
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.companionstudio.nyx.MainActivity
import com.companionstudio.nyx.R
import com.companionstudio.nyx.setup.SetupPermission
import java.time.Instant
import java.util.Date

/**
 * Channels and the notifications the service posts (§14.4, §14.5, §14.7): N-01 pet running, N-02 break reminder,
 * N-03 permission lost, N-04 nag while the pet isn't on screen.
 */
object Notifications {
    const val PET_RUNNING_ID = 1
    private const val BREAK_REMINDER_ID = 2
    private const val PERMISSION_LOST_ID = 3
    private const val NAG_ID = 4
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

    /**
     * N-01. Android requires it while the service runs; tapping it opens Home. While the pet is hidden
     * ([hiddenUntil] not `null`) it says until when and offers to show the pet now (US-29).
     */
    fun petRunning(context: Context, hiddenUntil: Instant?): Notification {
        val builder = NotificationCompat.Builder(context, CHANNEL_PET)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(context.getString(R.string.app_name))
            .setOngoing(true)
            .setContentIntent(openApp(context, MainActivity.intent(context, MainActivity.Open.HOME), requestCode = 1))
        if (hiddenUntil == null) {
            return builder.setContentText(context.getString(R.string.notification_pet_running)).build()
        }
        // The phone's 12/24-hour setting
        val time = DateFormat.getTimeFormat(context).format(Date.from(hiddenUntil))
        val showPet = PendingIntent.getBroadcast(
            context,
            0,
            Intent(context, ShowPetReceiver::class.java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        return builder.setContentText(context.getString(R.string.notification_pet_hidden, time))
            .addAction(R.drawable.ic_notification, context.getString(R.string.show_pet), showPet)
            .build()
    }

    /** Replaces N-01 when the pet is hidden or shown again. */
    fun updatePetRunning(context: Context, hiddenUntil: Instant?) {
        // Without the permission the service still runs; Android just doesn't show N-01 (§14.4)
        if (context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) return
        NotificationManagerCompat.from(context).notify(PET_RUNNING_ID, petRunning(context, hiddenUntil))
    }

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
            .setContentIntent(openApp(context, MainActivity.intent(context, MainActivity.Open.PERMISSIONS), requestCode = 3))
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

    /** N-02 with the pet's [line] (US-11). One at a time: a new reminder replaces the last (D-104). */
    fun showBreakReminder(context: Context, line: String) =
        showLine(context, BREAK_REMINDER_ID, context.getString(R.string.notification_break_title), line)

    /** The break was taken, so its reminder no longer applies (D-104). */
    fun cancelBreakReminder(context: Context) {
        NotificationManagerCompat.from(context).cancel(BREAK_REMINDER_ID)
    }

    /** N-04: a nag while the pet is hidden or can't draw (D-24). One at a time, like N-02. */
    fun showNag(context: Context, line: String) =
        showLine(context, NAG_ID, context.getString(R.string.app_name), line)

    private fun showLine(context: Context, id: Int, title: String, line: String) {
        if (context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
            Log.w(TAG, "Notification $id not shown: notifications are off")
            return
        }
        val notification = NotificationCompat.Builder(context, CHANNEL_ALERTS)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(title)
            .setContentText(line)
            .setStyle(NotificationCompat.BigTextStyle().bigText(line))
            .setAutoCancel(true)
            .setContentIntent(openApp(context, MainActivity.intent(context, MainActivity.Open.HOME), requestCode = id))
            .build()
        NotificationManagerCompat.from(context).notify(id, notification)
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
