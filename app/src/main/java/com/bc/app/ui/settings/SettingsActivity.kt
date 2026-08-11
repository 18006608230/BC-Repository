package com.bc.app.ui.settings

import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
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
 * 提供：暗黑模式切换、数据备份与恢复
 */
class SettingsActivity : AppCompatActivity() {

    private lateinit var binding: ActivitySettingsBinding

    companion object {
        private const val REQUEST_EXPORT = 1001
        private const val REQUEST_IMPORT = 1002
        private const val MIME_JSON = "application/json"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivitySettingsBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // 页面返回按钮
        binding.buttonBack.setOnClickListener { finish() }

        setupDarkModeToggle()
        setupBackupButtons()
    }

    private fun setupDarkModeToggle() {
        val isDark = SettingsUtils.isDarkMode(this)
        binding.switchDarkMode.isChecked = isDark

        binding.switchDarkMode.setOnCheckedChangeListener { _, checked ->
            SettingsUtils.setDarkMode(this, checked)
            SettingsUtils.applyTheme(application, checked)
            // 重建当前界面以应用主题
            recreate()
        }
    }

    private fun setupBackupButtons() {
        binding.buttonExport.setOnClickListener { launchExportPicker() }
        binding.buttonImport.setOnClickListener { launchImportPicker() }
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

    /**
     * 执行数据导出
     */
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

    /**
     * 执行数据导入
     */
    private fun performImport(uri: Uri) {
        // 二次确认，避免误操作覆盖现有数据
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
