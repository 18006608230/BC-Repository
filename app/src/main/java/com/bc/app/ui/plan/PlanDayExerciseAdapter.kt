package com.bc.app.ui.plan

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.bc.app.data.entity.Exercise
import com.bc.app.databinding.ItemPlanDayExerciseBinding
import com.bc.app.utils.loadExerciseImage

/**
 * 计划详情 · 某训练日的动作卡片列表适配器
 * 每张卡：动作配图 + 名称 + 组×次；点击进入动作详情
 */
class PlanDayExerciseAdapter(
    private val onExerciseClick: (Exercise) -> Unit
) : RecyclerView.Adapter<PlanDayExerciseAdapter.ViewHolder>() {

    /** 当日动作条目（保持计划内顺序） */
    private val items = mutableListOf<Pair<Exercise, Pair<Int, Int>>>()

    fun submit(items: List<Pair<Exercise, Pair<Int, Int>>>) {
        this.items.clear()
        this.items.addAll(items)
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val binding = ItemPlanDayExerciseBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return ViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        // 嵌套解构 val (a, (b, c)) 不被 Kotlin 支持，逐层取值
        val entry = items[position]
        val exercise = entry.first
        val sets = entry.second.first
        val reps = entry.second.second
        holder.bind(exercise, sets, reps)
    }

    override fun getItemCount(): Int = items.size

    inner class ViewHolder(
        private val binding: ItemPlanDayExerciseBinding
    ) : RecyclerView.ViewHolder(binding.root) {

        fun bind(exercise: Exercise, sets: Int, reps: Int) {
            binding.textViewExerciseName.text = exercise.name
            binding.textViewSetsReps.text = "$sets 组 × $reps 次"
            binding.imageViewExercise.loadExerciseImage(exercise.imagePath)
            binding.root.setOnClickListener { onExerciseClick(exercise) }
        }
    }
}