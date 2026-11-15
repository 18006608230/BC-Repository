package com.bc.app.ui.timer

import com.bc.app.ui.base.BaseActivity

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.view.GestureDetector
import android.view.MotionEvent
import android.view.View
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import com.bc.app.R
import com.bc.app.databinding.ActivityTimerBinding
import com.bc.app.utils.SettingsUtils
import kotlinx.coroutines.launch

/**
 * 组间休息计时器页面（界面层）
 *
 * 计时本身由 [RestTimerService] 前台服务负责，本页只做两件事：
 *  1. 订阅 [RestTimerService.state] 渲染倒计时与按钮状态；
 *  2. 把用户操作（开始/暂停/继续/重置/加时）转成服务动作。
 *
 * 因此离开本页、息屏、旋转屏幕都不会中断计时；通知栏可查看与操作。
 */
class TimerActivity : BaseActivity() {

    companion object {
        const val EXTRA_DEFAULT_SECONDS = "extra_default_seconds"
        private const val KEY_SELECTED_SECONDS = "key_selected_seconds"
        private const val PREFS_TIMER_HINT = "timer_hints"
        private const val KEY_BATTERY_HINT_DISMISSED = "battery_hint_dismissed"
    }

    private lateinit var binding: ActivityTimerBinding

    /** 本地选择的时长：仅在服务无会话时决定大数字显示什么 */
    private var selectedSeconds = RestTimerService.DEFAULT_SECONDS

    /** 首屏需要拉起服务、但正在等通知权限回调 */
    private var pendingStartAfterPermission = false

    /** 通知权限回调：无论是否授权都继续启动，未授权时通知不显示但计时与震动仍有效 */
    private val notificationPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { _ ->
            if (pendingStartAfterPermission) {
                pendingStartAfterPermission = false
                startSession()
            }
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityTimerBinding.inflate(layoutInflater)
        setContentView(binding.root)

        title = "组间休息"
        supportActionBar?.title = "组间休息"

        binding.buttonBack.setOnClickListener { finish() }

        selectedSeconds = savedInstanceState?.getInt(KEY_SELECTED_SECONDS)
            ?: intent.getIntExtra(EXTRA_DEFAULT_SECONDS, RestTimerService.DEFAULT_SECONDS)

        // 上一轮的「刚结束」状态只在本次打开时清一次，之后以服务状态为准
        RestTimerService.consumeFinished()

        setupListeners()
        setupTimerDisplayTap()
        observeTimer()
        restoreSessionIfNeeded()
        maybeShowBatteryHint()
    }

    /**
     * 进程被系统杀掉后，服务内存里的会话没了，但结束时刻还留在磁盘上。
     * 回到页面时把它们接起来，倒计时从正确的剩余继续（而不是从头再来）。
     */
    private fun restoreSessionIfNeeded() {
        if (RestTimerService.state.value.active) return
        if (!RestTimerStore.hasSession(this)) return
        RestTimerService.restore(this)
    }

