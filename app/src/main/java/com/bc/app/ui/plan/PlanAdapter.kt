package com.bc.app.ui.plan

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.bc.app.data.entity.TrainingPlan
import com.bc.app.databinding.ItemPlanBinding

/**
 * 训练计划列表适配器
 */
class PlanAdapter(
    private val onClick: (TrainingPlan) -> Unit,
    private val onLongClick: (TrainingPlan) -> Unit = {}
) : ListAdapter<TrainingPlan, PlanAdapter.ViewHolder>(DiffCallback()) {

    var itemCountMap: Map<Long, Int> = emptyMap()
        set(value) {
            field = value
            notifyDataSetChanged()
        }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val binding = ItemPlanBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return ViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    inner class ViewHolder(
        private val binding: ItemPlanBinding
    ) : RecyclerView.ViewHolder(binding.root) {

        fun bind(plan: TrainingPlan) {
            binding.textViewPlanName.text = plan.name
            binding.textViewPlanDescription.text = plan.description.ifEmpty { "暂无说明" }
            binding.textViewActiveBadge.visibility =
                if (plan.isActive) android.view.View.VISIBLE else android.view.View.GONE
            binding.textViewPlanItemCount.text = "${itemCountMap[plan.id] ?: 0} 个动作"
            binding.root.setOnClickListener { onClick(plan) }
            binding.root.setOnLongClickListener {
                onLongClick(plan)
                true
            }
        }
    }

    class DiffCallback : DiffUtil.ItemCallback<TrainingPlan>() {
        override fun areItemsTheSame(oldItem: TrainingPlan, newItem: TrainingPlan): Boolean {
            return oldItem.id == newItem.id
        }

        override fun areContentsTheSame(oldItem: TrainingPlan, newItem: TrainingPlan): Boolean {
            return oldItem == newItem
        }
    }
}
