package com.bc.app.ui.timer

import android.content.Context
import android.media.AudioAttributes
import android.os.Build
import android.os.VibrationAttributes
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager

/**
 * 组间休息计时器 · 震动反馈
 *
 * 几个容易被忽略、但直接决定「震不震得出来」的点：
 *
 * 1. **必须声明用途为闹钟级（USAGE_ALARM）**。默认用途是 UNKNOWN，在勿扰模式、静音模式
 *    以及小米 / 华为 / OPPO / vivo 等 ROM 的省电策略下会被系统直接吞掉，
 *    表现就是「计时结束了但手机毫无反应」。USAGE_ALARM 属于闹钟级别，可穿透勿扰。
 * 2. **API 31+ 走 VibratorManager** 取 Vibrator，避免已废弃的 VIBRATOR_SERVICE。
 * 3. **支持振幅调节的设备用带强度的波形**，震动更明显可辨；不支持的退化为普通波形。
 * 4. 震动只由这里发出，通知渠道的震动已关闭，避免「通知震一次 + 代码震一次」的叠震。
 */
object TimerVibrator {

    /** 休息结束：三下由轻到重，最后一下最长，隔着手机也能感觉到 */
    private val FINISH_TIMINGS = longArrayOf(0, 420, 180, 420, 180, 700)
    private val FINISH_AMPLITUDES = intArrayOf(0, 170, 0, 200, 0, 255)

    /** 测试震动：短促两下，用于在不等待倒计时的前提下验证设备是否真的会震 */
    private val TEST_TIMINGS = longArrayOf(0, 130, 110, 240)
    private val TEST_AMPLITUDES = intArrayOf(0, 150, 0, 255)

    /** 休息结束时调用 */
    fun restFinished(context: Context) {
        vibrate(context, FINISH_TIMINGS, FINISH_AMPLITUDES)
    }

    /** 震动自检（计时页长按大数字触发） */
    fun test(context: Context) {
        vibrate(context, TEST_TIMINGS, TEST_AMPLITUDES)
    }

    private fun vibrate(context: Context, timings: LongArray, amplitudes: IntArray) {
        val vibrator = context.systemVibrator() ?: return
        if (!vibrator.hasVibrator()) return

        // 先取消残留波形，保证本次从第一下开始
        runCatching { vibrator.cancel() }

        val effect =
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && vibrator.hasAmplitudeControl()) {
                VibrationEffect.createWaveform(timings, amplitudes, -1)
            } else {
                VibrationEffect.createWaveform(timings, -1)
            }

        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                // API 33+：用 VibrationAttributes 声明闹钟级用途
                vibrator.vibrate(
                    effect,
                    VibrationAttributes.Builder()
                        .setUsage(VibrationAttributes.USAGE_ALARM)
                        .build()
                )
            } else {
                // API 26~32：用 AudioAttributes 声明同样的用途
                @Suppress("DEPRECATION")
                vibrator.vibrate(
                    effect,
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_ALARM)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                        .build()
                )
            }
        } catch (e: Exception) {
            // 少数 ROM 会直接拒绝（后台限制 / 省电策略）。计时器不该因此崩溃，
            // 也不该让已经保存好的计时状态受影响，忽略即可。
        }
    }

    private fun Context.systemVibrator(): Vibrator? = try {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            getSystemService(VibratorManager::class.java)?.defaultVibrator
        } else {
            @Suppress("DEPRECATION")
            getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
        }
    } catch (e: Exception) {
        null
    }
}