    /**
     * 省电白名单引导。
     *
     * 国产 ROM（小米 / 华为 / OPPO / vivo）的后台清理可能无视前台服务直接杀进程，
     * 这是软件层面唯一拦不住的一环，只能请用户把 App 加进白名单。
     * 只在「确实还没加白名单」且用户没点过「知道了」时提示一次。
     */
    private fun maybeShowBatteryHint() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) return
        val pm = getSystemService(android.os.PowerManager::class.java) ?: return
        if (pm.isIgnoringBatteryOptimizations(packageName)) return

        val prefs = getSharedPreferences(PREFS_TIMER_HINT, MODE_PRIVATE)
        if (prefs.getBoolean(KEY_BATTERY_HINT_DISMISSED, false)) return

        binding.layoutBatteryHint.visibility = View.VISIBLE
        binding.buttonBatteryDismiss.setOnClickListener {
            prefs.edit().putBoolean(KEY_BATTERY_HINT_DISMISSED, true).apply()
            binding.layoutBatteryHint.visibility = View.GONE
        }
        binding.buttonBatterySettings.setOnClickListener {
            prefs.edit().putBoolean(KEY_BATTERY_HINT_DISMISSED, true).apply()
            binding.layoutBatteryHint.visibility = View.GONE
            try {
                startActivity(
                    Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS)
                        .setData(Uri.parse("package:$packageName"))
                )
            } catch (e: Exception) {
                // 部分 ROM 没有这个设置页，忽略即可
            }
        }
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putInt(KEY_SELECTED_SECONDS, selectedSeconds)
    }

    // ------------------------------------------------------------------
    // 状态订阅
    // ------------------------------------------------------------------

    private fun observeTimer() {
        lifecycleScope.launch {
            RestTimerService.state.collect { state -> render(state) }
        }
    }

    /**
     * 渲染规则：
     *  - 服务有会话 → 一切以服务为准（这样通知里 +15 秒 也能同步到页面）
     *  - 服务无会话 → 以本地选择的时长为准（避免与刚发出的服务动作产生竞态覆盖）
     */
    private fun render(state: RestTimerService.TimerState) {
        if (state.active) {
            selectedSeconds = state.totalSeconds
            updateTimerDisplay(state.remainSeconds)
            binding.buttonStartTimer.text =
                if (state.running) "暂停" else "继续"
            binding.textViewTimerHint.visibility =
                if (state.running) View.VISIBLE else View.INVISIBLE
        } else {
            updateTimerDisplay(selectedSeconds)
            binding.buttonStartTimer.text =
                if (state.finished) "重新开始" else "开始"
            binding.textViewTimerHint.visibility = View.INVISIBLE
        }
        // 始终把当前显示值写回按钮语义，供无障碍/长按提示等使用
        binding.buttonStartTimer.contentDescription = binding.buttonStartTimer.text
    }

    /** 无会话时的本地渲染（不回读服务状态，避免读到尚未落地的旧值） */
    private fun showIdle() {
        updateTimerDisplay(selectedSeconds)
        binding.buttonStartTimer.text = "开始"
        binding.textViewTimerHint.visibility = View.INVISIBLE
    }

    // ------------------------------------------------------------------
    // 交互
    // ------------------------------------------------------------------

    private fun setupListeners() {
        binding.buttonStartTimer.setOnClickListener { onPrimaryAction() }
        binding.buttonResetTimer.setOnClickListener { onReset() }

        binding.buttonSet30s.setOnClickListener { onPickPreset(30) }
        binding.buttonSet60s.setOnClickListener { onPickPreset(60) }
        binding.buttonSet90s.setOnClickListener { onPickPreset(90) }
        binding.buttonSet2min.setOnClickListener { onPickPreset(120) }
        binding.buttonSet3min.setOnClickListener { onPickPreset(180) }
        binding.buttonSet5min.setOnClickListener { onPickPreset(300) }
    }

    /**
     * 大数字手势：
     *  - 单击加时：按触摸 x 坐标判断——左半（分，前两位）加 1 分钟，右半（秒，后两位）加 15 秒。
     *    运行中禁用，避免倒计时中途改乱。
     *  - 长按：触发一次震动自检，方便确认本机是否真的会震（不必等完整倒计时）。
     */
    private fun setupTimerDisplayTap() {
        val gestureDetector = GestureDetector(
            this,
            object : GestureDetector.SimpleOnGestureListener() {
                override fun onLongPress(e: MotionEvent) {
                    onTestVibration()
                }

                override fun onSingleTapUp(e: MotionEvent): Boolean {
                    if (RestTimerService.state.value.running) return true
                    val width = binding.textViewTimer.width.toFloat()
                    if (width > 0f) {
                        onAdjust(if (e.x < width / 2f) 60 else 15)
                    }
                    return true
                }
            }
        )
        binding.textViewTimer.setOnTouchListener { _, event ->
            gestureDetector.onTouchEvent(event)
            true
        }
    }

    /** 震动自检：直接打一次测试波形，并提示排查方向 */
    private fun onTestVibration() {
        TimerVibrator.test(this)
        Toast.makeText(this, R.string.timer_vibration_test, Toast.LENGTH_SHORT).show()
    }

    /** 主按钮：开始 / 暂停 / 继续 / 重新开始 */
    private fun onPrimaryAction() {
        val state = RestTimerService.state.value
        when {
            state.active && state.running -> RestTimerService.pause(this)
            state.active && !state.running -> RestTimerService.resume(this)
            else -> requestNotificationThenStart()
        }
    }

    /** 重置：结束当前会话（撤下通知）并回到所选时长待开始 */
    private fun onReset() {
        if (RestTimerService.state.value.active) {
            RestTimerService.stop(this)
        } else {
            RestTimerService.consumeFinished()
        }
        showIdle()
    }

    /** 预设时长：结束当前会话并采用新时长 */
    private fun onPickPreset(seconds: Int) {
        if (RestTimerService.state.value.active) {
            RestTimerService.stop(this)
        }
        RestTimerService.consumeFinished()
        selectedSeconds = seconds
        showIdle()
    }

    /** 大数字加减时：无会话时改本地选择，已暂停的会话则同步改服务 */
    private fun onAdjust(delta: Int) {
        if (RestTimerService.state.value.active) {
            RestTimerService.addSeconds(this, delta)
            return
        }
        selectedSeconds = (selectedSeconds + delta)
            .coerceIn(1, RestTimerService.MAX_SECONDS)
        RestTimerService.consumeFinished()
        showIdle()
    }

    private fun requestNotificationThenStart() {
        val needsPermission = Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) !=
            PackageManager.PERMISSION_GRANTED
        if (needsPermission) {
            pendingStartAfterPermission = true
            notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        } else {
            startSession()
        }
    }

    private fun startSession() {
        RestTimerService.start(this, selectedSeconds)
    }

    private fun updateTimerDisplay(seconds: Int) {
        val minutes = seconds / 60
        val secs = seconds % 60
        binding.textViewTimer.text = String.format("%02d:%02d", minutes, secs)
        // 颜色：默认主题色；剩余 <15 秒变强调色
        binding.textViewTimer.setTextColor(
            SettingsUtils.resolveAttrColor(
                this,
                if (seconds < 15) R.attr.bcAccent else R.attr.bcPrimary
            )
        )
    }
}
