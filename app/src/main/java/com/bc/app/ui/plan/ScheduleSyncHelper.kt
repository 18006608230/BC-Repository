package com.bc.app.ui.plan

import android.app.AlertDialog
import android.content.Context
import android.text.InputType
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import com.bc.app.R
import com.bc.app.data.db.BrawnCatalogDatabase
import com.bc.app.data.entity.PlanDay
import com.bc.app.data.entity.WorkoutSchedule
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

/**
 * 计划「同步到日历」辅助：弹出配置对话框，生成 workout_schedules 排课
 *
 * 新版逻辑（v2）：
 *  - 今天是？：从周期训练日列表中选择，代表「今天开始哪个训练日」
 *  - 循环周期（天）：默认等于计划训练日天数，可自行修改
 *  - 确定后：从今天起按周期顺序每天连续排课（HORIZON_DAYS 天），
 *    超出计划天数的周期位置自动排为「休息日」
 */
object ScheduleSyncHelper {

    private val dateFormat = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())

    /** 排课前瞻天数：从今天起连续排这么多天，覆盖日历浏览范围 */
    private const val HORIZON_DAYS = 90

    fun show(
        context: Context,
        planId: Long,
        planName: String,
        days: List<PlanDay>,
        onDone: () -> Unit
    ) {
        // 按 dayIndex 升序，作为周期顺序
        val sortedDays = days.sortedBy { it.dayIndex }
        if (sortedDays.isEmpty()) {
            android.widget.Toast.makeText(
                context,
                "该计划没有训练日，请先编辑计划并生成天卡",
                android.widget.Toast.LENGTH_SHORT
            ).show()
            return
        }
        val cycleDefault = sortedDays.size

        val content = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(context, 20), dp(context, 12), dp(context, 20), dp(context, 16))
        }

        // 选项 1：今天是第几天？
        content.addView(label(context, "今天是第几天"))
        val todayPicker = TextView(context).apply {
            text = dayLabel(sortedDays[0], 1)
            tag = 0 // 选中的 days 下标（0-based）
            textSize = 15f
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(context, 14), 0, dp(context, 14), 0)
            setTextColor(primary(context))
            background = context.getDrawable(R.drawable.bg_input_rounded)
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                dp(context, 46)
            )
            setOnClickListener {
                val choices = sortedDays.mapIndexed { i, d -> dayLabel(d, i + 1) }.toTypedArray()
                // 自定义 adapter：选项文字统一深主题色
                val adapter = object : ArrayAdapter<String>(
                    context, android.R.layout.simple_list_item_1, choices
                ) {
                    override fun getView(
                        position: Int,
                        convertView: View?,
                        parent: ViewGroup
                    ): View {
                        val v = super.getView(position, convertView, parent)
                        (v as? TextView)?.setTextColor(primary(context))
                        return v
                    }
                }
                val dialog = AlertDialog.Builder(context)
                    .setTitle("选择今天开始的训练日")
                    .setAdapter(adapter) { _, which ->
                        text = choices[which]
                        tag = which
                    }
                    .create()
                dialog.show()
                // 标题统一深主题色
                dialog.findViewById<TextView>(android.R.id.title)?.setTextColor(primary(context))
            }
        }
        content.addView(todayPicker)

        // 选项 2：循环周期
        content.addView(label(context, "循环周期（天）"))
        val cycleField = numberField(context, cycleDefault.toString())
        content.addView(cycleField)

        // 提示
        content.addView(
            label(
                context,
                "确定后将按所选顺序循环排课：从今天起连续 $HORIZON_DAYS 天，每天对应一个训练日；超出周期天数的位置自动算休息日。"
            )
        )

        val scroller = ScrollView(context).apply { addView(content) }

        val dialog = AlertDialog.Builder(context)
            .setTitle("同步「$planName」到日历")
            .setView(scroller)
            .setPositiveButton("确定") { _, _ ->
                val chosenIndex = (todayPicker.tag as? Int)?.coerceIn(0, sortedDays.size - 1) ?: 0
                val cycle = cycleField.text.toString().toIntOrNull() ?: cycleDefault
                if (cycle < 1) return@setPositiveButton
                writeSchedules(context, planId, sortedDays, chosenIndex, cycle, onDone)
            }
            .setNegativeButton("取消", null)
            .create()
        dialog.show()
        // 标题与按钮文字统一深主题色
        dialog.findViewById<TextView>(android.R.id.title)?.setTextColor(primary(context))
        dialog.getButton(AlertDialog.BUTTON_POSITIVE).setTextColor(primary(context))
        dialog.getButton(AlertDialog.BUTTON_NEGATIVE).setTextColor(primary(context))
    }

    private fun dayLabel(day: PlanDay, index: Int): String {
        val name = day.name.ifEmpty { if (day.isRestDay) "休息日" else "训练" }
        return if (day.isRestDay) "第 $index 天 · 休息日" else "第 $index 天 · $name"
    }

    private fun writeSchedules(
        context: Context,
        planId: Long,
        days: List<PlanDay>,
        chosenIndex: Int,
        cycleLen: Int,
        onDone: () -> Unit
    ) {
        CoroutineScope(Dispatchers.Main).launch {
            withContext(Dispatchers.IO) {
                writeSchedulesSuspend(context, planId, days, chosenIndex, cycleLen)
            }
            onDone()
        }
    }

    /**
     * 生成排课：
     *  - 先删除该计划「今天及以后」的旧排课（保留历史），避免重复/残留
     *  - 从今天开始连续 HORIZON_DAYS 天，第 p 天对应周期位置 (chosenIndex + p) % cycleLen
     *  - 位置 < days.size → 对应具体训练日；否则为休息日
     */
    private suspend fun writeSchedulesSuspend(
        context: Context,
        planId: Long,
        days: List<PlanDay>,
        chosenIndex: Int,
        cycleLen: Int
    ) {
        val db = BrawnCatalogDatabase.getDatabase(context)
        val todayKey = dateFormat.format(Calendar.getInstance().time)

        // 清理该计划今天起的旧排课，避免与新周期冲突
        db.workoutScheduleDao().deleteByPlanBetween(planId, todayKey, "9999-12-31")

        val cal = Calendar.getInstance()
        val schedules = ArrayList<WorkoutSchedule>()
        for (p in 0 until HORIZON_DAYS) {
            val pos = (chosenIndex + p) % cycleLen
            val day = days.getOrNull(pos)
            schedules.add(
                if (day != null) {
                    WorkoutSchedule(
                        date = dateFormat.format(cal.time),
                        planId = planId,
                        dayId = day.id,
                        dayName = day.name
                    )
                } else {
                    // 周期比训练日多出的位置：休息日
                    WorkoutSchedule(
                        date = dateFormat.format(cal.time),
                        planId = planId,
                        dayId = null,
                        dayName = "休息日"
                    )
                }
            )
            cal.add(Calendar.DAY_OF_YEAR, 1)
        }
        if (schedules.isNotEmpty()) {
            db.workoutScheduleDao().insertAll(schedules)
        }
    }

    private fun numberField(context: Context, def: String): EditText =
        EditText(context).apply {
            setText(def)
            setTextColor(primary(context))
            inputType = InputType.TYPE_CLASS_NUMBER
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(context, 14), 0, dp(context, 14), 0)
            // 圆角浅底，去掉系统下划线
            background = context.getDrawable(R.drawable.bg_input_rounded)
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                dp(context, 46)
            )
        }

    private fun label(context: Context, text: String): TextView =
        TextView(context).apply {
            this.text = text
            textSize = 13f
            setTextColor(primary(context))
            setPadding(0, dp(context, 12), 0, dp(context, 4))
        }

    private fun primary(context: Context): Int =
        com.bc.app.utils.SettingsUtils.resolveAttrColor(context, com.bc.app.R.attr.bcPrimary)

    private fun dp(context: Context, value: Int): Int =
        (value * context.resources.displayMetrics.density).toInt()
}
