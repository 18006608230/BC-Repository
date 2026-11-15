package com.bc.app.ui.timer

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.app.AlarmManager
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import android.os.SystemClock
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.bc.app.R
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlin.math.ceil

/**
 * 组间休息计时器 · 前台服务
 *
 * 为什么需要服务：原实现用 CountDownTimer 直接跑在 TimerActivity 里，
 *  - 旋转屏幕 / 被系统回收 → 计时归零（无状态保存）；
 *  - 切后台、息屏 → 只有 Toast + 一次性震动，看不到也感受不到；
 *  - 没有通知，无法离开计时页去记录动作。
 *
 * 现在由本服务持有权威计时状态：
 *  - 用 [SystemClock.elapsedRealtime] 计算剩余（含深睡时间，不受 Doze 影响的墙钟漂移）；
 *  - 前台通知实时展示倒计时，并提供 暂停/继续、+15 秒、停止 操作；
 *  - 结束时走高优先级渠道响铃 + 震动提醒，可点通知回到计时页；
 *  - 状态通过 [state] 暴露给 Activity，Activity 重建后直接读回，旋转不再丢。
 *
 * 会话生命周期：只在「运行中 / 已暂停」期间保活；重置或停止后立即结束会话并撤下通知，
 * 不会留下常驻通知。
 */
class RestTimerService : Service() {

    /** 对外暴露的单一计时状态（进程内共享，Activity 据此渲染） */
    data class TimerState(
        /** 是否存在会话（运行中或已暂停）。false 表示服务已结束、页面只用本地选择的时长 */
        val active: Boolean = false,
        /** 本次会话设定的总时长（秒） */
        val totalSeconds: Int = DEFAULT_SECONDS,
        /** 剩余秒数（向上取整，结束时为 0） */
        val remainSeconds: Int = DEFAULT_SECONDS,
        /** 是否正在跑 */
        val running: Boolean = false,
        /** 是否刚刚自然结束（用于页面显示 00:00 与「重新开始」） */
        val finished: Boolean = false
    )

    companion object {
        const val DEFAULT_SECONDS = 90
        const val MAX_SECONDS = 5999

        private const val ACTION_START = "com.bc.app.timer.START"
        private const val ACTION_PAUSE = "com.bc.app.timer.PAUSE"
        private const val ACTION_RESUME = "com.bc.app.timer.RESUME"
        private const val ACTION_ADD = "com.bc.app.timer.ADD"
        private const val ACTION_STOP = "com.bc.app.timer.STOP"
        private const val ACTION_RESTORE = "com.bc.app.timer.RESTORE"

        /** 兜底闹钟的动作（RestTimerReceiver 接收） */
        const val ACTION_ALERT = "com.bc.app.timer.ALERT"

        private const val EXTRA_SECONDS = "extra_seconds"

        private const val CHANNEL_ONGOING = "rest_timer_ongoing"
        private const val NOTIF_ID_ONGOING = 1001

        private val _state = MutableStateFlow(TimerState())
        /** Activity 观察此状态渲染界面 */
        val state: StateFlow<TimerState> = _state.asStateFlow()

        /** 开始一段新会话（会覆盖旧会话） */
        fun start(context: Context, seconds: Int) {
            dispatch(context, ACTION_START, seconds)
        }

        fun pause(context: Context) = dispatch(context, ACTION_PAUSE)

        fun resume(context: Context) = dispatch(context, ACTION_RESUME)

        /** 加时（运行中加到结束时间，暂停时加到剩余量），同时抬高会话总时长 */
        fun addSeconds(context: Context, seconds: Int) = dispatch(context, ACTION_ADD, seconds)

        /** 结束会话并撤下通知（「重置」与通知里的「停止」都走这里） */
        fun stop(context: Context) = dispatch(context, ACTION_STOP)

        /**
         * 页面离开很久后重新进入时，把「刚结束」标记清掉，
         * 让界面回到「用本地选择的时长待开始」的状态。
         */
        fun consumeFinished() {
            if (_state.value.finished && !_state.value.active) {
                _state.value = _state.value.copy(finished = false)
            }
        }

        /**
         * 从持久化记录里恢复会话。
         * 进程被系统杀掉后，页面重新打开时调用它，倒计时会从正确的剩余继续。
         */
        fun restore(context: Context) = dispatch(context, ACTION_RESTORE)

        private fun dispatch(context: Context, action: String, seconds: Int = 0) {
            val intent = Intent(context, RestTimerService::class.java)
                .setAction(action)
                .putExtra(EXTRA_SECONDS, seconds)
            // minSdk 26，startForegroundService 一定可用；非 START 动作只会在会话已存在时下发
            ContextCompat.startForegroundService(context, intent)
        }
    }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var tickJob: Job? = null

