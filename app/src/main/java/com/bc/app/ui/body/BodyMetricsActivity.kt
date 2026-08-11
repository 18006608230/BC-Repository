package com.bc.app.ui.body

import android.os.Bundle
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.bc.app.data.db.BrawnCatalogDatabase
import com.bc.app.data.entity.BodyMetric
import com.bc.app.databinding.ActivityBodyMetricsBinding
import com.bc.app.ui.stats.LineChartView
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

/**
 * 身体数据记录页
 * 记录体重、体脂、肌肉量、腰围
 * 页面底部以折线图展示体重变化趋势：近一周 / 近一月 / 近一年
 */
class BodyMetricsActivity : AppCompatActivity() {

    private lateinit var binding: ActivityBodyMetricsBinding
    private lateinit var adapter: BodyMetricAdapter

    private val dateFmt = SimpleDateFormat("M/d", Locale.getDefault())

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityBodyMetricsBinding.inflate(layoutInflater)
        setContentView(binding.root)

        title = "身体数据"

        // 页面返回按钮
        binding.buttonBack.setOnClickListener { finish() }

        adapter = BodyMetricAdapter()
        binding.recyclerViewMetrics.layoutManager = LinearLayoutManager(this)
        binding.recyclerViewMetrics.adapter = adapter

        binding.buttonSaveMetric.setOnClickListener { saveMetric() }

        observeMetrics()
    }

    private fun observeMetrics() {
        val database = BrawnCatalogDatabase.getDatabase(this)
        lifecycleScope.launch {
            database.bodyMetricDao().getAll().collectLatest { metrics ->
                adapter.submitList(metrics)
                updateCharts(database)
            }
        }
    }

    /**
     * 按 近一周 / 近一月 / 近一年 三个时间窗口，绘制体重折线图
     */
    private fun updateCharts(database: BrawnCatalogDatabase) {
        lifecycleScope.launch {
            // 全部体重数据（按日期升序），只保留有体重值的记录
            val all = database.bodyMetricDao().getAllSync()
                .filter { it.weight != null }
                .sortedBy { it.date }
            if (all.isEmpty()) {
                binding.chartWeightWeek.setData(emptyList())
                binding.chartWeightMonth.setData(emptyList())
                binding.chartWeightYear.setData(emptyList())
                return@launch
            }

            val now = System.currentTimeMillis()
            val weekStart = startOfDay(now, -6)
            val monthStart = startOfDay(now, -29)
            val yearStart = startOfDay(now, -364)

            binding.chartWeightWeek.setData(toPoints(all.filter { it.date >= weekStart }))
            binding.chartWeightMonth.setData(toPoints(all.filter { it.date >= monthStart }))
            binding.chartWeightYear.setData(toPoints(all.filter { it.date >= yearStart }))
        }
    }

    private fun startOfDay(ref: Long, dayOffset: Int): Long {
        val cal = Calendar.getInstance().apply {
            timeInMillis = ref
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
            add(Calendar.DAY_OF_YEAR, dayOffset)
        }
        return cal.timeInMillis
    }

    private fun toPoints(metrics: List<BodyMetric>): List<LineChartView.Point> {
        return metrics.map { m ->
            LineChartView.Point(dateFmt.format(Date(m.date)), m.weight ?: 0f)
        }
    }

    private fun saveMetric() {
        val weight = binding.editWeight.text.toString().toFloatOrNull()
        val bodyFat = binding.editBodyFat.text.toString().toFloatOrNull()
        val muscle = binding.editMuscle.text.toString().toFloatOrNull()
        val waist = binding.editWaist.text.toString().toFloatOrNull()

        // 至少填一项
        if (weight == null && bodyFat == null && muscle == null && waist == null) {
            Toast.makeText(this, "请至少填写一项数据", Toast.LENGTH_SHORT).show()
            return
        }

        val metric = BodyMetric(
            date = System.currentTimeMillis(),
            weight = weight,
            bodyFat = bodyFat,
            muscle = muscle,
            waist = waist
        )

        lifecycleScope.launch {
            val database = BrawnCatalogDatabase.getDatabase(this@BodyMetricsActivity)
            database.bodyMetricDao().insert(metric)
            Toast.makeText(this@BodyMetricsActivity, "已保存", Toast.LENGTH_SHORT).show()
            // 清空表单
            binding.editWeight.text?.clear()
            binding.editBodyFat.text?.clear()
            binding.editMuscle.text?.clear()
            binding.editWaist.text?.clear()
        }
    }
}
