package com.bc.app.ui.exercise

import com.bc.app.ui.base.BaseActivity

import android.content.Intent
import android.content.res.ColorStateList
import android.graphics.Color
import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.lifecycle.lifecycleScope
import com.bc.app.R
import com.bc.app.data.db.BrawnCatalogDatabase
import com.bc.app.data.entity.TrainingRecord
import com.bc.app.data.repository.ExerciseRepository
import com.bc.app.data.repository.TrainingRecordRepository
import com.bc.app.databinding.ActivityExerciseDetailBinding
import com.bc.app.ui.record.AddRecordActivity
import com.bc.app.ui.stats.LineChartView
import com.bc.app.ui.timer.TimerActivity
import com.bc.app.utils.OneRmCalculator
import com.bc.app.utils.SettingsUtils
import com.bc.app.utils.loadExerciseImage
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * 动作详情页
 * 展示动作基本信息和历史训练记录
 */
class ExerciseDetailActivity : BaseActivity() {

    companion object {
        const val EXTRA_EXERCISE_ID = "extra_exercise_id"
        const val EXTRA_EXERCISE_NAME = "extra_exercise_name"
    }

    private lateinit var binding: ActivityExerciseDetailBinding
    private lateinit var trainingRecordAdapter: TrainingRecordAdapter
    private lateinit var exerciseRepository: ExerciseRepository
    private lateinit var trainingRecordRepository: TrainingRecordRepository

    private var exerciseId: Long = -1
    private var exerciseName: String = ""
    // 是否为有氧动作：有氧展示总时长，不做 PR/重量
    private var isCardio: Boolean = false
    // 当前收藏状态（详情页收藏按钮用）
    private var currentFavorite: Boolean = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityExerciseDetailBinding.inflate(layoutInflater)
        setContentView(binding.root)

        exerciseId = intent.getLongExtra(EXTRA_EXERCISE_ID, -1)
        exerciseName = intent.getStringExtra(EXTRA_EXERCISE_NAME) ?: "动作详情"

        title = exerciseName
        supportActionBar?.title = exerciseName

        // 页面返回按钮
        binding.buttonBack.setOnClickListener { finish() }

