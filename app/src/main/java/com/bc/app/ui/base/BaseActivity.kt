package com.bc.app.ui.base

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import com.bc.app.utils.SettingsUtils

/**
 * 所有 Activity 的基类：
 * 1. onCreate（setContentView 之前）应用用户选择的颜色主题，让 AppCompatDelegate 读对主题。
 * 2. onResume 检测持久化主题 key 与本 Activity 创建时记录的主题 key，不一致就 recreate()，
 *    从而在「设置页换主题后回到主页」等场景下即时刷新颜色，无需依赖系统配置变化（如横屏）。
 *
 * Android 的 Activity 主题来自 manifest，Application.setTheme() 不会影响已创建的 Activity，
 * 因此必须由每个 Activity 在 super.onCreate() 之前调用 setTheme() 才能运行时换肤。
 * 而主题偏好变化又不在 Android 默认重建 Activity 的条件里，所以用 onResume 兜底比对。
 */
abstract class BaseActivity : AppCompatActivity() {

    /** 本 Activity 实例被创建时所应用的主题 key（来自 SharedPreferences 当时读取的值）。 */
    private var appliedThemeKey: String? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        // 必须在 super.onCreate() 之前，AppCompatDelegate 才能读到正确的主题
        setTheme(SettingsUtils.getThemeResId(this))
        appliedThemeKey = SettingsUtils.getTheme(this).key
        super.onCreate(savedInstanceState)
    }

    override fun onResume() {
        super.onResume()
        // 如果偏好已变更（例如从设置页返回时），则重建本 Activity 以应用新主题。
        // recreate() 会再次触发 onCreate，那里会重新 setTheme + 更新 appliedThemeKey。
        // 处理首次 onCreate 之后本字段已设置、不需要 recreate 的常见情况。
        val currentKey = SettingsUtils.getTheme(this).key
        if (appliedThemeKey != null && appliedThemeKey != currentKey) {
            recreate()
            // recreate 后 onResume 不会再走（因为 Activity 已被销毁），无需手动置字段
        }
    }
}
