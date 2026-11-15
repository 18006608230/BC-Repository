package com.bc.app.utils

import android.content.Context
import android.content.SharedPreferences
import androidx.annotation.StyleRes
import com.bc.app.R

/**
 * 应用设置工具类
 * 负责颜色主题等用户偏好的持久化（已用颜色主题取代原暗黑模式）
 */
object SettingsUtils {

    private const val PREFS_NAME = "brawn_catalog_settings"
    private const val KEY_THEME = "color_theme"

    /** 可选颜色主题：黑/深蓝/深绿/酒红/日落黄/爱马仕橙/深紫（亮一点、不荧光） */
    enum class Theme(val key: String, val label: String, @StyleRes val styleRes: Int) {
        BLUE("blue", "深蓝", R.style.Theme_BrawnCatalog_Blue),
        BLACK("black", "黑", R.style.Theme_BrawnCatalog_Black),
        RED("red", "酒红", R.style.Theme_BrawnCatalog_Red),
        GREEN("green", "深绿", R.style.Theme_BrawnCatalog_Green),
        YELLOW("yellow", "日落黄", R.style.Theme_BrawnCatalog_Yellow),
        PURPLE("purple", "深紫", R.style.Theme_BrawnCatalog_Purple),
        ORANGE("orange", "爱马仕橙", R.style.Theme_BrawnCatalog_Orange);

        companion object {
            fun fromKey(key: String?): Theme = entries.firstOrNull { it.key == key } ?: BLUE
        }
    }

    /** 读取当前颜色主题 */
    fun getTheme(context: Context): Theme {
        return Theme.fromKey(getPrefs(context).getString(KEY_THEME, Theme.BLUE.key))
    }

    /** 保存当前颜色主题 */
    fun setTheme(context: Context, theme: Theme) {
        getPrefs(context).edit().putString(KEY_THEME, theme.key).apply()
    }

    /**
     * 当前主题对应的 style 资源 id。
     * 由 BaseActivity 在 onCreate / setContentView 之前 setTheme() 调用。
     */
    @StyleRes
    fun getThemeResId(context: Context): Int = getTheme(context).styleRes

    /**
     * 主题主色（用于 Kotlin 代码里需要动态设置颜色的场景，如设置页色块、按钮着色）
     */
    fun resolvePrimaryColor(context: Context): Int =
        resolveAttrColor(context, R.attr.bcPrimary)

    /**
     * 从当前主题解析指定属性对应的颜色
     */
    fun resolveAttrColor(context: Context, attrRes: Int): Int {
        val tv = android.util.TypedValue()
        return if (context.theme.resolveAttribute(attrRes, tv, true)) tv.data
        else android.graphics.Color.GRAY
    }

    private fun getPrefs(context: Context): SharedPreferences {
        return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    }
}