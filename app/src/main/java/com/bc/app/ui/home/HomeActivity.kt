package com.bc.app.ui.home

import com.bc.app.ui.base.BaseActivity

import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.withContext
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
class HomeActivity : BaseActivity() {

    private lateinit var binding: ActivityHomeBinding
    private lateinit var bodyPartAdapter: BodyPartAdapter
    private lateinit var bodyPartRepository: BodyPartRepository

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // 让状态栏与顶部浅蓝背景同色，使浅蓝色一直延续到屏幕最顶端
        window.statusBarColor = com.bc.app.utils.SettingsUtils.resolveAttrColor(this, com.bc.app.R.attr.bcHeaderBg)

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
     * 从其它页面返回时刷新快捷统计。
     * 在日历里删掉记录、或记录了休息之后，首页这些数字必须跟着变 ——
     * 否则会停在 onCreate 时的旧值。
     */
    override fun onResume() {
        super.onResume()
        loadQuickStats()
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

        // 本周已训练 → 统计页（部位分布 / 肌群覆盖）
        binding.cardWeekSessions.setOnClickListener {
            startActivity(Intent(this, StatsActivity::class.java))
        }
        // 已训练 → 训练日历（GitHub 热力图）
        binding.cardStreak.setOnClickListener {
            startActivity(Intent(this, TrainingCalendarActivity::class.java))
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
            if (bodyPart.name == REST_PART_NAME) {
                // 休息：没有子分类，先确认再记一次
                confirmAndRecordRest(bodyPart)
            } else {
                // 其余部位进入“子分类二级卡片”页（胸/肩/背/手臂/腿/核心/有氧）
                val intent = Intent(this, BodyPartSubcategoryActivity::class.java).apply {
                    putExtra(BodyPartSubcategoryActivity.EXTRA_BODY_PART_ID, bodyPart.id)
                    putExtra(BodyPartSubcategoryActivity.EXTRA_BODY_PART_NAME, bodyPart.name)
                }
                startActivity(intent)
            }
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

    // ------------------------------------------------------------------
    // 休息卡片
    // ------------------------------------------------------------------

    /**
     * 记录休息前的确认。
     * 休息会直接落一条记录（影响日历、统计和连续天数），误触不好撤回，所以先问一句。
     */
    private fun confirmAndRecordRest(bodyPart: BodyPart) {
        androidx.appcompat.app.AlertDialog.Builder(this)
            .setTitle("记录休息")
            .setMessage("把今天记为一次休息日？")
            .setNegativeButton("取消", null)
            .setPositiveButton("确定") { _, _ -> recordRest(bodyPart) }
            .show()
    }

    /**
     * 记录一次休息。
     *
     * 之所以在该部位下维护一个名为「休息」的**动作**、再插一条训练记录，
     * 是为了**复用训练部位那一套统计口径**：所有统计都是
     *「记录 → 动作 → 部位」反查，这样休息天数会自动出现在饼图、
     * 肌群周覆盖和热力图里，不需要在每个统计处各加一个分支。
     *
     * 同一天只记一次：重复点击给出提示，
     * 否则一天点十次会把热力图刷成一整片深色，失去参考价值。
     */
    private fun recordRest(bodyPart: BodyPart) {
        lifecycleScope.launch {
            val database = BrawnCatalogDatabase.getDatabase(this@HomeActivity)
            val result = withContext(kotlinx.coroutines.Dispatchers.IO) {
                // 1) 确保该部位下有「休息」这个动作
                val existing = database.exerciseDao().getAllSyncSafe()
                    .firstOrNull { it.bodyPartId == bodyPart.id && it.name == REST_EXERCISE_NAME }
                val exercise = existing ?: database.exerciseDao().getById(
                    database.exerciseDao().insert(
                        com.bc.app.data.entity.Exercise(
                            bodyPartId = bodyPart.id,
                            name = REST_EXERCISE_NAME,
                            tips = "主动恢复日",
                            isCustom = false
                        )
                    )
                )

                if (exercise == null) {
                    RestResult.FAILED
                } else if (hasRestToday(database, exercise.id)) {
                    RestResult.ALREADY
                } else {
                    database.trainingRecordDao().insert(
                        com.bc.app.data.entity.TrainingRecord(
                            exerciseId = exercise.id,
                            date = System.currentTimeMillis(),
                            // 休息没有组数 / 重量 / 次数概念，全部留 0（不填占位值，
                            // 否则日历和容量统计里会冒出「1 组 × 0kg」这种假数据）
                            weight = 0f,
                            sets = 0,
                            avgReps = 0,
                            remark = REST_EXERCISE_NAME
                        )
                    )
                    RestResult.OK
                }
            }

            val message = when (result) {
                RestResult.OK -> "已记录一次休息"
                RestResult.ALREADY -> "今天已经记过休息了"
                RestResult.FAILED -> "记录失败，请重试"
            }
            Toast.makeText(this@HomeActivity, message, Toast.LENGTH_SHORT).show()
        }
    }

    private suspend fun hasRestToday(
        database: com.bc.app.data.db.BrawnCatalogDatabase,
        exerciseId: Long
    ): Boolean {
        val cal = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }
        val dayStart = cal.timeInMillis
        return database.trainingRecordDao().getAllSyncSafe()
            .any { it.exerciseId == exerciseId && it.date >= dayStart }
    }

    private enum class RestResult { OK, ALREADY, FAILED }

    companion object {
        /** 休息部位 / 动作统一用这个名字 */
        private const val REST_PART_NAME = "休息"
        private const val REST_EXERCISE_NAME = "休息"
    }
}
