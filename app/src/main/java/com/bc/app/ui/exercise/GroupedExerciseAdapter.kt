package com.bc.app.ui.exercise

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.bc.app.R
import com.bc.app.data.entity.Exercise
import com.bc.app.databinding.ItemExerciseBinding
import com.bc.app.databinding.ItemExerciseGroupHeaderBinding
import com.bc.app.utils.loadExerciseImage

/**
 * 分组动作列表适配器
 *
 * 力量部位按子分类（杠铃/哑铃/徒手/器械）分组展示。
 * 卡片右侧的小五角星用于快速收藏/取消收藏，不会触发整卡点击。
 */
class GroupedExerciseAdapter(
    private val onItemClick: (Exercise) -> Unit,
    private val onItemLongClick: (Exercise) -> Unit,
    private val onFavoriteToggle: (Exercise) -> Unit
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
            binding.buttonFavorite.setOnClickListener {
                val pos = adapterPosition
                if (pos != RecyclerView.NO_POSITION) onFavoriteToggle(items[pos] as Exercise)
            }
        }

        fun bind(exercise: Exercise) {
            binding.textViewExerciseName.text = exercise.name
            if (exercise.nameEn.isNotBlank()) {
                binding.textViewExerciseNameEn.visibility = View.VISIBLE
                binding.textViewExerciseNameEn.text = exercise.nameEn
            } else {
                binding.textViewExerciseNameEn.visibility = View.GONE
            }
            // 动作配图（数据集 GIF 或用户自定义图片；无则隐藏）
            binding.imageViewExercise.loadExerciseImage(exercise.imagePath)
            // 右侧小五角星
            binding.buttonFavorite.setImageResource(
                if (exercise.isFavorite) R.drawable.ic_star_filled else R.drawable.ic_star_outline
            )
        }
    }
}
