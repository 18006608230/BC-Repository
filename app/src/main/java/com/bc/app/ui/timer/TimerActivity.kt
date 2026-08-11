package com.bc.app.ui.timer

import android.os.Bundle
import android.os.CountDownTimer
import android.view.MotionEvent
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.bc.app.databinding.ActivityTimerBinding

/**
 * 组间计时器页面
 * 支持自定义时长，结束震动提醒
 */
class TimerActivity : AppCompatActivity() {

    companion object {
        const val EXTRA_DEFAULT_SECONDS = "extra_default_seconds"
    }

    private lateinit var binding: ActivityTimerBinding
    private var countDownTimer: CountDownTimer? = null
    private var isRunning = false
    private var totalSeconds = 90 // 默认90秒

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityTimerBinding.inflate(layoutInflater)
        setContentView(binding.root)

        totalSeconds = intent.getIntExtra(EXTRA_DEFAULT_SECONDS, 90)

        title = "组间休息"
        supportActionBar?.title = "组间休息"

        // 页面返回按钮
        binding.buttonBack.setOnClickListener { finish() }

        setupListeners()
        setupTimerDisplayTap()
        updateTimerDisplay(totalSeconds)
    }

    /**
     * 大数字点击加时：按触摸 x 坐标判断——左半（分，前两位）加 1 分钟，右半（秒，后两位）加 15 秒。
     * 用普通 click 监听按坐标判断，避免 Spannable/ClickableSpan 可能引起的渲染问题。
     */
    private fun setupTimerDisplayTap() {
        binding.textViewTimer.setOnTouchListener { _, event ->
            if (event.action == MotionEvent.ACTION_UP) {
                if (isRunning) return@setOnTouchListener true
                val width = binding.textViewTimer.width.toFloat()
                if (width > 0f) {
                    if (event.x < width / 2f) {
                        adjustTimer(60)   // 分部分：+1 分钟
                    } else {
                        adjustTimer(15)   // 秒部分：+15 秒
                    }
                }
            }
            true
        }
    }

    private fun setupListeners() {
        binding.buttonStartTimer.setOnClickListener {
            if (isRunning) {
                stopTimer()
            } else {
                startTimer()
            }
        }

        binding.buttonResetTimer.setOnClickListener {
            stopTimer()
            updateTimerDisplay(totalSeconds)
        }

        // 快速设置：第一行
        binding.buttonSet30s.setOnClickListener { setTimer(30) }
        binding.buttonSet60s.setOnClickListener { setTimer(60) }
        binding.buttonSet90s.setOnClickListener { setTimer(90) }
        // 快速设置：第二行
        binding.buttonSet2min.setOnClickListener { setTimer(120) }
        binding.buttonSet3min.setOnClickListener { setTimer(180) }
        binding.buttonSet5min.setOnClickListener { setTimer(300) }
    }

    private fun setTimer(seconds: Int) {
        stopTimer()
        totalSeconds = seconds
        updateTimerDisplay(totalSeconds)
    }

    private fun startTimer() {
        if (isRunning) return

        val millisInFuture = totalSeconds * 1000L
        countDownTimer = object : CountDownTimer(millisInFuture, 1000) {
            override fun onTick(millisUntilFinished: Long) {
                val seconds = (millisUntilFinished / 1000).toInt()
                updateTimerDisplay(seconds)
            }

            override fun onFinish() {
                isRunning = false
                updateTimerDisplay(0)
                binding.buttonStartTimer.text = "开始"
                Toast.makeText(this@TimerActivity, "休息时间结束！", Toast.LENGTH_SHORT).show()
                // 震动提醒
                val vibrator = getSystemService(VIBRATOR_SERVICE) as android.os.Vibrator
                if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
                    vibrator.vibrate(android.os.VibrationEffect.createOneShot(500, android.os.VibrationEffect.DEFAULT_AMPLITUDE))
                } else {
                    @Suppress("DEPRECATION")
                    vibrator.vibrate(500)
                }
            }
        }.start()

        isRunning = true
        binding.buttonStartTimer.text = "暂停"
    }

    private fun stopTimer() {
        countDownTimer?.cancel()
        isRunning = false
        binding.buttonStartTimer.text = "开始"
    }

    private fun updateTimerDisplay(seconds: Int) {
        val minutes = seconds / 60
        val secs = seconds % 60
        binding.textViewTimer.text = String.format("%02d:%02d", minutes, secs)
        // 颜色：默认蓝色；剩余 <15 秒变橙色
        binding.textViewTimer.setTextColor(
            if (seconds < 15) getColor(com.bc.app.R.color.bc_accent)
            else getColor(com.bc.app.R.color.bc_primary)
        )
    }

    /**
     * 在大数字上点击时分/秒快捷加时（仅在不运行时生效，避免倒计时中途改乱）
     */
    private fun adjustTimer(secondsToAdd: Int) {
        if (isRunning) return
        totalSeconds = (totalSeconds + secondsToAdd).coerceAtMost(5999)
        updateTimerDisplay(totalSeconds)
    }

    override fun onDestroy() {
        super.onDestroy()
        countDownTimer?.cancel()
    }
}
