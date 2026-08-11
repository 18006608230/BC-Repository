package com.bc.app.ui.plan

import android.annotation.SuppressLint
import android.app.AlertDialog
import android.content.Context
import android.text.InputType
import android.view.Gravity
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
 * 选项：
 *  - 训练日：全部训练日循环 / 仅选中的某个训练日
 *  - 起始日：今天起第 N 天（0=今天）
 *  - 持续天数：从起始日往后排 N 天
 *  - 间隔：每隔 N 天排一次（1=每天）
 */
object ScheduleSyncHelper {

    private val dateFormat = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())

    fun show(
        context: Context,
        planId: Long,
        planName: String,
        days: List<PlanDay>,
        onDone: () -> Unit
    ) {
        // 训练日选项（0=全部循环）
        val dayChoices = ArrayList<String>().apply {
            add("全部训练日（循环）")
            days.forEach { add(it.name) }
        }

        val content = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(context, 20), dp(context, 12), dp(context, 20), dp(context, 16))
        }

        // 训练日选择
        content.addView(label(context, "训练日"))
        val dayPicker = TextView(context).apply {
            text = dayChoices[0]
            textSize = 15f
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(context, 14), 0, dp(context, 14), 0)
            background = context.getDrawable(R.drawable.bg_input_rounded)
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                dp(context, 46)
            )
            setOnClickListener {
                AlertDialog.Builder(context)
                    .setTitle("选择训练日")
                    .setItems(dayChoices.toTypedArray()) { _, which -> text = dayChoices[which] }
                    .show()
            }
        }
        content.addView(dayPicker)

        // 起始日偏移
        content.addView(label(context, "起始日（今天起第几天，0=今天）"))
        val startOffset = numberField(context, "0")
        content.addView(startOffset)

        // 持续天数
        content.addView(label(context, "持续天数"))
        val durationDays = numberField(context, "7")
        content.addView(durationDays)

        // 间隔
        content.addView(label(context, "间隔（每 N 天一次）"))
        val intervalDays = numberField(context, "1")
        content.addView(intervalDays)

        val scroller = ScrollView(context).apply { addView(content) }

        AlertDialog.Builder(context)
            .setTitle("同步「$planName」到日历")
            .setView(scroller)
            .setPositiveButton("确定") { _, _ ->
                val start = startOffset.text.toString().toIntOrNull() ?: 0
                val duration = durationDays.text.toString().toIntOrNull() ?: 7
                val interval = intervalDays.text.toString().toIntOrNull() ?: 1
                if (duration < 1 || interval < 1) return@setPositiveButton
                val idx = dayChoices.indexOf(dayPicker.text.toString()).coerceAtLeast(0)
                writeSchedules(context, planId, days, idx, start, duration, interval, onDone)
            }
            .setNegativeButton("取消", null)
            .show()
    }

    private fun writeSchedules(
        context: Context,
        planId: Long,
        days: List<PlanDay>,
        dayChoiceIndex: Int,
        startOffset: Int,
        duration: Int,
        interval: Int,
        onDone: () -> Unit
    ) {
        CoroutineScope(Dispatchers.Main).launch {
            withContext(Dispatchers.IO) {
                writeSchedulesSuspend(context, planId, days, dayChoiceIndex, startOffset, duration, interval)
            }
            onDone()
        }
    }

    private suspend fun writeSchedulesSuspend(
        context: Context,
        planId: Long,
        days: List<PlanDay>,
        dayChoiceIndex: Int,
        startOffset: Int,
        duration: Int,
        interval: Int
    ) {
        val db = BrawnCatalogDatabase.getDatabase(context)
        val cal = Calendar.getInstance().apply { add(Calendar.DAY_OF_YEAR, startOffset) }
        val schedules = ArrayList<WorkoutSchedule>()
        var cursor = 0
        var scheduled = 0
        // cal 当前指向起始日；每排一次后按 interval 前进
        while (scheduled < duration) {
            if (dayChoiceIndex == 0) {
                // 全部训练日循环
                if (days.isEmpty()) {
                    schedules.add(
                        WorkoutSchedule(
                            date = dateFormat.format(cal.time),
                            planId = planId,
                            dayId = null,
                            dayName = null
                        )
                    )
                } else {
                    val d = days[cursor % days.size]
                    schedules.add(
                        WorkoutSchedule(
                            date = dateFormat.format(cal.time),
                            planId = planId,
                            dayId = d.id,
                            dayName = d.name
                        )
                    )
                    cursor++
                }
            } else {
                // 仅某训练日
                val day = days.getOrNull(dayChoiceIndex - 1)
                schedules.add(
                    WorkoutSchedule(
                        date = dateFormat.format(cal.time),
                        planId = planId,
                        dayId = day?.id,
                        dayName = day?.name
                    )
                )
            }
            scheduled++
            cal.add(Calendar.DAY_OF_YEAR, interval)
        }
        if (schedules.isNotEmpty()) {
            db.workoutScheduleDao().insertAll(schedules)
        }
    }

    private fun numberField(context: Context, def: String): EditText =
        EditText(context).apply {
            setText(def)
            inputType = InputType.TYPE_CLASS_NUMBER
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                dp(context, 46)
            )
        }

    private fun label(context: Context, text: String): TextView =
        TextView(context).apply {
            this.text = text
            textSize = 13f
            setTextColor(context.getColor(R.color.bc_text_secondary))
            setPadding(0, dp(context, 12), 0, dp(context, 4))
        }

    private fun dp(context: Context, value: Int): Int =
        (value * context.resources.displayMetrics.density).toInt()
}
