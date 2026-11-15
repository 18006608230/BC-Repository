package com.bc.app.ui.home

import com.bc.app.ui.base.BaseActivity

import android.os.Bundle
import android.view.View
import androidx.lifecycle.lifecycleScope
import com.bc.app.data.db.BrawnCatalogDatabase
import com.bc.app.databinding.ActivityTrainingCalendarBinding
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

/**
 * 训练日历（GitHub 热力图风格）
 *
 * 首页「已训练」卡片进入。用一整年的格子把训练痕迹铺开，
 * 累计之外更直观地看到「连续性」—— 哪几天连着练、哪里断了。
 */
class TrainingCalendarActivity : BaseActivity() {

    private lateinit var binding: ActivityTrainingCalendarBinding

    private val dayKeyFmt = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
    private val dayDisplayFmt = SimpleDateFormat("yyyy年M月d日", Locale.getDefault())

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityTrainingCalendarBinding.inflate(layoutInflater)
        setContentView(binding.root)

        binding.buttonBack.setOnClickListener { finish() }

        // 选中日期说明默认隐藏，点格子后才显示（不再放常驻提示语）
        binding.heatmap.onCellClick = { dayKey, count, isRest -> showDay(dayKey, count, isRest) }

        loadData()
    }

    private fun loadData() {
        lifecycleScope.launch {
            val database = BrawnCatalogDatabase.getDatabase(this@TrainingCalendarActivity)
            val records = withContext(Dispatchers.IO) {
                database.trainingRecordDao().getAllSyncSafe()
            }

            // 休息没有组数 / 重量 / 次数，不能按「动作数」算强度，单独拆出来记
            val exercises = withContext(Dispatchers.IO) {
                database.exerciseDao().getAllSyncSafe()
            }
            val parts = withContext(Dispatchers.IO) {
                database.bodyPartDao().getAllSync()
            }
            val restPartIds = parts.filter { it.name == REST_PART_NAME }.map { it.id }.toHashSet()
            val partOfExercise = exercises.associate { it.id to it.bodyPartId }

            // 当天完成的动作数：一条记录 = 一个动作（**不含休息**）
            val counter = HashMap<String, Int>()
            val restDaySet = HashSet<String>()
            val allDays = HashSet<String>()
            for (r in records) {
                val key = dayKeyFmt.format(Date(r.date))
                allDays.add(key)
                if (partOfExercise[r.exerciseId] in restPartIds) {
                    restDaySet.add(key)
                } else {
                    counter[key] = (counter[key] ?: 0) + 1
                }
            }
            // 累计 / 连续天数按「当天有过训练或休息记录」算，与首页「已训练」口径一致
            val sortedDays = allDays.sorted()
            val (current, longest) = computeStreaks(sortedDays)
            val thisYear = Calendar.getInstance().get(Calendar.YEAR).toString()

            binding.textTotalDays.text = sortedDays.size.toString()
            binding.textCurrentStreak.text = current.toString()
            binding.textLongestStreak.text = longest.toString()
            binding.textYearDays.text = sortedDays.count { it.startsWith(thisYear) }.toString()

            if (sortedDays.isEmpty()) {
                binding.heatmap.visibility = View.GONE
                binding.textSelectedDay.text = "还没有训练记录，先去记录几次训练吧"
                binding.textSelectedDay.visibility = View.VISIBLE
            } else {
                binding.heatmap.visibility = View.VISIBLE
                binding.heatmap.setData(counter, restDaySet)
                // 53 周比屏幕宽，进来先滚到最近一周（右侧），别停在一年前
                binding.heatmapScroll.post {
                    binding.heatmapScroll.fullScroll(View.FOCUS_RIGHT)
                }
            }

            // 每月训练天数（今年）
            val monthDays = IntArray(12)
            for (day in sortedDays) {
                if (!day.startsWith(thisYear)) continue
                val m = day.substring(5, 7).toIntOrNull() ?: continue
                if (m in 1..12) monthDays[m - 1]++
            }
            binding.monthBars.setData(
                monthDays,
                highlightMonth = Calendar.getInstance().get(Calendar.MONTH)
            )
            binding.textMonthScope.text = "$thisYear 年 · 高亮为当前月份"
        }
    }

    private fun showDay(dayKey: String, count: Int, isRest: Boolean) {
        val date = try {
            dayKeyFmt.parse(dayKey)
        } catch (e: Exception) {
            null
        }
        val label = if (date != null) dayDisplayFmt.format(date) else dayKey
        binding.textSelectedDay.text = when {
            isRest && count > 0 -> "$label · 休息，另完成 $count 个动作"
            isRest -> "$label · 休息"
            count > 0 -> "$label · 完成 $count 个动作"
            else -> "$label · 无记录"
        }
        binding.textSelectedDay.visibility = View.VISIBLE
    }

    /**
     * 计算「当前连续天数」与「最长连续天数」。
     *
     * 用 Calendar.add(DAY_OF_YEAR) 逐日推进来比日期字符串，
     * 而不是拿时间戳除以 86400000 —— 后者在夏令时切换日会算出 23/25 小时而误判断链。
     */
    private fun computeStreaks(sortedDays: List<String>): Pair<Int, Int> {
        if (sortedDays.isEmpty()) return 0 to 0
        val daySet = sortedDays.toHashSet()

        // 最长连续：只从「前一天没练」的日子起数，避免重复扫描
        var longest = 0
        for (day in sortedDays) {
            if (shiftDay(day, -1) in daySet) continue
            var cursor = day
            var len = 0
            while (cursor in daySet) {
                len++
                cursor = shiftDay(cursor, 1)
            }
            if (len > longest) longest = len
        }

        // 当前连续：今天没练就从昨天开始往回数（今天还没结束，不该直接判为断）
        var current = 0
        var cursor = dayKeyFmt.format(Date())
        if (cursor !in daySet) cursor = shiftDay(cursor, -1)
        while (cursor in daySet) {
            current++
            cursor = shiftDay(cursor, -1)
        }

        return current to longest
    }

    companion object {
        /** 与 HomeActivity 保持一致：休息部位名 */
        private const val REST_PART_NAME = "休息"
    }

    /** 把 yyyy-MM-dd 平移 @param delta 天（支持负数） */
    private fun shiftDay(dayKey: String, delta: Int): String {
        val date = try {
            dayKeyFmt.parse(dayKey)
        } catch (e: Exception) {
            return dayKey
        } ?: return dayKey
        val cal = Calendar.getInstance().apply {
            time = date
            add(Calendar.DAY_OF_YEAR, delta)
        }
        return dayKeyFmt.format(cal.time)
    }
}
