package com.bc.app.ui.stats

import android.content.res.ColorStateList
import android.graphics.Color
import android.os.Bundle
import android.view.LayoutInflater
import com.google.android.material.chip.Chip
import com.bc.app.ui.base.BaseActivity
import com.bc.app.utils.SettingsUtils
import com.bc.app.R
import androidx.lifecycle.lifecycleScope
import com.bc.app.data.db.BrawnCatalogDatabase
import com.bc.app.data.entity.Exercise
import com.bc.app.data.entity.TrainingRecord
import com.bc.app.databinding.ActivityStatsBinding
import com.bc.app.databinding.ItemMuscleCoverageBinding
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
class StatsActivity : BaseActivity() {

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

        setupRangeChips()
        wireChipClicks()

        loadStats()
    }

    /**
     * 为 3 个时间范围 chip 手动接管点击事件。
     *
     * 父样式是 `Widget.MaterialComponents.Chip.Action`（不带 onSurface 黑叠加，
     * 解决了未选中态黑色一坨问题），但 Action chip **没有 checked 状态切换能力**，
     * `setOnCheckedStateChangeListener` 不会触发。因此改为：
     * - 给每个 chip 设 `setOnClickListener`
     * - 点击时把目标 chip `isChecked = true`，其余 chip `isChecked = false`
     * - ColorStateList 包含 `state_checked` 状态，视觉自动跟随
     * - 同时直接更新 rangeDays + renderBodyPartPie()
     */
    private fun wireChipClicks() {
        val chips = listOf(binding.chipWeek, binding.chipMonth, binding.chipYear)
        // 初始选中态：layout 里 chipWeek 已经 android:checked=true，其余确保 false
        chips.forEach { it.isChecked = (it.id == binding.chipWeek.id) }

        chips.forEach { chip ->
            chip.setOnClickListener {
                if (chip.isChecked) return@setOnClickListener
                chips.forEach { it.isChecked = (it.id == chip.id) }
                rangeDays = when (chip.id) {
                    binding.chipMonth.id -> 30
                    binding.chipYear.id -> 365
                    else -> 7
                }
                renderBodyPartPie()
            }
        }
    }

    /**
     * 程序化设置 3 个时间范围 chip 的颜色。
     *
     * 之前用 `res/color/chip_state_*.xml` ColorStateList 引用 `?attr/bcXxx`，在某些
     * Material Chip 内部 drawable 链路下未能稳定覆盖父样式 `Widget.MaterialComponents.Chip.Choice`
     * 的 `colorOnSurface` 黑色叠加，仍会渲染出"黑色一坨"。这里改为在 onCreate 时用
     * [SettingsUtils.resolveAttrColor] 拿到当前主题的实色 int，再 [ColorStateList] 直接
     * 喂给 chip/chipStrokeColor/textColor/rippleColor 四项。运行时构造 ColorStateList
     * 不走 XML 解析链路，100% 覆盖父样式。
     */
    private fun setupRangeChips() {
        val primary = SettingsUtils.resolveAttrColor(this, R.attr.bcPrimary)
        val primaryPale = SettingsUtils.resolveAttrColor(this, R.attr.bcPrimaryPale)
        val border = SettingsUtils.resolveAttrColor(this, R.attr.bcBorder)
        val textPrimary = SettingsUtils.resolveAttrColor(this, R.attr.bcTextPrimary)
        val cardBg = SettingsUtils.resolveAttrColor(this, R.attr.bcCardBg)

        // 状态序列：state_checked=true / 默认
        val states = arrayOf(
            intArrayOf(android.R.attr.state_checked),
            intArrayOf()
        )
        // 选中 = 浅主题色片 / 未选 = 透明（彻底解决黑色块）
        val bgColors = intArrayOf(primaryPale, Color.TRANSPARENT)
        val strokeColors = intArrayOf(primary, border)
        val textColors = intArrayOf(primary, textPrimary)
        // Material Chip 内部用于阴影/表面色调的颜色属性已在 themes.xml
        // `Widget.BrawnCatalog.Chip.Range` 显式设为 @android:color/transparent。
        // 这里不再通过 Kotlin 二次设置（避免某些版本/编译器缓存下 unresolved 报错）。
        // 按压涟漪：浅色片透明叠加；默认透明，去掉 Material Choice 自带的黑色涟漪
        val rippleStates = arrayOf(
            intArrayOf(android.R.attr.state_pressed),
            intArrayOf()
        )
        val rippleColors = intArrayOf(primaryPale, Color.TRANSPARENT)

        // 让 chip 起始就贴近最终色，避免 inflate → onCreate 之间的瞬间黑闪
        binding.chipGroupRange.alpha = 1f
        // 强制 ChipGroup 触发一次属性解析，规避 view-binding 懒初始化导致的属性延迟
        binding.chipGroupRange.isSingleLine = true

        listOf(binding.chipWeek, binding.chipMonth, binding.chipYear).forEach { chip ->
            applyChipColors(
                chip,
                bg = ColorStateList(states, bgColors),
                stroke = ColorStateList(states, strokeColors),
                text = ColorStateList(states, textColors),
                ripple = ColorStateList(rippleStates, rippleColors),
            )
        }

        // 拦截 chip 在第一次绘制前的瞬间黑闪 —— 在测量布局完成后、第一帧 onDraw
        // 之前把所有相关属性再彻底刷一遍，确保父样式潜在叠加（backgroundTint、
        // colorOnSurface 等）覆盖不到时仍按我们指定的颜色绘制
        binding.chipGroupRange.viewTreeObserver.addOnPreDrawListener(
            object : android.view.ViewTreeObserver.OnPreDrawListener {
                override fun onPreDraw(): Boolean {
                    binding.chipGroupRange.viewTreeObserver.removeOnPreDrawListener(this)
                    listOf(binding.chipWeek, binding.chipMonth, binding.chipYear).forEach { chip ->
                        chip.setBackgroundColor(Color.TRANSPARENT)
                        chip.chipBackgroundColor = ColorStateList(states, bgColors)
                        chip.setTextColor(ColorStateList(states, textColors))
                        chip.requestLayout()
                    }
                    return true
                }
            }
        )
    }

    private fun applyChipColors(
        chip: Chip,
        bg: ColorStateList,
        stroke: ColorStateList,
        text: ColorStateList,
        ripple: ColorStateList,
    ) {
        chip.chipBackgroundColor = bg
        chip.chipStrokeColor = stroke
        chip.chipStrokeWidth = 1f * resources.displayMetrics.density
        chip.setTextColor(text)
        chip.rippleColor = ripple
        // chip 的 surface / icon tint / checked icon tint 已在 themes.xml 样式中
        // 显式设为 @android:color/transparent，Kotlin 端不再重复设置（兼容老版本 Material）。
        chip.isCheckedIconVisible = false
        chip.isCloseIconVisible = false
        chip.isChipIconVisible = false
    }

    /**
     * 返回本页时重新取数。
     * 在日历里删掉记录之后，统计页若沿用 onCreate 的旧数据就会对不上。
     */
    override fun onResume() {
        super.onResume()
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
            renderMuscleBalance()
        }
    }

    /**
     * 渲染「本周肌群覆盖」卡片。
     *
     * 固定统计**本周**，不跟随上方的时间范围 chip ——
     * 「每周每个肌群练几次」本身就是以周为周期的指标，拉长到一年就失去指导意义了。
     */
    private fun renderMuscleBalance() {
        val result = MuscleBalance.compute(allRecords, exerciseNameMap, bodyPartNameMap)

        // 各部位行：数据驱动，部位列表来自数据库
        binding.layoutMuscleRows.removeAllViews()
        val inflater = LayoutInflater.from(this)
        for (stat in result.parts) {
            val row = ItemMuscleCoverageBinding.inflate(inflater, binding.layoutMuscleRows, false)
            val color = statusColor(stat.level)
            val noTarget = stat.target <= 0

            row.textPartName.text = stat.name
            row.textPartCount.text = if (noTarget) {
                // 不做要求的部位：只报次数，不显示「/ N 次」
                "本周 ${stat.sessions} 次 · 不做要求"
            } else {
                "本周 ${stat.sessions} / ${stat.target} 次"
            }
            // 次数文字也上状态色：未练时进度条是 0，光看条读不出状态
            row.textPartCount.setTextColor(color)
            row.progressPart.progress =
                if (noTarget) 0 else progressValue(stat.sessions, stat.target)
            row.progressPart.progressTintList = ColorStateList.valueOf(color)

            binding.layoutMuscleRows.addView(row.root)
        }

        binding.textUnderTrained.text =
            if (result.underTrained.isEmpty()) {
                "本周各部位都达到了建议次数，保持住"
            } else {
                "本周还差：${result.underTrained.joinToString("、")}"
            }
        binding.textUnderTrained.setTextColor(
            if (result.underTrained.isEmpty()) statusColor(MuscleBalance.Level.OK)
            else statusColor(MuscleBalance.Level.LOW)
        )
    }

    private fun progressValue(sessions: Int, target: Int): Int =
        ((sessions.toFloat() / target.coerceAtLeast(1)) * 100).toInt().coerceIn(0, 100)

    private fun statusColor(level: MuscleBalance.Level): Int = SettingsUtils.resolveAttrColor(
        this,
        when (level) {
            MuscleBalance.Level.OK -> R.attr.bcStatusOk
            MuscleBalance.Level.LOW -> R.attr.bcStatusWarn
            MuscleBalance.Level.NONE -> R.attr.bcStatusLow
        }
    )

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
