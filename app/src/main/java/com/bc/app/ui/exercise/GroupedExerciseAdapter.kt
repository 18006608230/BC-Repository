package com.bc.app.ui.exercise

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.bc.app.data.entity.Exercise
import com.bc.app.databinding.ItemExerciseBinding
import com.bc.app.databinding.ItemExerciseGroupHeaderBinding

/**
 * 分组动作列表适配器
 * 力量部位按子分类（杠铃/哑铃/徒手/器械）分组展示
 */
class GroupedExerciseAdapter(
    private val onItemClick: (Exercise) -> Unit,
    private val onItemLongClick: (Exercise) -> Unit
) : RecyclerView.Adapter<RecyclerView.ViewHolder>() {

    private val items = mutableListOf<Any>() // Header(String) 或 Exercise

    companion object {
        private const val TYPE_HEADER = 0
        private const val TYPE_EXERCISE = 1
    }

    fun submitGroups(groups: List<Pair<String, List<Exercise>>>) {
        items.clear()
        for ((header, list) in groups) {
            if (list.isEmpty()) continue
            items.add(header)
            items.addAll(list)
        }
        notifyDataSetChanged()
    }

    override fun getItemViewType(position: Int): Int =
        if (items[position] is String) TYPE_HEADER else TYPE_EXERCISE

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        val inflater = LayoutInflater.from(parent.context)
        return if (viewType == TYPE_HEADER) {
            HeaderHolder(ItemExerciseGroupHeaderBinding.inflate(inflater, parent, false))
        } else {
            ExerciseHolder(ItemExerciseBinding.inflate(inflater, parent, false))
        }
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        when (val item = items[position]) {
            is String -> (holder as HeaderHolder).bind(item)
            is Exercise -> (holder as ExerciseHolder).bind(item)
        }
    }

    override fun getItemCount(): Int = items.size

    inner class HeaderHolder(private val binding: ItemExerciseGroupHeaderBinding) :
        RecyclerView.ViewHolder(binding.root) {
        fun bind(name: String) {
            binding.textViewGroupHeader.text = name
        }
    }

    inner class ExerciseHolder(private val binding: ItemExerciseBinding) :
        RecyclerView.ViewHolder(binding.root) {
        init {
            binding.root.setOnClickListener {
                val pos = adapterPosition
                if (pos != RecyclerView.NO_POSITION) onItemClick(items[pos] as Exercise)
            }
            binding.root.setOnLongClickListener {
                val pos = adapterPosition
                if (pos != RecyclerView.NO_POSITION) onItemLongClick(items[pos] as Exercise)
                true
            }
        }

        fun bind(exercise: Exercise) {
            binding.textViewExerciseName.text = exercise.name
            binding.textViewExerciseTips.text = exercise.tips
        }
    }
}
