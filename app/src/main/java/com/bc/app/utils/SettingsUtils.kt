package com.bc.app.utils

import android.app.Application
import android.content.Context
import android.content.SharedPreferences

/**
 * 应用设置工具类
 * 负责暗黑模式等用户偏好的持久化
 */
object SettingsUtils {

    private const val PREFS_NAME = "brawn_catalog_settings"
    private const val KEY_DARK_MODE = "dark_mode"

    /**
     * 读取暗黑模式开关状态
     */
    fun isDarkMode(context: Context): Boolean {
        return getPrefs(context).getBoolean(KEY_DARK_MODE, false)
    }

    /**
     * 保存暗黑模式开关状态
     */
    fun setDarkMode(context: Context, enabled: Boolean) {
        getPrefs(context).edit().putBoolean(KEY_DARK_MODE, enabled).apply()
    }

    private fun getPrefs(context: Context): SharedPreferences {
        return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    }

    /**
     * 应用当前主题模式
     * 需在 setContentView 之前调用
     */
    fun applyTheme(application: Application, darkMode: Boolean) {
        val nightMode = if (darkMode) {
            androidx.appcompat.app.AppCompatDelegate.MODE_NIGHT_YES
        } else {
            androidx.appcompat.app.AppCompatDelegate.MODE_NIGHT_NO
        }
        androidx.appcompat.app.AppCompatDelegate.setDefaultNightMode(nightMode)
    }
}
