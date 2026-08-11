package com.bc.app.ui.calendar

import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.bc.app.data.db.BrawnCatalogDatabase
import com.bc.app.data.entity.TrainingRecord
import com.bc.app.databinding.ActivityCalendarBinding
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
class CalendarActivity : AppCompatActivity() {

    private lateinit var binding: ActivityCalendarBinding

    private val calendar = Calendar.getInstance()
    private val adapter = CalendarRecordAdapter()
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
        weekDays.forEach { day ->
            val tv = TextView(this).apply {
                text = day
                gravity = Gravity.CENTER
                textSize = 14f
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

    /**
     * 加载当月数据并重建日历网格
     */
    private fun loadData(cal: Calendar) {
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
            if (todayInMonth) {
                showDay(todayKey)
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
                    // 今天且有记录：蓝色圆 + 白色数字 + 白色小勾
                    indicator.setBackgroundResource(com.bc.app.R.drawable.bg_calendar_today_circle)
                    indicator.setTextColor(0xFFFFFFFF.toInt())
                    indicator.text = "$currentDay"
                    marker.text = "✓"
                    marker.setTextColor(0xFF1B4FBF.toInt())
                } else if (isToday) {
                    // 今天无记录：蓝色圆 + 白色数字，无勾
                    indicator.setBackgroundResource(com.bc.app.R.drawable.bg_calendar_today_circle)
                    indicator.setTextColor(0xFFFFFFFF.toInt())
                    indicator.text = "$currentDay"
                    marker.text = ""
                } else if (hasRecord) {
                    // 有记录（非今天）：蓝色加粗数字 + 下方蓝点
                    indicator.setBackgroundResource(0)
                    indicator.setTextColor(0xFF1B4FBF.toInt())
                    indicator.text = "$currentDay"
                    marker.text = "●"
                    marker.setTextColor(0xFF1B4FBF.toInt())
                    marker.setBackgroundResource(0)
                } else if (sched != null) {
                    // 有排课（计划已同步到日历）：橙色小方点标记，提示当天该练
                    indicator.setBackgroundResource(0)
                    indicator.setTextColor(getColor(com.bc.app.R.color.bc_text_primary))
                    indicator.text = "$currentDay"
                    marker.text = "▪"
                    marker.setTextColor(0xFFFF7043.toInt())
                    marker.setBackgroundResource(0)
                    marker.textSize = 14f
                } else {
                    // 普通日期
                    indicator.setBackgroundResource(0)
                    indicator.setTextColor(getColor(com.bc.app.R.color.bc_text_primary))
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
     *  - 若为非今天的排课日：打开“查看当日训练计划”（只读视图）
     */
    private fun onDateSelected(dateKey: String, sched: com.bc.app.data.entity.WorkoutSchedule?) {
        showDay(dateKey)
        highlightSelection()
        val isToday = dateKey == dateFormat.format(Date())
        // 非今天 + 有计划预设 → 查看当日训练计划（只读），不再弹“开始训练”对话框
        if (!isToday && sched != null) {
            openWorkout(sched, readOnly = true)
        }
    }

    /**
     * 点击“开始训练”：仅当天可点，按今天的排课/当前计划启动训练执行页（可编辑 + 可跳转）
     */
    private fun startTodayWorkout() {
        val todayKey = dateFormat.format(Date())
        val sched = scheduleOnDay[todayKey]
        if (sched != null) {
            openWorkout(sched, readOnly = false)
        } else {
            // 今天没有排课：按当前启用计划（全部训练日）开始
            val intent = android.content.Intent(this, WorkoutActivity::class.java).apply {
                putExtra(WorkoutActivity.EXTRA_PLAN_ID, -1L)
                putExtra(WorkoutActivity.EXTRA_DAY_ID, -1L)
                putExtra(WorkoutActivity.EXTRA_DATE, System.currentTimeMillis())
                putExtra(WorkoutActivity.EXTRA_READ_ONLY, false)
            }
            startActivity(intent)
        }
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
