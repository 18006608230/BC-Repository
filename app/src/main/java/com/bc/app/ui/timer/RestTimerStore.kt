package com.bc.app.ui.timer

import android.content.Context
import android.os.SystemClock

/**
 * 组间休息会话的持久化（第 2 层保障）
 *
 * 核心思路：**把「计时」从「进程存活」里解耦出来**。
 *
 * 这里存的不是「还剩多少秒」，而是「**结束时刻**」。
 * 进程被系统杀掉后重新起来，只要拿当前时间跟结束时刻一比，就能算出正确剩余 ——
 * 哪怕中间隔了十分钟，也能立刻发现早已超时。
 *
 * 双时钟设计：
 *  - 主用 [SystemClock.elapsedRealtime]（开机以来的单调时钟，含深睡时间，不受 NTP
 *    校时和用户改时间影响）。只要没重启，存下来的结束时刻就一直有效。
 *  - 同时存一份墙钟时刻，仅用于「检测到设备重启过」时兜底换算。
 *
 * 判断重启的方法：把写入时的 elapsedRealtime 一并存下来（锚点）。
 * 恢复时若 `nowElapsed < anchorElapsed`，说明时钟回退了，即中途重启过。
 */
object RestTimerStore {

    private const val PREFS = "rest_timer_session"
    private const val K_ACTIVE = "active"
    private const val K_RUNNING = "running"
    private const val K_TOTAL = "total_seconds"
    private const val K_END_ELAPSED = "end_at_elapsed"
    private const val K_END_EPOCH = "end_at_epoch"
    private const val K_REMAIN_ON_PAUSE = "remain_on_pause"
    private const val K_ANCHOR_ELAPSED = "anchor_elapsed"
    private const val K_ANCHOR_EPOCH = "anchor_epoch"
    private const val K_FINISHED_AT = "finished_at"

    /** 一次会话的完整状态 */
    data class Session(
        val running: Boolean,
        val totalSeconds: Int,
        /** 运行中：结束时刻（elapsedRealtime 基准） */
        val endAtElapsed: Long,
        /** 运行中：结束时刻（墙钟基准，重启后兜底用） */
        val endAtEpoch: Long,
        /** 暂停中：剩余毫秒 */
        val remainOnPause: Long
    )

    private fun prefs(context: Context) =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    fun save(context: Context, session: Session) {
        prefs(context).edit()
            .putBoolean(K_ACTIVE, true)
            .putBoolean(K_RUNNING, session.running)
            .putInt(K_TOTAL, session.totalSeconds)
            .putLong(K_END_ELAPSED, session.endAtElapsed)
            .putLong(K_END_EPOCH, session.endAtEpoch)
            .putLong(K_REMAIN_ON_PAUSE, session.remainOnPause)
            .putLong(K_ANCHOR_ELAPSED, SystemClock.elapsedRealtime())
            .putLong(K_ANCHOR_EPOCH, System.currentTimeMillis())
            .apply()
    }

    /** 用户主动停止 / 重置：整份会话作废 */
    fun clear(context: Context) {
        prefs(context).edit().clear().apply()
    }

    /**
     * 标记「刚刚结束」。用于防止服务自然结束与闹钟兜底**重复提醒**：
     * 两条路径谁先到都会写下这个时间戳，另一条看到就跳过。
     */
    fun markFinished(context: Context) {
        prefs(context).edit()
            .putBoolean(K_ACTIVE, false)
            .putLong(K_FINISHED_AT, System.currentTimeMillis())
            .apply()
    }

    /** 最近是否已经提醒过（默认 3 秒内的重复就忽略） */
    fun finishedRecently(context: Context, windowMs: Long = 3_000L): Boolean {
        val t = prefs(context).getLong(K_FINISHED_AT, 0L)
        return t != 0L && System.currentTimeMillis() - t in 0 until windowMs
    }

    fun hasSession(context: Context): Boolean = prefs(context).getBoolean(K_ACTIVE, false)

    fun isRunning(context: Context): Boolean =
        hasSession(context) && prefs(context).getBoolean(K_RUNNING, false)

    fun totalSeconds(context: Context): Int =
        prefs(context).getInt(K_TOTAL, RestTimerService.DEFAULT_SECONDS)

    fun endAtEpoch(context: Context): Long = prefs(context).getLong(K_END_EPOCH, 0L)

    /**
     * 当前剩余毫秒。没有会话时返回 null。
     * 返回值 <= 0 表示已经超时。
     */
    fun remainMillis(context: Context): Long? {
        val p = prefs(context)
        if (!p.getBoolean(K_ACTIVE, false)) return null

        if (!p.getBoolean(K_RUNNING, false)) {
            return p.getLong(K_REMAIN_ON_PAUSE, 0L)
        }

        val endElapsed = p.getLong(K_END_ELAPSED, 0L)
        val endEpoch = p.getLong(K_END_EPOCH, 0L)
        val anchorElapsed = p.getLong(K_ANCHOR_ELAPSED, 0L)
        val nowElapsed = SystemClock.elapsedRealtime()

        return if (nowElapsed >= anchorElapsed) {
            // 正常情况：单调时钟没回退，直接相减最可靠
            endElapsed - nowElapsed
        } else {
            // 中途重启过，elapsedRealtime 已归零重来，退化为墙钟换算
            endEpoch - System.currentTimeMillis()
        }
    }
}
