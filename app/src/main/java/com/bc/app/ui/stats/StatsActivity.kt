package com.bc.app.ui.stats

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.bc.app.data.db.BrawnCatalogDatabase
import com.bc.app.data.entity.Exercise
import com.bc.app.data.entity.TrainingRecord
import com.bc.app.databinding.ActivityStatsBinding
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

/**
 * 训练统计页
 * 展示近一周 / 近一月 / 近一年 各部位训练次数，以扇形图呈现分布占比
 */
class StatsActivity : AppCompatActivity() {

    private lateinit var binding: ActivityStatsBinding

    // 全部记录 + 部位/动作名称映射（用于分布统计）
    private var allRecords: List<TrainingRecord> = emptyList()
    private val bodyPartNameMap = HashMap<Long, String>()
    private val exerciseNameMap = HashMap<Long, Exercise>()

    // 当前选中的时间范围（天）
    private var rangeDays: Int = 7

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityStatsBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // 页面返回按钮
        binding.buttonBack.setOnClickListener { finish() }

        binding.chipGroupRange.setOnCheckedStateChangeListener { _, checkedIds ->
            rangeDays = when {
                checkedIds.contains(binding.chipMonth.id) -> 30
                checkedIds.contains(binding.chipYear.id) -> 365
                else -> 7
            }
            renderBodyPartPie()
        }

        loadStats()
    }

    private fun loadStats() {
        lifecycleScope.launch {
            val database = BrawnCatalogDatabase.getDatabase(this@StatsActivity)
            val allExercises = withContext(Dispatchers.IO) { database.exerciseDao().getAllSyncSafe() }
            allRecords = withContext(Dispatchers.IO) { database.trainingRecordDao().getAllSyncSafe() }
            val allBodyParts = withContext(Dispatchers.IO) { database.bodyPartDao().getAllSync() }

            for (bp in allBodyParts) bodyPartNameMap[bp.id] = bp.name
            for (ex in allExercises) exerciseNameMap[ex.id] = ex

            renderBodyPartPie()
        }
    }

    /**
     * 计算当前时间范围内各部位的训练次数，渲染扇形图 + 明细
     * 统计口径：某部位某天只要训练过（有任一该部位动作的记录）即计 1 次（动作+日期去重）
     */
    private fun renderBodyPartPie() {
        if (allRecords.isEmpty()) {
            binding.pieChartBodyParts.clear()
            binding.textBodyPartDetail.text = "暂无训练记录，先去记录几次训练吧"
            return
        }

        // 时间窗口起点
        val cal = Calendar.getInstance()
        cal.add(Calendar.DAY_OF_YEAR, -(rangeDays - 1))
        cal.set(Calendar.HOUR_OF_DAY, 0); cal.set(Calendar.MINUTE, 0); cal.set(Calendar.SECOND, 0); cal.set(Calendar.MILLISECOND, 0)
        val rangeStart = cal.timeInMillis

        // 部位 -> 训练日期去重集合
        val partSet = HashMap<Long, HashSet<String>>()
        val dayKeyFmt = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
        for (r in allRecords) {
            if (r.date < rangeStart) continue
            val ex = exerciseNameMap[r.exerciseId] ?: continue
            val dayKey = dayKeyFmt.format(Date(r.date))
            val key = ex.bodyPartId.toString() + "_" + dayKey
            partSet.getOrPut(ex.bodyPartId) { HashSet() }.add(key)
        }

        if (partSet.isEmpty()) {
            binding.pieChartBodyParts.clear()
            binding.textBodyPartDetail.text = "当前范围暂无训练记录"
            return
        }

        // 组装扇区数据（按次数降序）
        val slices = partSet.entries
            .map { (partId, days) ->
                val name = bodyPartNameMap[partId] ?: "其他"
                name to days.size.toFloat()
            }
            .sortedByDescending { it.second }
            .map { PieChartView.Slice(it.first, it.second) }

        val totalDays = slices.sumOf { it.value.toInt() }
        binding.pieChartBodyParts.setData(slices, totalDays.toString())

        // 明细文字：胸 5次 · 背 3次 ...
        binding.textBodyPartDetail.text = slices.joinToString("  ·  ") {
            "${it.label} ${it.value.toInt()}次"
        }
    }
}
