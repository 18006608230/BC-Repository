package com.bc.app.ui.timer

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.bc.app.R

/**
 * 「休息结束」通知的发送（供服务与闹钟广播共用）
 *
 * 渠道本身是**静默**的（不响不震）：震动由 [TimerVibrator] 单独发出，
 * 这样即使 Android 13+ 没授予通知权限、通知根本不显示，震动也照常工作。
 */
object RestTimerNotifier {

    const val CHANNEL_ALERT = "rest_timer_alert"
    const val NOTIF_ID_ALERT = 1002

    fun createChannel(context: Context) {
        val manager = context.getSystemService(NotificationManager::class.java) ?: return
        manager.createNotificationChannel(
            NotificationChannel(
                CHANNEL_ALERT,
                context.getString(R.string.timer_channel_alert_name),
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = context.getString(R.string.timer_channel_alert_desc)
                enableVibration(false)
                setSound(null, null)
                setBypassDnd(true)
            }
        )
    }

    /**
     * 发出结束通知。失败（未授予通知权限等）时静默忽略，不影响震动。
     */
    fun postFinish(context: Context, openIntent: android.app.PendingIntent) {
        createChannel(context)
        val notification = NotificationCompat.Builder(context, CHANNEL_ALERT)
            .setSmallIcon(R.drawable.ic_timer_notification)
            .setContentTitle(context.getString(R.string.timer_finished_title))
            .setContentText(context.getString(R.string.timer_finished_text))
            .setContentIntent(openIntent)
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .build()
        try {
            NotificationManagerCompat.from(context).notify(NOTIF_ID_ALERT, notification)
        } catch (_: SecurityException) {
            // 未授予 POST_NOTIFICATIONS（Android 13+）：通知不显示，但震动不受影响
        }
    }
}
