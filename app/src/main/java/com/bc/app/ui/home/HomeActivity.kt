package com.bc.app.ui.home

import android.content.Intent
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.GridLayoutManager
import com.bc.app.data.db.BrawnCatalogDatabase
import com.bc.app.data.entity.BodyPart
import com.bc.app.data.repository.BodyPartRepository
import com.bc.app.databinding.ActivityHomeBinding
import com.bc.app.ui.body.BodyMetricsActivity
import com.bc.app.ui.calendar.CalendarActivity
import com.bc.app.ui.exercise.BodyPartSubcategoryActivity
import com.bc.app.ui.exercise.ExerciseListActivity
import com.bc.app.ui.plan.PlansActivity
import com.bc.app.ui.settings.SettingsActivity
import com.bc.app.ui.stats.StatsActivity
import com.bc.app.ui.timer.TimerActivity
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

/**
 * 首页：部位选择页
 * 网格展示所有训练部位
 */
class HomeActivity : AppCompatActivity() {

    private lateinit var binding: ActivityHomeBinding
    private lateinit var bodyPartAdapter: BodyPartAdapter
    private lateinit var bodyPartRepository: BodyPartRepository

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // 让状态栏与顶部浅蓝背景同色，使浅蓝色一直延续到屏幕最顶端
        window.statusBarColor = getColor(com.bc.app.R.color.bc_header_bg)

        binding = ActivityHomeBinding.inflate(layoutInflater)
        setContentView(binding.root)

        initRepository()
        initRecyclerView()
        initTopBar()
        initGreeting()
        loadQuickStats()
        observeBodyParts()
    }

    /**
     * 根据当前时段设置问候语
     */
    private fun initGreeting() {
        val hour = Calendar.getInstance().get(Calendar.HOUR_OF_DAY)
        val greeting = when (hour) {
            in 5..11 -> "早上好，开始今天的训练吧 ☀️"
            in 12..17 -> "下午好，保持状态 💪"
            else -> "晚上好，来一组收尾训练 🌙"
        }
        binding.textViewGreeting.text = greeting
    }

    /**
     * 加载首页快捷统计：本周训练次数 / 本周容量 / 连续训练天数
     */
    private fun loadQuickStats() {
        lifecycleScope.launch {
            val database = BrawnCatalogDatabase.getDatabase(this@HomeActivity)
            val allRecords = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                database.trainingRecordDao().getAllSyncSafe()
            }

            // 本周起点（周一 00:00）
            val cal = Calendar.getInstance()
            cal.set(Calendar.HOUR_OF_DAY, 0); cal.set(Calendar.MINUTE, 0); cal.set(Calendar.SECOND, 0); cal.set(Calendar.MILLISECOND, 0)
            cal.set(Calendar.DAY_OF_WEEK, Calendar.MONDAY)
            val weekStart = cal.timeInMillis

            // 本周已训练的天数（本周内有训练记录的独立日期数）
            val weekDays = HashSet<String>()
            val distinctDays = HashSet<String>()
            for (r in allRecords) {
                val dayKey = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date(r.date))
                if (r.date >= weekStart) {
                    weekDays.add(dayKey)
                }
                distinctDays.add(dayKey)
            }

            binding.textWeekSessions.text = weekDays.size.toString()

            // 已训练天数：任意一天有训练记录即 +1（累计独立训练日数）
            binding.textStreak.text = distinctDays.size.toString()
        }
    }

    private fun initTopBar() {
        binding.buttonSettings.setOnClickListener {
            startActivity(Intent(this, SettingsActivity::class.java))
        }

        // 计时器快捷入口（原“本周容量”卡片位置）：蓝色按钮，打开组间休息计时器
        binding.buttonTimerShortcut.setOnClickListener {
            val intent = Intent(this, TimerActivity::class.java).apply {
                putExtra(TimerActivity.EXTRA_DEFAULT_SECONDS, 90)
            }
            startActivity(intent)
        }

        // 本周已训练 / 连续天数 卡片可点击 → 跳转统计页
        binding.cardWeekSessions.setOnClickListener {
            startActivity(Intent(this, StatsActivity::class.java))
        }
        binding.cardStreak.setOnClickListener {
            startActivity(Intent(this, StatsActivity::class.java))
        }

        // Tab 标签栏：日历 / 计划 / 身体数据 / 统计
        // 注意：点击当前已选中的 Tab 会触发 onTabReselected 而非 onTabSelected，
        // 二者都要启动对应页面，否则返回后再次点击同一 Tab 会无反应。
        val listener = object : com.google.android.material.tabs.TabLayout.OnTabSelectedListener {
            override fun onTabSelected(tab: com.google.android.material.tabs.TabLayout.Tab) {
                launchTab(tab.position)
                // 取消常驻选中态：仅保留点击瞬间的蓝色反馈，随后回到未选中灰色
                binding.tabFeatureBar.post { binding.tabFeatureBar.selectTab(null) }
            }

            override fun onTabUnselected(tab: com.google.android.material.tabs.TabLayout.Tab) {}
            override fun onTabReselected(tab: com.google.android.material.tabs.TabLayout.Tab) {
                launchTab(tab.position)
            }
        }
        binding.tabFeatureBar.addOnTabSelectedListener(listener)
    }

    private fun launchTab(position: Int) {
        val activity = when (position) {
            0 -> CalendarActivity::class.java
            1 -> PlansActivity::class.java
            2 -> BodyMetricsActivity::class.java
            else -> StatsActivity::class.java
        }
        startActivity(Intent(this@HomeActivity, activity))
    }

    private fun initRepository() {
        val database = BrawnCatalogDatabase.getDatabase(this)
        bodyPartRepository = BodyPartRepository(database.bodyPartDao())
    }

    private fun initRecyclerView() {
        bodyPartAdapter = BodyPartAdapter { bodyPart, _ ->
            // 所有部位统一进入“子分类二级卡片”页（胸/肩/背/手臂/腿/核心/有氧）
            val intent = Intent(this, BodyPartSubcategoryActivity::class.java).apply {
                putExtra(BodyPartSubcategoryActivity.EXTRA_BODY_PART_ID, bodyPart.id)
                putExtra(BodyPartSubcategoryActivity.EXTRA_BODY_PART_NAME, bodyPart.name)
            }
            startActivity(intent)
        }

        binding.recyclerViewBodyParts.apply {
            layoutManager = GridLayoutManager(this@HomeActivity, 2)
            adapter = bodyPartAdapter
        }
    }

    private fun observeBodyParts() {
        lifecycleScope.launch {
            bodyPartRepository.allBodyParts.collect { bodyParts ->
                bodyPartAdapter.submitList(bodyParts)
            }
        }
    }
}