    /** 用 elapsedRealtime 记录的结束时刻（运行中有效） */
    private var endAtElapsed = 0L

    /** 暂停时保留的剩余毫秒 */
    private var remainMillisOnPause = DEFAULT_SECONDS * 1000L

    /** 最近一次写入状态的总时长，供加时后重置回退用 */
    private var sessionTotalSeconds = DEFAULT_SECONDS

    private var lastPublishedSecond = -1

    override fun onCreate() {
        super.onCreate()
        createChannels()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_START -> {
                val seconds = (intent.getIntExtra(EXTRA_SECONDS, DEFAULT_SECONDS))
                    .coerceIn(1, MAX_SECONDS)
                startNewSession(seconds)
            }

            ACTION_PAUSE -> {
                if (!hasSession()) return stopWithoutForeground()
                pauseCountdown()
            }

            ACTION_RESUME -> {
                if (!hasSession()) return stopWithoutForeground()
                resumeCountdown()
            }

            ACTION_ADD -> {
                if (!hasSession()) return stopWithoutForeground()
                addSecondsInternal(intent.getIntExtra(EXTRA_SECONDS, 15))
            }

            ACTION_STOP -> {
                endSession()
                return START_NOT_STICKY
            }

            ACTION_RESTORE -> restoreSession()

            else -> {
                // 没有动作（例如系统重启服务）时不恢复，避免出现无来源的常驻通知
                if (!hasSession()) return stopWithoutForeground()
                // 会话仍在：重新挂上前台通知（进程被重建后的兜底）
                goForeground(buildOngoingNotification(remainSecondsNow(), _state.value.running))
            }
        }
        return START_NOT_STICKY
    }

    /**
     * 从 [RestTimerStore] 恢复会话（第 2 层保障）
     *
     * 进程被杀后重新启动时，内存里的会话已经没了，但结束时刻还在磁盘上。
     * 这里算出当前剩余，然后按「仍在跑」或「已暂停」分别恢复；
     * 如果发现早就超时了，就直接按结束处理并补上提醒。
     */
    private fun restoreSession() {
        if (_state.value.active) return

        // 磁盘上没有会话：这次是误启动，快速合规退出
        val remain = RestTimerStore.remainMillis(this)
        if (remain == null) {
            stopSelf()
            return
        }
        sessionTotalSeconds = RestTimerStore.totalSeconds(this)

        if (remain <= 0L) {
            // 进程不在的这段时间里已经超时，补上提醒
            finishAsExpired()
            return
        }

        remainMillisOnPause = remain
        if (RestTimerStore.isRunning(this)) {
            endAtElapsed = SystemClock.elapsedRealtime() + remain
            lastPublishedSecond = -1
            publish(remainSeconds = millisToDisplaySeconds(remain), running = true, finished = false)
            goForeground(buildOngoingNotification(millisToDisplaySeconds(remain), running = true))
            scheduleAlarm()
            launchTick()
        } else {
            publish(remainSeconds = millisToDisplaySeconds(remain), running = false, finished = false)
            goForeground(buildOngoingNotification(millisToDisplaySeconds(remain), running = false))
        }
    }

    /** 恢复时发现已超时：直接收尾并补上提醒（不是倒计时自然走到 0） */
    private fun finishAsExpired() {
        val total = sessionTotalSeconds
        RestTimerStore.markFinished(this)
        cancelAlarm()
        _state.value = TimerState(
            active = false,
            totalSeconds = total,
            remainSeconds = 0,
            running = false,
            finished = true
        )
        TimerVibrator.restFinished(this)
        RestTimerNotifier.postFinish(this, openAppIntent())
        stopForegroundCompat()
        stopSelf()
    }

    override fun onDestroy() {
        tickJob?.cancel()
        scope.cancel()
        super.onDestroy()
    }

    // ------------------------------------------------------------------
    // 会话控制
    // ------------------------------------------------------------------

    private fun hasSession(): Boolean = _state.value.active

    private fun startNewSession(seconds: Int) {
        tickJob?.cancel()
        NotificationManagerCompat.from(this).cancel(RestTimerNotifier.NOTIF_ID_ALERT)
        lastPublishedSecond = -1
        sessionTotalSeconds = seconds
        remainMillisOnPause = seconds * 1000L
        endAtElapsed = SystemClock.elapsedRealtime() + remainMillisOnPause
        publish(remainSeconds = seconds, running = true, finished = false)
        goForeground(buildOngoingNotification(seconds, running = true))
        persistSession(running = true)
        scheduleAlarm()
        launchTick()
    }

    private fun pauseCountdown() {
        tickJob?.cancel()
        remainMillisOnPause = (endAtElapsed - SystemClock.elapsedRealtime()).coerceAtLeast(0L)
        val remain = millisToDisplaySeconds(remainMillisOnPause)
        publish(remainSeconds = remain, running = false, finished = false)
        goForeground(buildOngoingNotification(remain, running = false))
        // 暂停期间不需要到点提醒
        cancelAlarm()
        persistSession(running = false)
    }

    private fun resumeCountdown() {
        if (remainMillisOnPause <= 0L) {
            // 已经归零，直接当作结束
            finishSession()
            return
        }
        endAtElapsed = SystemClock.elapsedRealtime() + remainMillisOnPause
        publish(remainSeconds = millisToDisplaySeconds(remainMillisOnPause), running = true, finished = false)
        goForeground(buildOngoingNotification(millisToDisplaySeconds(remainMillisOnPause), running = true))
        persistSession(running = true)
        scheduleAlarm()
        launchTick()
    }

    private fun addSecondsInternal(seconds: Int) {
        sessionTotalSeconds = (sessionTotalSeconds + seconds).coerceAtMost(MAX_SECONDS)
        if (_state.value.running) {
            endAtElapsed += seconds * 1000L
            val remain = millisToDisplaySeconds(endAtElapsed - SystemClock.elapsedRealtime())
            lastPublishedSecond = -1
            publish(remainSeconds = remain, running = true, finished = false)
            goForeground(buildOngoingNotification(remain, running = true))
            persistSession(running = true)
            scheduleAlarm()
        } else {
            remainMillisOnPause = (remainMillisOnPause + seconds * 1000L)
                .coerceAtMost(MAX_SECONDS * 1000L)
            val remain = millisToDisplaySeconds(remainMillisOnPause)
            publish(remainSeconds = remain, running = false, finished = false)
            goForeground(buildOngoingNotification(remain, running = false))
            persistSession(running = false)
        }
    }

    /** 用户主动停止 / 重置：结束会话，撤下常驻通知，状态回到「无会话」 */
    private fun endSession() {
        tickJob?.cancel()
        NotificationManagerCompat.from(this).cancel(RestTimerNotifier.NOTIF_ID_ALERT)
        cancelAlarm()
        RestTimerStore.clear(this)
        _state.value = TimerState(
            active = false,
            totalSeconds = sessionTotalSeconds,
            remainSeconds = sessionTotalSeconds,
            running = false,
            finished = false
        )
        stopForegroundCompat()
        stopSelf()
    }

    /** 计时自然结束：震动提醒，并结束会话 */
    private fun finishSession() {
        tickJob?.cancel()
        val total = sessionTotalSeconds
        cancelAlarm()
        // 先落盘再提醒：闹钟若同时到达，会看到已结束标记而不再重复震动
        RestTimerStore.markFinished(this)
        _state.value = TimerState(
            active = false,
            totalSeconds = total,
            remainSeconds = 0,
            running = false,
            finished = true
        )
        stopForegroundCompat()
        RestTimerNotifier.postFinish(this, openAppIntent())
        TimerVibrator.restFinished(this)
        stopSelf()
    }

    private fun launchTick() {
        tickJob?.cancel()
        tickJob = scope.launch {
            while (isActive) {
                val remainMillis = endAtElapsed - SystemClock.elapsedRealtime()
                if (remainMillis <= 0L) {
                    finishSession()
                    break
                }
                val remain = millisToDisplaySeconds(remainMillis)
                if (remain != lastPublishedSecond) {
                    lastPublishedSecond = remain
                    publish(remainSeconds = remain, running = true, finished = false)
                }
                // 通知用系统 Chronometer 自行渲染倒计时，这里不必每秒重刷通知
                delay(200)
            }
        }
    }

    private fun publish(remainSeconds: Int, running: Boolean, finished: Boolean) {
        _state.value = TimerState(
            active = true,
            totalSeconds = sessionTotalSeconds,
            remainSeconds = remainSeconds,
            running = running,
            finished = finished
        )
    }

    /** 剩余毫秒 → 显示秒数（向上取整，1.2s 显示 2s，保证 90 起步不会先跳到 89） */
    private fun millisToDisplaySeconds(millis: Long): Int =
        ceil(millis / 1000.0).toInt().coerceAtLeast(0)

    private fun remainSecondsNow(): Int =
        if (_state.value.running) {
            millisToDisplaySeconds(endAtElapsed - SystemClock.elapsedRealtime())
        } else {
            millisToDisplaySeconds(remainMillisOnPause)
        }

    // ------------------------------------------------------------------
    // 通知
    // ------------------------------------------------------------------

    private fun createChannels() {
        val manager = getSystemService(NotificationManager::class.java) ?: return

        manager.createNotificationChannel(
            NotificationChannel(
                CHANNEL_ONGOING,
                getString(R.string.timer_channel_ongoing_name),
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = getString(R.string.timer_channel_ongoing_desc)
                setShowBadge(false)
                enableVibration(false)
                setSound(null, null)
            }
        )

        // 结束提醒渠道交给 RestTimerNotifier 建（闹钟广播也会用同一个渠道）
        RestTimerNotifier.createChannel(this)
    }

    // ------------------------------------------------------------------
    // 持久化 + 兜底闹钟
    // ------------------------------------------------------------------

    /** 把当前会话写入磁盘；进程被杀后靠它恢复 */
    private fun persistSession(running: Boolean) {
        val endEpoch = System.currentTimeMillis() +
            if (running) (endAtElapsed - SystemClock.elapsedRealtime()) else 0L
        RestTimerStore.save(
            this,
            RestTimerStore.Session(
                running = running,
                totalSeconds = sessionTotalSeconds,
                endAtElapsed = endAtElapsed,
                endAtEpoch = endEpoch,
                remainOnPause = remainMillisOnPause
            )
        )
    }

    /**
     * 排一个精确闹钟，在结束时刻唤醒 [RestTimerReceiver]（第 3 层保障）。
     *
     * Android 12（API 31）起 setExactAndAllowWhileIdle 需要 SCHEDULE_EXACT_ALARM，
     * 而这个权限用户可以在系统设置里关掉。没授权就**静默跳过**，
     * 由前台服务那条路径继续兜着 —— 不弹权限、不打断使用。
     */
    private fun scheduleAlarm() {
        val endEpoch = System.currentTimeMillis() + (endAtElapsed - SystemClock.elapsedRealtime())
        if (endEpoch <= System.currentTimeMillis()) return

        val am = getSystemService(AlarmManager::class.java) ?: return
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S &&
            !am.canScheduleExactAlarms()
        ) {
            return
        }
        try {
            am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, endEpoch, alertPendingIntent())
        } catch (e: SecurityException) {
            // 极少数 ROM 会直接拒绝，忽略即可，前台服务仍在计时
        }
    }

    private fun cancelAlarm() {
        val am = getSystemService(AlarmManager::class.java) ?: return
        try {
            am.cancel(alertPendingIntent())
        } catch (e: SecurityException) {
            // 忽略
        }
    }

    private fun alertPendingIntent(): PendingIntent {
        val intent = Intent(this, RestTimerReceiver::class.java).setAction(ACTION_ALERT)
        return PendingIntent.getBroadcast(
            this,
            0,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }

    private fun goForeground(notification: Notification) {
        // Android 14（API 34）起必须显式传前台服务类型；低版本传类型会因未声明而抛异常，
        // 因此 34 以下仍走两参数重载（类型由 manifest 的 foregroundServiceType 决定）。
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            startForeground(
                NOTIF_ID_ONGOING,
                notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE
            )
        } else {
            startForeground(NOTIF_ID_ONGOING, notification)
        }
    }

    private fun stopForegroundCompat() {
        stopForeground(STOP_FOREGROUND_REMOVE)
    }

    /** 非 START 动作但会话已不存在：快速合规退出，避免 startForegroundService 超时崩溃 */
    private fun stopWithoutForeground(): Int {
        stopSelf()
        return START_NOT_STICKY
    }

    private fun buildOngoingNotification(remainSeconds: Int, running: Boolean): Notification {
        val builder = NotificationCompat.Builder(this, CHANNEL_ONGOING)
            .setSmallIcon(R.drawable.ic_timer_notification)
            .setContentTitle(getString(R.string.timer_notification_title))
            .setContentIntent(openAppIntent())
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setCategory(NotificationCompat.CATEGORY_STOPWATCH)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)

        if (running) {
            // 交给系统 Chronometer 渲染倒计时：无需每秒重刷通知，且系统层面持续更新
            builder.setUsesChronometer(true)
                .setChronometerCountDown(true)
                .setWhen(System.currentTimeMillis() + remainSeconds * 1000L)
                .setContentText(getString(R.string.timer_notification_counting))
        } else {
            builder.setShowWhen(false)
                .setContentText(getString(R.string.timer_notification_paused) + " · " + formatSeconds(remainSeconds))
        }

        if (running) {
            builder.addAction(
                0, getString(R.string.timer_action_pause),
                serviceAction(ACTION_PAUSE, NOTIF_ID_ONGOING + 1)
            )
        } else {
            builder.addAction(
                0, getString(R.string.timer_action_resume),
                serviceAction(ACTION_RESUME, NOTIF_ID_ONGOING + 2)
            )
        }
        builder.addAction(
            0, getString(R.string.timer_action_add15),
            serviceAction(ACTION_ADD, NOTIF_ID_ONGOING + 3, 15)
        )
        builder.addAction(
            0, getString(R.string.timer_action_stop),
            serviceAction(ACTION_STOP, NOTIF_ID_ONGOING + 4)
        )

        return builder.build()
    }

    private fun openAppIntent(): PendingIntent {
        val intent = Intent(this, TimerActivity::class.java).apply {
            addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP)
        }
        return PendingIntent.getActivity(
            this,
            0,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }

    /** 通知按钮 → 前台服务动作。通知交互会临时放行后台启动前台服务 */
    private fun serviceAction(action: String, requestCode: Int, seconds: Int = 0): PendingIntent {
        val intent = Intent(this, RestTimerService::class.java)
            .setAction(action)
            .putExtra(EXTRA_SECONDS, seconds)
        return PendingIntent.getForegroundService(
            this,
            requestCode,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }

    private fun formatSeconds(seconds: Int): String {
        val m = seconds / 60
        val s = seconds % 60
        return String.format("%02d:%02d", m, s)
    }
}
