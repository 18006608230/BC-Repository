package com.bc.app.ui.calendar

import com.bc.app.ui.base.BaseActivity

import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.bc.app.data.db.BrawnCatalogDatabase
import com.bc.app.data.entity.TrainingRecord
import com.bc.app.databinding.ActivityCalendarBinding
import com.bc.app.databinding.DialogRecordActionsBinding
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

/**
 * 日历视图
 * 以月份网格展示训练日，点击某天查看当天训练记录
 */
class CalendarActivity : BaseActivity() {

    private lateinit var binding: ActivityCalendarBinding

    private val calendar = Calendar.getInstance()
    private val adapter = CalendarRecordAdapter { record -> showRecordOptions(record) }
    private val dateFormat = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())

    // 该月每天的记录数（用于标记训练日）
    private val recordsOnDay = HashMap<String, Int>()
    private val selectedDayKey = HashMap<String, String>()

    // 当前选中的日期 key（yyyy-MM-dd）
    private var selectedDateKey: String? = null
    // 当前选中日期是否为今天
    private var isSelectedToday = false

    // 排课：date -> Schedule (用于日历标注 + 点击直接开练)
    private val scheduleOnDay = HashMap<String, com.bc.app.data.entity.WorkoutSchedule>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityCalendarBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // 页面返回按钮
        binding.buttonBack.setOnClickListener { finish() }

        binding.recyclerViewRecords.layoutManager = LinearLayoutManager(this)
        binding.recyclerViewRecords.adapter = adapter

        buildWeekHeader()
        binding.buttonPrevMonth.setOnClickListener { changeMonth(-1) }
        binding.buttonNextMonth.setOnClickListener { changeMonth(1) }
        binding.buttonStartWorkout.setOnClickListener {
            // 仅当选中“今天”时才可点：按今天的计划开始训练
            startTodayWorkout()
        }

        loadData(calendar)
    }

    /**
     * 构建星期标题行
     */
    private fun buildWeekHeader() {
        val weekDays = arrayOf("日", "一", "二", "三", "四", "五", "六")
        binding.layoutWeekHeader.removeAllViews()
        val weekHeaderColor = com.bc.app.utils.SettingsUtils.resolveAttrColor(this, com.bc.app.R.attr.bcPrimary)
        weekDays.forEach { day ->
            val tv = TextView(this).apply {
                text = day
                gravity = Gravity.CENTER
                textSize = 14f
                // 周几文字加粗深主题色
                setTextColor(weekHeaderColor)
                paint.isFakeBoldText = true
                layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
            }
            binding.layoutWeekHeader.addView(tv)
        }
    }

    /**
     * 切换月份
     */
    private fun changeMonth(monthDelta: Int) {
        calendar.add(Calendar.MONTH, monthDelta)
        loadData(calendar)
    }

    // ------------------------------------------------------------------
    // 记录长按菜单：收藏该动作 / 删除这条记录
    // ------------------------------------------------------------------

    /**
     * 长按一条记录后弹出的操作菜单。
     *
     * 「收藏」作用在**动作**上（TrainingRecord 没有收藏字段，加字段要动数据库迁移；
     * 而 Exercise 本来就有 isFavorite，用户想收藏的通常也是「这个动作」）。
     */
    private fun showRecordOptions(record: TrainingRecord) {
        val exercise = adapter.exerciseMap[record.exerciseId]
        val dialogBinding = DialogRecordActionsBinding.inflate(layoutInflater)

        dialogBinding.textDialogTitle.text = exercise?.name ?: "动作记录"
        dialogBinding.buttonFavorite.text =
            if (exercise?.isFavorite == true) "取消收藏" else "收藏该动作"

        val dialog = AlertDialog.Builder(this)
            .setView(dialogBinding.root)
            .create()

        dialogBinding.buttonFavorite.setOnClickListener {
            dialog.dismiss()
            toggleFavorite(record)
        }
        dialogBinding.buttonDelete.setOnClickListener {
            dialog.dismiss()
            confirmDeleteRecord(record, exercise?.name)
        }
        dialog.show()
    }

    private fun toggleFavorite(record: TrainingRecord) {
        lifecycleScope.launch {
            val database = BrawnCatalogDatabase.getDatabase(this@CalendarActivity)
            val newState = withContext(Dispatchers.IO) {
                val exercise = database.exerciseDao().getById(record.exerciseId)
                if (exercise == null) {
                    null
                } else {
                    val next = !exercise.isFavorite
                    database.exerciseDao().update(exercise.copy(isFavorite = next))
                    next
                }
            }
            if (newState == null) {
                Toast.makeText(this@CalendarActivity, "找不到对应动作", Toast.LENGTH_SHORT).show()
            } else {
                Toast.makeText(
                    this@CalendarActivity,
                    if (newState) "已收藏" else "已取消收藏",
                    Toast.LENGTH_SHORT
                ).show()
                // 刷新动作映射，下次长按才会显示正确的「取消收藏」
                loadData(calendar, selectedDateKey)
            }
        }
    }

    /**
     * 删除是**不可撤回**的，且会影响连续天数、统计页和日历，所以再确认一次。
     */
    private fun confirmDeleteRecord(record: TrainingRecord, exerciseName: String?) {
        AlertDialog.Builder(this)
            .setTitle("删除记录")
            .setMessage(
                "将删除「${exerciseName ?: "该动作"}」这条记录。\n" +
                    "删除后日历、统计页和训练历史都会同步移除，且无法恢复。确定删除吗？"
            )
            .setNegativeButton("取消", null)
            .setPositiveButton("删除") { _, _ -> deleteRecord(record) }
            .show()
    }

    private fun deleteRecord(record: TrainingRecord) {
        lifecycleScope.launch {
            val database = BrawnCatalogDatabase.getDatabase(this@CalendarActivity)
            withContext(Dispatchers.IO) {
                database.trainingRecordDao().delete(record)
            }
            Toast.makeText(this@CalendarActivity, "已删除", Toast.LENGTH_SHORT).show()
            // 重新读库：月网格标记 + 当天列表一起刷新，并回到原来选中的日期
            loadData(calendar, selectedDateKey)
        }
    }

    /**
     * 加载当月数据并重建日历网格
     *
     * @param restoreDay 刷新后要选中的日期（删除 / 收藏后保持原来的选中日，
     *                   否则会跳回今天，操作完就找不到刚才在哪了）。
     */
    private fun loadData(cal: Calendar, restoreDay: String? = null) {
        // 更新标题
        val titleFormat = SimpleDateFormat("yyyy年M月", Locale.getDefault())
        binding.textViewMonthTitle.text = titleFormat.format(cal.time)

        // 查询所有记录，统计每天的记录数
        val database = BrawnCatalogDatabase.getDatabase(this)
        lifecycleScope.launch {
            val allRecords = withContext(Dispatchers.IO) {
                database.trainingRecordDao().getAllSyncSafe()
            }
            val exerciseMap = withContext(Dispatchers.IO) {
                database.exerciseDao().getAllSyncSafe().associateBy { it.id }
            }

            recordsOnDay.clear()
            for (r in allRecords) {
                val key = dateFormat.format(Date(r.date))
                recordsOnDay[key] = (recordsOnDay[key] ?: 0) + 1
            }

            // 排课：当月（含前后几天）的排课数据
            scheduleOnDay.clear()
            val monthStart = SimpleDateFormat("yyyy-MM-01", Locale.getDefault()).format(cal.time)
            val monthEnd = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(
                java.util.Date(
                    Calendar.getInstance().apply { set(cal.get(Calendar.YEAR), cal.get(Calendar.MONTH) + 1, 0) }.timeInMillis
                )
            )
            val schedules = withContext(Dispatchers.IO) {
                database.workoutScheduleDao().getByDateRange(monthStart, monthEnd)
            }
            for (s in schedules) scheduleOnDay[s.date] = s

            // 保存动作名映射供点击查看
            adapter.exerciseMap = exerciseMap

            buildCalendarGrid(cal)

            // 默认选中今天（若在本月）
            val todayKey = dateFormat.format(Date())
            val todayInMonth = todayKey.startsWith(
                SimpleDateFormat("yyyy-MM", Locale.getDefault()).format(cal.time)
            )
            val targetDay = restoreDay ?: if (todayInMonth) todayKey else null
            if (targetDay != null) {
                showDay(targetDay)
            } else {
                adapter.submitList(emptyList())
                binding.buttonStartWorkout.visibility = View.GONE
            }
        }
    }

    /**
     * 构建日历网格
     */
    private fun buildCalendarGrid(cal: Calendar) {
        binding.layoutCalendarGrid.removeAllViews()

        // 解析主题色：日期上记录的小点/圆圈统一跟随深主题色，排课的方点用主题辅色
        val primaryColor = com.bc.app.utils.SettingsUtils.resolveAttrColor(this, com.bc.app.R.attr.bcPrimary)
        val accentColor = com.bc.app.utils.SettingsUtils.resolveAttrColor(this, com.bc.app.R.attr.bcAccent)

        val year = cal.get(Calendar.YEAR)
        val month = cal.get(Calendar.MONTH)

        // 当月第一天是星期几
        val firstDayCal = Calendar.getInstance().apply {
            set(year, month, 1)
        }
        val firstDayOfWeek = firstDayCal.get(Calendar.DAY_OF_WEEK) - 1 // 0=周日
        val daysInMonth = firstDayCal.getActualMaximum(Calendar.DAY_OF_MONTH)

        // 今天
        val today = Calendar.getInstance()

        // 计算需要多少行
        val totalCells = firstDayOfWeek + daysInMonth
        val rows = (totalCells + 6) / 7

        var day = 1
        for (row in 0 until rows) {
            val rowLayout = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                )
            }

            for (col in 0 until 7) {
                val cellIndex = row * 7 + col
                val isBlank = cellIndex < firstDayOfWeek || day > daysInMonth

                if (isBlank) {
                    // 空白格
                    rowLayout.addView(TextView(this).apply {
                        gravity = Gravity.CENTER
                        layoutParams = LinearLayout.LayoutParams(0, dp(52), 1f)
                    })
                    continue
                }

                val currentDay = day
                val key = dateFormat.format(Date(firstDayCal.timeInMillis + (currentDay - 1) * 86400000L))
                val isToday = year == today.get(Calendar.YEAR) && month == today.get(Calendar.MONTH) && currentDay == today.get(Calendar.DAY_OF_MONTH)
                val hasRecord = recordsOnDay.containsKey(key)
                val sched = scheduleOnDay[key]

                // 每个日期单元格用竖向容器：上方一个固定尺寸的“圆点/数字”指示器，下方一个小标记
                val cell = LinearLayout(this).apply {
                    orientation = LinearLayout.VERTICAL
                    gravity = Gravity.CENTER_HORIZONTAL
                    isClickable = true
                    layoutParams = LinearLayout.LayoutParams(0, dp(56), 1f)
                    setOnClickListener {
                        val d = Calendar.getInstance().apply { set(year, month, currentDay) }
                        onDateSelected(dateFormat.format(d.time), sched)
                    }
                }



                // 主指示器：普通/有记录/今天 三种样式，直径固定 40dp，保证是正圆
                val indicator = TextView(this).apply {
                    gravity = Gravity.CENTER
                    textSize = 15f
                    includeFontPadding = false
                    layoutParams = LinearLayout.LayoutParams(dp(40), dp(40))
                }
                // 下方小标记（勾 / 圆点），仅在有需要时显示
                val marker = TextView(this).apply {
                    gravity = Gravity.CENTER
                    textSize = 12f
                    includeFontPadding = false
                    layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT)
                }

                if (isToday && hasRecord) {
                    // 今天且有记录：深主题色圆 + 白色数字 + 白色小勾
                    indicator.setBackgroundResource(com.bc.app.R.drawable.bg_calendar_today_circle)
                    indicator.setTextColor(0xFFFFFFFF.toInt())
                    indicator.text = "$currentDay"
                    indicator.paint.isFakeBoldText = true
                    marker.text = "✓"
                    marker.setTextColor(primaryColor)
                } else if (isToday) {
                    // 今天无记录：深主题色圆 + 白色数字，无勾
                    indicator.setBackgroundResource(com.bc.app.R.drawable.bg_calendar_today_circle)
                    indicator.setTextColor(0xFFFFFFFF.toInt())
                    indicator.text = "$currentDay"
                    indicator.paint.isFakeBoldText = true
                    marker.text = ""
                } else if (hasRecord) {
                    // 有记录（非今天）：深主题色加粗数字 + 下方深主题色实心点
                    indicator.setBackgroundResource(0)
                    indicator.setTextColor(primaryColor)
                    indicator.text = "$currentDay"
                    indicator.paint.isFakeBoldText = true
                    marker.text = "●"
                    marker.setTextColor(primaryColor)
                    marker.setBackgroundResource(0)
                } else if (sched != null) {
                    // 有排课（计划已同步到日历）：辅色方点标记（与记录点区分），提示当天该练
                    indicator.setBackgroundResource(0)
                    indicator.setTextColor(com.bc.app.utils.SettingsUtils.resolveAttrColor(this, com.bc.app.R.attr.bcTextPrimary))
                    indicator.text = "$currentDay"
                    if (sched.dayName == "休息日") {
                        // 休息日：显示「休」字
                        marker.text = "休"
                        marker.setTextColor(accentColor)
                        marker.setBackgroundResource(0)
                        marker.textSize = 11f
                    } else {
                        marker.text = "▪"
                        marker.setTextColor(accentColor)
                        marker.setBackgroundResource(0)
                        marker.textSize = 14f
                    }
                } else {
                    // 普通日期
                    indicator.setBackgroundResource(0)
                    indicator.setTextColor(com.bc.app.utils.SettingsUtils.resolveAttrColor(this, com.bc.app.R.attr.bcTextPrimary))
                    indicator.text = "$currentDay"
                    marker.text = ""
                }

                cell.addView(indicator)
                cell.addView(marker)
                rowLayout.addView(cell)
                day++
            }
            binding.layoutCalendarGrid.addView(rowLayout)
        }
    }

    /**
     * 显示某天的记录
     */
    private fun showDay(dateKey: String) {
        selectedDateKey = dateKey
        isSelectedToday = dateKey == dateFormat.format(Date())
        // 仅当选中“今天”时显示“开始训练”按钮
        binding.buttonStartWorkout.visibility =
            if (isSelectedToday) View.VISIBLE else View.GONE

        val database = BrawnCatalogDatabase.getDatabase(this)
        binding.textViewSelectedDate.text = "训练记录 · $dateKey"
        lifecycleScope.launch {
            val allRecords = withContext(Dispatchers.IO) {
                database.trainingRecordDao().getAllSyncSafe()
            }
            // 按当天范围过滤（00:00 - 23:59）
            val dayRecords = allRecords.filter { r ->
                dateFormat.format(Date(r.date)) == dateKey
            }
            adapter.submitList(dayRecords)
        }
    }

    /**
     * 日期点击：选中该天。
     *  - 若为“今天”且今天有计划预设：显示开始训练按钮（不弹窗）
     *  - 若为非今天的排课日：打开“查看当日训练计划”（只读视图）；休息日仅提示
     */
    private fun onDateSelected(dateKey: String, sched: com.bc.app.data.entity.WorkoutSchedule?) {
        showDay(dateKey)
        highlightSelection()
        val isToday = dateKey == dateFormat.format(Date())
        // 非今天 + 有计划预设 → 查看当日训练计划（只读），不再弹“开始训练”对话框
        if (!isToday && sched != null) {
            if (sched.dayName == "休息日") {
                Toast.makeText(this, "这天是休息日，不用训练", Toast.LENGTH_SHORT).show()
            } else {
                openWorkout(sched, readOnly = true)
            }
        }
    }

    /**
     * 点击“开始训练”：仅当天可点，按今天的排课/周期推导启动训练执行页。
     * 只加载「当天」对应的训练日动作，不再出现整计划所有天的动作。
     */
    private fun startTodayWorkout() {
        val todayKey = dateFormat.format(Date())
        val sched = scheduleOnDay[todayKey]
        if (sched != null) {
            when {
                // 排到具体训练日：只练当天
                sched.dayId != null -> openWorkout(sched, readOnly = false)
                // 排到休息日
                sched.dayName == "休息日" ->
                    Toast.makeText(this, "今天是休息日，好好休息", Toast.LENGTH_SHORT).show()
                // 旧版「整计划」排课数据：退化为整计划
                else -> openWorkout(sched, readOnly = false)
            }
        } else {
            // 今天没有排课：尝试按当前启用计划的周期推导今天的训练日
            startTodayByPlanDerive(todayKey)
        }
    }

    /**
     * 无排课时：依据当前启用计划「最近一次（<=今天）的排课日期 + 距今天数 % 周期天数」
     * 推导今天应练的训练日；推导不出则提示先同步到日历。
     */
    private fun startTodayByPlanDerive(todayKey: String) {
        lifecycleScope.launch {
            val database = BrawnCatalogDatabase.getDatabase(this@CalendarActivity)
            val activePlan = withContext(Dispatchers.IO) {
                database.trainingPlanDao().getActivePlanSync()
            }
            if (activePlan == null) {
                Toast.makeText(this@CalendarActivity, "尚未设置当前计划，请先在「计划」中设置", Toast.LENGTH_SHORT).show()
                return@launch
            }

            val (hasDays, todayDay) = withContext(Dispatchers.IO) {
                val days = database.planDayDao().getDaysByPlanSync(activePlan.id).sortedBy { it.dayIndex }
                val day = if (days.isEmpty()) null else deriveTodayDay(database, activePlan.id, todayKey, days)
                Pair(days.isNotEmpty(), day)
            }

            if (!hasDays) {
                // 扁平计划（无训练日结构）：整个计划就是今日计划
                val intent = android.content.Intent(this@CalendarActivity, WorkoutActivity::class.java).apply {
                    putExtra(WorkoutActivity.EXTRA_PLAN_ID, activePlan.id)
                    putExtra(WorkoutActivity.EXTRA_DAY_ID, -1L)
                    putExtra(WorkoutActivity.EXTRA_DATE, System.currentTimeMillis())
                    putExtra(WorkoutActivity.EXTRA_READ_ONLY, false)
                }
                startActivity(intent)
                return@launch
            }

            when {
                todayDay == null ->
                    Toast.makeText(this@CalendarActivity, "今天还没有排课，请先在计划详情「同步到日历」", Toast.LENGTH_SHORT).show()
                todayDay.isRestDay ->
                    Toast.makeText(this@CalendarActivity, "今天是休息日，好好休息", Toast.LENGTH_SHORT).show()
                else -> {
                    val intent = android.content.Intent(this@CalendarActivity, WorkoutActivity::class.java).apply {
                        putExtra(WorkoutActivity.EXTRA_PLAN_ID, activePlan.id)
                        putExtra(WorkoutActivity.EXTRA_DAY_ID, todayDay.id)
                        putExtra(WorkoutActivity.EXTRA_DATE, System.currentTimeMillis())
                        putExtra(WorkoutActivity.EXTRA_READ_ONLY, false)
                    }
                    startActivity(intent)
                }
            }
        }
    }

    /**
     * 周期推导：找到该计划最近一次（<=今天）的「训练日」排课作为锚点
     * （休息日排课 dayId 为空无法定位周期，跳过），
     * 今天 = 锚点训练日 + 距今天数 在周期中的位置。
     */
    private suspend fun deriveTodayDay(
        database: BrawnCatalogDatabase,
        planId: Long,
        todayKey: String,
        days: List<com.bc.app.data.entity.PlanDay>
    ): com.bc.app.data.entity.PlanDay? {
        val schedules = database.workoutScheduleDao().getByPlan(planId)
        val anchor = schedules
            .filter { it.date <= todayKey && it.dayId != null }
            .maxByOrNull { it.date } ?: return null
        val anchorIdx = days.indexOfFirst { it.id == anchor.dayId }
        if (anchorIdx < 0) return null
        val offset = try {
            java.time.temporal.ChronoUnit.DAYS.between(
                java.time.LocalDate.parse(anchor.date),
                java.time.LocalDate.parse(todayKey)
            )
        } catch (e: Exception) {
            return null
        }
        if (offset < 0) return null
        return days[(anchorIdx + offset.toInt()) % days.size]
    }

    private fun openWorkout(sch: com.bc.app.data.entity.WorkoutSchedule, readOnly: Boolean) {
        val intent = android.content.Intent(this, WorkoutActivity::class.java).apply {
            putExtra(WorkoutActivity.EXTRA_PLAN_ID, sch.planId)
            if (sch.dayId != null) putExtra(WorkoutActivity.EXTRA_DAY_ID, sch.dayId)
            else putExtra(WorkoutActivity.EXTRA_DAY_ID, -1L)
            putExtra(WorkoutActivity.EXTRA_DATE, System.currentTimeMillis())
            putExtra(WorkoutActivity.EXTRA_READ_ONLY, readOnly)
        }
        startActivity(intent)
    }

    /**
     * 高亮当前选中的日期（简单实现：重建网格并重新标记）
     */
    private fun highlightSelection() {
        buildCalendarGrid(calendar)
    }

    private fun dp(value: Int): Int {
        return (value * resources.displayMetrics.density).toInt()
    }

    override fun onDestroy() {
        super.onDestroy()
    }

    override fun onResume() {
        super.onResume()
        // 从训练执行页返回后刷新数据（新记录会更新圆点和当天列表）
        loadData(calendar)
    }
}