        initRepository()
        initRecyclerView()
        loadExerciseDetails()
        loadPrData()
        observeTrainingRecords()
        setupListeners()
        setupTrendChips()
    }

    private fun initRepository() {
        val database = BrawnCatalogDatabase.getDatabase(this)
        exerciseRepository = ExerciseRepository(database.exerciseDao())
        trainingRecordRepository = TrainingRecordRepository(database.trainingRecordDao())
    }

    private fun initRecyclerView() {
        trainingRecordAdapter = TrainingRecordAdapter()

        binding.recyclerViewRecords.apply {
            layoutManager = androidx.recyclerview.widget.LinearLayoutManager(this@ExerciseDetailActivity)
            adapter = trainingRecordAdapter
        }
    }

    private fun loadExerciseDetails() {
        lifecycleScope.launch {
            val exercise = exerciseRepository.getById(exerciseId)
            exercise?.let {
                binding.textViewExerciseName.text = it.name
                loadedExercise = it
                currentFavorite = it.isFavorite
                bindExerciseImage(it)
                bindDatasetInfo(it)
                updateFavoriteButton()
                // 识别有氧并重建记录列表为有氧展示形态
                isCardio = it.bodyPartId == com.bc.app.data.entity.Exercise.CARDIO_BODY_PART_SORT
                if (isCardio) {
                    binding.recyclerViewRecords.adapter =
                        TrainingRecordAdapter(isCardio = true)
                    trainingRecordAdapter = binding.recyclerViewRecords.adapter as TrainingRecordAdapter
                }
                // 有氧：更新 PR 卡为累计时长展示
                loadPrData()
                // isCardio 刚确定，趋势卡要跟着显隐重算
                renderTrend()
            }
        }
    }

    /**
     * 填充数据集扩展字段（英文名 / 目标肌群 / 次要肌群 / 分步要领）。
     * 每个字段为空时隐藏对应行（GONE），避免空白占位造成的“大量空格”；
     * 分步要领优先用数据集 steps，缺失时回退到用户 tips，二者皆空才隐藏整卡。
     */
    private fun bindDatasetInfo(exercise: com.bc.app.data.entity.Exercise) {
        // 英文名
        if (exercise.nameEn.isNotBlank()) {
            binding.textViewNameEn.visibility = View.VISIBLE
            binding.textViewNameEn.text = "英文名：${exercise.nameEn}"
        } else {
            binding.textViewNameEn.visibility = View.GONE
        }
        // 目标肌群
        if (exercise.target.isNotBlank()) {
            binding.textViewTarget.visibility = View.VISIBLE
            binding.textViewTarget.text = "目标肌群：${exercise.target}"
        } else {
            binding.textViewTarget.visibility = View.GONE
        }
        // 次要肌群
        if (exercise.secondaryMuscles.isNotBlank()) {
            binding.textViewSecondary.visibility = View.VISIBLE
            binding.textViewSecondary.text = "次要肌群：${exercise.secondaryMuscles}"
        } else {
            binding.textViewSecondary.visibility = View.GONE
        }
        // 分步要领（steps 优先，回退 tips）
        val stepsText = when {
            exercise.steps.isNotBlank() ->
                exercise.stepList().mapIndexed { i, s -> "${i + 1}. $s" }.joinToString("\n")
            exercise.tips.isNotBlank() -> exercise.tips
            else -> ""
        }
        if (stepsText.isNotBlank()) {
            binding.textViewSteps.visibility = View.VISIBLE
            binding.textViewSteps.text = "分步要领：\n$stepsText"
        } else {
            binding.textViewSteps.visibility = View.GONE
        }

        val hasInfo = exercise.nameEn.isNotBlank() || exercise.target.isNotBlank() ||
                exercise.secondaryMuscles.isNotBlank() || stepsText.isNotBlank()
        binding.cardExerciseInfo.visibility = if (hasInfo) View.VISIBLE else View.GONE
    }

    private var loadedExercise: com.bc.app.data.entity.Exercise? = null

    // ===== 进步趋势 =====
    /** 趋势指标 */
    private enum class TrendMetric { ONE_RM, MAX_WEIGHT, VOLUME }

    private var trendMetric: TrendMetric = TrendMetric.ONE_RM
    private var trendRecords: List<TrainingRecord> = emptyList()
    private val trendDayFmt = SimpleDateFormat("MM-dd", Locale.getDefault())

    /**
     * 根据 currentFavorite 刷新标题旁的收藏小五角星（仅图标，无文字）。
     */
    private fun updateFavoriteButton() {
        binding.buttonFavorite.setImageResource(
            if (currentFavorite) R.drawable.ic_star_filled else R.drawable.ic_star_outline
        )
        binding.buttonFavorite.contentDescription =
            if (currentFavorite) "已收藏" else "收藏"
    }

    /**
     * 加载动作配图（GIF，assets/exercise_images/）。无配图时隐藏 ImageView。
     */
    private fun bindExerciseImage(exercise: com.bc.app.data.entity.Exercise) {
        binding.imageViewExercise.loadExerciseImage(exercise.imagePath)
    }

    private fun loadPrData() {
        lifecycleScope.launch {
            if (isCardio) {
                // 有氧：展示累计总时长（分钟），隐藏 1RM
                val all = trainingRecordRepository.getByExerciseIdSync(exerciseId)
                val totalMin = all.sumOf { it.weight.toInt() }
                binding.textPrWeightValue.text = "$totalMin"
                binding.textPrOneRmValue.text = "分钟"
                return@launch
            }
            val maxWeight = trainingRecordRepository.getMaxWeightByExerciseId(exerciseId)
            val maxOneRm = trainingRecordRepository.getMaxOneRmByExerciseId(exerciseId)
            binding.textPrWeightValue.text = if (maxWeight != null) "${maxWeight} kg" else "-"
            binding.textPrOneRmValue.text = if (maxOneRm != null) OneRmCalculator.format(maxOneRm) else "-"
        }
    }

    /**
     * 从添加记录页返回后刷新 PR 卡片与历史记录
     */
    override fun onResume() {
        super.onResume()
        loadPrData()
    }

    private fun observeTrainingRecords() {
        lifecycleScope.launch {
            trainingRecordRepository.getByExerciseId(exerciseId).collectLatest { records ->
                trainingRecordAdapter.submitList(records)
                trendRecords = records
                renderTrend()
            }
        }
    }

    // ------------------------------------------------------------------
    // 进步趋势
    // ------------------------------------------------------------------

    /**
     * 趋势指标 chip。
     * 与统计页同样的处理：`Widget.BrawnCatalog.Chip.Range` 的父样式是 Chip.Action，
     * **没有 checked 状态自动切换**，所以手动接管点击；颜色也用运行时构造的
     * ColorStateList 直接设置，绕开父样式里 colorOnSurface 的黑色叠加。
     */
    private fun setupTrendChips() {
        val primary = SettingsUtils.resolveAttrColor(this, R.attr.bcPrimary)
        val primaryPale = SettingsUtils.resolveAttrColor(this, R.attr.bcPrimaryPale)
        val border = SettingsUtils.resolveAttrColor(this, R.attr.bcBorder)
        val textPrimary = SettingsUtils.resolveAttrColor(this, R.attr.bcTextPrimary)

        val states = arrayOf(intArrayOf(android.R.attr.state_checked), intArrayOf())
        val chips = listOf(binding.chipMetricOneRm, binding.chipMetricWeight, binding.chipMetricVolume)
        chips.forEach { chip ->
            chip.chipBackgroundColor = ColorStateList(states, intArrayOf(primaryPale, Color.TRANSPARENT))
            chip.chipStrokeColor = ColorStateList(states, intArrayOf(primary, border))
            chip.chipStrokeWidth = 1f * resources.displayMetrics.density
            chip.setTextColor(ColorStateList(states, intArrayOf(primary, textPrimary)))
            chip.rippleColor = ColorStateList(
                arrayOf(intArrayOf(android.R.attr.state_pressed), intArrayOf()),
                intArrayOf(primaryPale, Color.TRANSPARENT)
            )
            chip.isCheckedIconVisible = false
        }

        chips.forEachIndexed { index, chip ->
            chip.isChecked = (index == 0)
            chip.setOnClickListener {
                if (chip.isChecked) return@setOnClickListener
                chips.forEach { it.isChecked = false }
                chip.isChecked = true
                trendMetric = when (chip.id) {
                    binding.chipMetricWeight.id -> TrendMetric.MAX_WEIGHT
                    binding.chipMetricVolume.id -> TrendMetric.VOLUME
                    else -> TrendMetric.ONE_RM
                }
                renderTrend()
            }
        }
    }

    /**
     * 渲染趋势折线。
     *
     * 口径说明：同一天的多条记录会**先按天合并**再取点 ——
     * 1RM / 最大重量 取当天最好一次，总容量 取当天之和，
     * 这样一天练两次也不会在图上出现两个同名 x 轴刻度。
     */
    private fun renderTrend() {
        // 有氧动作只有时长，画重量类趋势没有意义
        if (isCardio || trendRecords.isEmpty()) {
            binding.cardTrend.visibility = View.GONE
            return
        }

        val byDay = LinkedHashMap<String, MutableList<TrainingRecord>>()
        trendRecords.sortedBy { it.date }.forEach { r ->
            byDay.getOrPut(trendDayFmt.format(Date(r.date))) { mutableListOf() }.add(r)
        }

        // 只有一个数据点画不出折线，意义不大，直接隐藏整卡
        if (byDay.size < 2) {
            binding.cardTrend.visibility = View.GONE
            return
        }
        binding.cardTrend.visibility = View.VISIBLE

        val points = byDay.map { (day, list) ->
            val value = when (trendMetric) {
                TrendMetric.ONE_RM -> list.maxOf { OneRmCalculator.estimate(it.weight, it.avgReps) }
                TrendMetric.MAX_WEIGHT -> list.maxOf { it.weight }
                TrendMetric.VOLUME ->
                    list.sumOf { (it.weight * it.sets * it.avgReps).toDouble() }.toFloat()
            }
            LineChartView.Point(day, value)
        }
        binding.lineChartTrend.setData(points)

        val first = points.first().value
        val last = points.last().value
        val best = points.maxOf { it.value }
        val unit = if (trendMetric == TrendMetric.VOLUME) "kg·次" else "kg"
        val delta = last - first
        val pct = if (first > 0f) delta / first * 100f else 0f

        val deltaText = when {
            delta > 0.05f -> "相比首次 +${OneRmCalculator.format(delta)} $unit（+${pct.toInt()}%）"
            delta < -0.05f -> "相比首次 ${OneRmCalculator.format(delta)} $unit（${pct.toInt()}%）"
            else -> "相比首次基本持平"
        }
        binding.textTrendSummary.text =
            "$deltaText\n峰值 ${OneRmCalculator.format(best)} $unit · 共 ${points.size} 次训练"
    }

    private fun setupListeners() {
        // 收藏切换
        binding.buttonFavorite.setOnClickListener {
            if (exerciseId <= 0) return@setOnClickListener
            lifecycleScope.launch {
                currentFavorite = !currentFavorite
                exerciseRepository.setFavorite(exerciseId, currentFavorite)
                updateFavoriteButton()
            }
        }

        // 添加记录
        binding.fabAddRecord.setOnClickListener {
            val intent = Intent(this, AddRecordActivity::class.java).apply {
                putExtra(AddRecordActivity.EXTRA_EXERCISE_ID, exerciseId)
                putExtra(AddRecordActivity.EXTRA_EXERCISE_NAME, exerciseName)
            }
            startActivity(intent)
        }

        // 组间计时器
        binding.buttonTimer.setOnClickListener {
            val intent = Intent(this, TimerActivity::class.java).apply {
                putExtra(TimerActivity.EXTRA_DEFAULT_SECONDS, 90)
            }
            startActivity(intent)
        }

        // 编辑动作
        binding.buttonEditExercise.setOnClickListener {
            val intent = Intent(this, ExerciseEditActivity::class.java).apply {
                putExtra(ExerciseEditActivity.EXTRA_EXERCISE_ID, exerciseId)
                loadedExercise?.let { putExtra(ExerciseEditActivity.EXTRA_BODY_PART_ID, it.bodyPartId) }
            }
            startActivity(intent)
        }

        // 删除确认
        binding.buttonDeleteExercise.setOnClickListener {
            showDeleteConfirmDialog()
        }
    }

    private fun showDeleteConfirmDialog() {
        AlertDialog.Builder(this)
            .setTitle("删除动作")
            .setMessage("确定要删除这个动作吗？相关的训练记录也会被删除。")
            .setPositiveButton("删除") { _, _ ->
                lifecycleScope.launch {
                    val exercise = exerciseRepository.getById(exerciseId)
                    exercise?.let {
                        exerciseRepository.delete(it)
                        Toast.makeText(this@ExerciseDetailActivity, "已删除", Toast.LENGTH_SHORT).show()
                        finish()
                    }
                }
            }
            .setNegativeButton("取消", null)
            .show()
    }
}
