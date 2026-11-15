package com.bc.app.ui.timer

import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

/**
 * 结束提醒的兜底闹钟（第 3 层保障）
 *
 * 由 AlarmManager 在结束时刻唤醒。即使倒计时所在的进程已经被系统杀掉，
 * 这一步也能准点把震动和通知发出来 —— 进程活着与否不再影响「提醒会不会到」。
 *
 * 与服务自然结束的协同：两条路径谁先到都会调用 [RestTimerStore.markFinished]，
 * 另一条通过 [RestTimerStore.finishedRecently] 发现已经提醒过就直接跳过，
 * 不会重复震动。
 */
class RestTimerReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != RestTimerService.ACTION_ALERT) return

        // 服务已经处理过（或用户已停止/重置），别重复提醒
        if (RestTimerStore.finishedRecently(context)) return

        val remain = RestTimerStore.remainMillis(context) ?: return
        // 还没到点（例如用户暂停后又恢复导致闹钟被重排前的旧触发）就放过
        if (remain > 1_000L) return

        RestTimerStore.markFinished(context)
        TimerVibrator.restFinished(context)
        RestTimerNotifier.postFinish(context, openTimerPendingIntent(context))
    }

    private fun openTimerPendingIntent(context: Context): PendingIntent {
        val intent = Intent(context, TimerActivity::class.java).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
        }
        return PendingIntent.getActivity(
            context,
            0,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }
}
