package com.bc.app.ui.plan

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.bc.app.data.entity.Exercise
import com.bc.app.data.entity.PlanDay
import com.bc.app.data.entity.TrainingPlanItem
import com.bc.app.databinding.ItemPlanDayBinding

/**
 * 计划详情页 · 训练日分组列表适配器
 * 每个训练日渲染为一张卡片，列出该日所有动作
 */
class PlanDayAdapter(
    private val exerciseMap: Map<Long, Exercise>
) : RecyclerView.Adapter<PlanDayAdapter.ViewHolder>() {

    // 训练日 -> 该日动作条目（保持顺序）
    private val days = mutableListOf<PlanDay>()
    private val itemsByDay = HashMap<Long, List<TrainingPlanItem>>()

    // 未分组条目（dayId == null）
    private var ungroupedItems: List<TrainingPlanItem> = emptyList()

    fun setData(dayList: List<PlanDay>, items: List<TrainingPlanItem>) {
        days.clear()
        days.addAll(dayList)
        itemsByDay.clear()
        for (d in dayList) {
            itemsByDay[d.id] = items.filter { it.dayId == d.id }
        }
        ungroupedItems = items.filter { it.dayId == null }
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val binding = ItemPlanDayBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return ViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        if (position == 0 && ungroupedItems.isNotEmpty()) {
            holder.bindUngrouped(ungroupedItems)
        } else {
            val idx = if (ungroupedItems.isNotEmpty()) position - 1 else position
            val day = days[idx]
            holder.bind(day, itemsByDay[day.id] ?: emptyList())
        }
    }

    override fun getItemCount(): Int {
        return days.size + if (ungroupedItems.isNotEmpty()) 1 else 0
    }

    inner class ViewHolder(
        private val binding: ItemPlanDayBinding
    ) : RecyclerView.ViewHolder(binding.root) {

        fun bind(day: PlanDay, items: List<TrainingPlanItem>) {
            binding.textViewDayTitle.text = day.name.ifEmpty { "第 ${day.dayIndex} 天" }
            binding.textViewDayMeta.text = "${items.size} 个动作"
            binding.textViewDayExercises.text = buildExerciseText(items)
        }

        fun bindUngrouped(items: List<TrainingPlanItem>) {
            binding.textViewDayTitle.text = "未分组"
            binding.textViewDayMeta.text = "${items.size} 个动作"
            binding.textViewDayExercises.text = buildExerciseText(items)
        }

        private fun buildExerciseText(items: List<TrainingPlanItem>): String {
            if (items.isEmpty()) return "暂无动作"
            val sb = StringBuilder()
            for (item in items) {
                val exName = exerciseMap[item.exerciseId]?.name ?: "未知动作"
                sb.append("• ").append(exName)
                    .append("  ·  ").append(item.sets).append("组 × ").append(item.reps).append("次")
                    .append("\n")
            }
            return sb.toString().trimEnd()
        }
    }
}
