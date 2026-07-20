package com.alive.alive.util

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import com.alive.alive.MainActivity
import com.alive.alive.R
import com.alive.alive.service.NotificationActionReceiver

object NotificationHelper {

    const val CHANNEL_FOREGROUND = "alive_foreground"
    const val CHANNEL_CARE = "alive_care"
    const val CHANNEL_CHECKIN = "alive_checkin"
    const val CHANNEL_LOCKED = "alive_locked"

    const val NOTIF_FOREGROUND_ID = 1001
    const val NOTIF_CARE_ID = 1002
    const val NOTIF_CHECKIN_ID = 1003
    const val NOTIF_LOCKED_ID = 1004

    const val ACTION_ACTIVE_CHECKIN = "com.alive.alive.action.ACTIVE_CHECKIN"

    fun ensureChannels(context: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val nm = context.getSystemService(NotificationManager::class.java)

        nm.createNotificationChannel(
            NotificationChannel(
                CHANNEL_FOREGROUND,
                context.getString(R.string.fg_channel_name),
                NotificationManager.IMPORTANCE_LOW
            ).apply { description = context.getString(R.string.fg_channel_desc) }
        )
        nm.createNotificationChannel(
            NotificationChannel(
                CHANNEL_CARE,
                context.getString(R.string.care_channel_name),
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = context.getString(R.string.care_channel_desc)
                setShowBadge(true)
            }
        )
        nm.createNotificationChannel(
            NotificationChannel(
                CHANNEL_CHECKIN,
                "签到结果",
                NotificationManager.IMPORTANCE_DEFAULT
            ).apply { description = "主动/被动签到完成时的提示" }
        )
        nm.createNotificationChannel(
            NotificationChannel(
                CHANNEL_LOCKED,
                "锁定提醒",
                NotificationManager.IMPORTANCE_HIGH
            ).apply { description = "SMTP 配置已锁定，无法发送邮件时的提醒" }
        )
    }

    fun buildForeground(context: Context): Notification {
        val openIntent = PendingIntent.getActivity(
            context, 0,
            Intent(context, MainActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        return NotificationCompat.Builder(context, CHANNEL_FOREGROUND)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle(context.getString(R.string.fg_notification_title))
            .setContentText(context.getString(R.string.fg_notification_text))
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setShowWhen(false)
            .setContentIntent(openIntent)
            .build()
    }

    /**
     * 关怀通知。slot=12 为首次「你还好吗？」；slot=18 为二次更强提醒。
     * 同一 [NOTIF_CARE_ID] 会被新通知覆盖，避免堆叠。
     */
    fun buildCare(context: Context, slot: Int): Notification {
        val openIntent = PendingIntent.getActivity(
            context, 1,
            Intent(context, MainActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        val actionIntent = PendingIntent.getBroadcast(
            context, 2,
            Intent(context, NotificationActionReceiver::class.java)
                .setAction(ACTION_ACTIVE_CHECKIN),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        val (title, text) = when (slot) {
            18 -> context.getString(R.string.care_notification_title_18) to
                context.getString(R.string.care_notification_text_18)
            else -> context.getString(R.string.care_notification_title) to
                context.getString(R.string.care_notification_text)
        }
        return NotificationCompat.Builder(context, CHANNEL_CARE)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle(title)
            .setContentText(text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .setOngoing(true)
            .setPriority(if (slot == 18) NotificationCompat.PRIORITY_MAX else NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_REMINDER)
            .setContentIntent(openIntent)
            .addAction(
                R.drawable.ic_launcher_foreground,
                context.getString(R.string.care_notification_button),
                actionIntent
            )
            .build()
    }

    fun showCare(context: Context, slot: Int = 12) {
        ensureChannels(context)
        val nm = context.getSystemService(NotificationManager::class.java)
        nm.notify(NOTIF_CARE_ID, buildCare(context, slot))
    }

    fun cancelCare(context: Context) {
        val nm = context.getSystemService(NotificationManager::class.java)
        nm.cancel(NOTIF_CARE_ID)
    }

    fun showCheckInResult(context: Context, title: String, text: String) {
        ensureChannels(context)
        val nm = context.getSystemService(NotificationManager::class.java)
        val n = NotificationCompat.Builder(context, CHANNEL_CHECKIN)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle(title)
            .setContentText(text)
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .build()
        nm.notify(NOTIF_CHECKIN_ID, n)
    }
}
