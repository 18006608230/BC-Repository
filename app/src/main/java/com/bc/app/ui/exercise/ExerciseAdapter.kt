package com.bc.app.ui.exercise

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.bc.app.R
import com.bc.app.data.entity.Exercise
import com.bc.app.databinding.ItemExerciseBinding
import com.bc.app.utils.loadExerciseImage

/**
 * 动作列表适配器
 *
 * 卡片右侧的小五角星（buttonFavorite）用于快速收藏/取消收藏，不会触发整卡点击；
 * 收藏状态仍由顶部分组的"★ 收藏"全局体现。
 */
class ExerciseAdapter(
    private val onItemClick: (Exercise) -> Unit,
    private val onFavoriteToggle: (Exercise) -> Unit
) : ListAdapter<Exercise, ExerciseAdapter.ViewHolder>(DiffCallback()) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val binding = ItemExerciseBinding.inflate(
            LayoutInflater.from(parent.context),
            parent,
            false
        )
        return ViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    inner class ViewHolder(
        private val binding: ItemExerciseBinding
    ) : RecyclerView.ViewHolder(binding.root) {

        init {
            binding.root.setOnClickListener {
                val position = adapterPosition
                if (position != RecyclerView.NO_POSITION) {
                    onItemClick(getItem(position))
                }
            }
            binding.buttonFavorite.setOnClickListener {
                val position = adapterPosition
                if (position != RecyclerView.NO_POSITION) {
                    onFavoriteToggle(getItem(position))
                }
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
            // 右侧小五角星：实心=已收藏，空心=未收藏
            binding.buttonFavorite.setImageResource(
                if (exercise.isFavorite) R.drawable.ic_star_filled else R.drawable.ic_star_outline
            )
        }
    }

    class DiffCallback : DiffUtil.ItemCallback<Exercise>() {
        override fun areItemsTheSame(oldItem: Exercise, newItem: Exercise): Boolean {
            return oldItem.id == newItem.id
        }

        override fun areContentsTheSame(oldItem: Exercise, newItem: Exercise): Boolean {
            return oldItem == newItem
        }
    }
}
