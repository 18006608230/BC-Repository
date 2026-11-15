package com.bc.app.ui.settings

import com.bc.app.ui.base.BaseActivity

import android.app.Activity
import android.content.Intent
import android.content.res.ColorStateList
import android.net.Uri
import android.os.Bundle
import android.util.TypedValue
import android.view.View
import android.widget.Toast
import androidx.lifecycle.lifecycleScope
import com.bc.app.R
import com.bc.app.data.backup.BackupManager
import com.bc.app.data.db.BrawnCatalogDatabase
import com.bc.app.databinding.ActivitySettingsBinding
import com.bc.app.utils.SettingsUtils
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.IOException

/**
 * 设置页
 * 提供：颜色主题选择（8 种低饱和度配色）、数据备份与恢复
 */
class SettingsActivity : BaseActivity() {

    private lateinit var binding: ActivitySettingsBinding

    companion object {
        private const val REQUEST_EXPORT = 1001
        private const val REQUEST_IMPORT = 1002
        private const val MIME_JSON = "application/json"
        private const val SWATCH_SIZE_DP = 40
        private const val SWATCH_MARGIN_DP = 10
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivitySettingsBinding.inflate(layoutInflater)
        setContentView(binding.root)

        binding.buttonBack.setOnClickListener { finish() }

        setupThemePicker()
        setupBackupButtons()
    }

    /**
     * 渲染 8 个主题色块；点击切换主题并 recreate 当前页面。
     */
    private fun setupThemePicker() {
        val current = SettingsUtils.getTheme(this)
        binding.textCurrentTheme.text = "当前：${current.label}"

        val density = resources.displayMetrics.density
        val size = (SWATCH_SIZE_DP * density).toInt()
        val margin = (SWATCH_MARGIN_DP * density).toInt()

        binding.themeSwatchContainer.removeAllViews()
        SettingsUtils.Theme.entries.forEach { theme ->
            val swatch = View(this).apply {
                layoutParams = android.widget.LinearLayout.LayoutParams(size, size).apply {
                    setMargins(margin, 0, margin, 0)
                }
                background = androidx.core.content.ContextCompat.getDrawable(
                    this@SettingsActivity,
                    if (theme == current) R.drawable.bg_theme_swatch_selected
                    else R.drawable.bg_theme_swatch
                )
                backgroundTintList = ColorStateList.valueOf(resolveThemeColor(theme, R.attr.bcPrimary))
                contentDescription = theme.label
                setOnClickListener {
                    if (SettingsUtils.getTheme(this@SettingsActivity) != theme) {
                        SettingsUtils.setTheme(this@SettingsActivity, theme)
                        recreate()
                    }
                }
            }
            binding.themeSwatchContainer.addView(swatch)
        }
    }

    /**
     * 用指定主题的 styleRes 解析主题属性（如各主题的 bcPrimary），用于给色块染色
     */
    private fun resolveThemeColor(theme: SettingsUtils.Theme, attrRes: Int): Int {
        val themedContext = android.view.ContextThemeWrapper(this, theme.styleRes)
        val tv = TypedValue()
        return if (themedContext.theme.resolveAttribute(attrRes, tv, true)) tv.data
        else android.graphics.Color.GRAY
    }

    private fun setupBackupButtons() {
        binding.buttonExport.setOnClickListener { launchExportPicker() }
        binding.buttonImport.setOnClickListener { launchImportPicker() }
        // 关于 彩蛋：点击弹出"BC牛逼"
        binding.textViewAbout.setOnClickListener { showBcNiuBi() }
    }

    /**
     * 彩蛋弹窗：点击「关于」时显示 —— 无标题，主文字 BC 牛逼 占满弹窗宽度 80%
     */
    private fun showBcNiuBi() {
        val view = layoutInflater.inflate(com.bc.app.R.layout.dialog_easter_egg, null)
        androidx.appcompat.app.AlertDialog.Builder(this)
            .setView(view)
            .setPositiveButton("低调低调", null)
            .show()
    }

    /**
     * 使用系统文件选择器选择导出位置
     */
    private fun launchExportPicker() {
        val intent = Intent(Intent.ACTION_CREATE_DOCUMENT).apply {
            addCategory(Intent.CATEGORY_OPENABLE)
            type = MIME_JSON
            putExtra(Intent.EXTRA_TITLE, "brawn_catalog_backup.json")
        }
        startActivityForResult(intent, REQUEST_EXPORT)
    }

    /**
     * 使用系统文件选择器选择备份文件
     */
    private fun launchImportPicker() {
        val intent = Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
            addCategory(Intent.CATEGORY_OPENABLE)
            type = MIME_JSON
        }
        startActivityForResult(intent, REQUEST_IMPORT)
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (resultCode != Activity.RESULT_OK || data?.data == null) return

        when (requestCode) {
            REQUEST_EXPORT -> performExport(data.data!!)
            REQUEST_IMPORT -> performImport(data.data!!)
        }
    }

    private fun performExport(uri: Uri) {
        setBackupStatus("正在导出…")
        lifecycleScope.launch {
            try {
                val database = BrawnCatalogDatabase.getDatabase(this@SettingsActivity)
                val backup = withContext(Dispatchers.IO) {
                    contentResolver.openOutputStream(uri)?.let { stream ->
                        BackupManager.export(database, stream)
                    } ?: throw IOException("无法打开目标文件")
                }
                setBackupStatus("导出成功：${backup.bodyParts.size}个部位、${backup.exercises.size}个动作、${backup.trainingRecords.size}条记录")
                Toast.makeText(this@SettingsActivity, "备份导出成功", Toast.LENGTH_SHORT).show()
            } catch (e: Exception) {
                setBackupStatus("导出失败：${e.message}")
                Toast.makeText(this@SettingsActivity, "导出失败：${e.message}", Toast.LENGTH_LONG).show()
            }
        }
    }

    private fun performImport(uri: Uri) {
        androidx.appcompat.app.AlertDialog.Builder(this)
            .setTitle("导入备份")
            .setMessage("导入将清空当前全部数据并恢复为备份内容，确定继续吗？")
            .setPositiveButton("确定") { _, _ -> doImport(uri) }
            .setNegativeButton("取消", null)
            .show()
    }

    private fun doImport(uri: Uri) {
        setBackupStatus("正在导入…")
        lifecycleScope.launch {
            try {
                val database = BrawnCatalogDatabase.getDatabase(this@SettingsActivity)
                val backup = withContext(Dispatchers.IO) {
                    contentResolver.openInputStream(uri)?.let { stream ->
                        BackupManager.import(database, stream)
                    } ?: throw IOException("无法打开备份文件")
                }
                setBackupStatus("导入成功：${backup.bodyParts.size}个部位、${backup.exercises.size}个动作、${backup.trainingRecords.size}条记录")
                Toast.makeText(this@SettingsActivity, "备份导入成功", Toast.LENGTH_SHORT).show()
            } catch (e: Exception) {
                setBackupStatus("导入失败：${e.message}")
                Toast.makeText(this@SettingsActivity, "导入失败：${e.message}", Toast.LENGTH_LONG).show()
            }
        }
    }

    private fun setBackupStatus(text: String) {
        binding.textBackupStatus.text = text
    }
}
