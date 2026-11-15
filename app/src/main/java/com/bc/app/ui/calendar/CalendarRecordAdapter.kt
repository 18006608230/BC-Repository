package com.bc.app.ui.calendar

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.bc.app.data.entity.Exercise
import com.bc.app.data.entity.TrainingRecord
import com.bc.app.databinding.ItemTrainingRecordBinding
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * 日历页动作记录适配器
 * 复用 item_training_record 布局，展示动作名称 + 时间 + 训练摘要
 */
class CalendarRecordAdapter(
    /** 长按某条记录：弹「收藏该动作 / 删除这条记录」菜单 */
    private val onRecordLongClick: (TrainingRecord) -> Unit
) : ListAdapter<TrainingRecord, CalendarRecordAdapter.ViewHolder>(DiffCallback()) {

    var exerciseMap: Map<Long, Exercise> = emptyMap()

    private companion object {
        /** 与 HomeActivity 保持一致：休息动作名 */
        const val REST_EXERCISE_NAME = "休息"
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val binding = ItemTrainingRecordBinding.inflate(
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
        private val binding: ItemTrainingRecordBinding
    ) : RecyclerView.ViewHolder(binding.root) {

        private val timeFormat = SimpleDateFormat("HH:mm", Locale.getDefault())

        init {
            // 长按弹出操作菜单（收藏该动作 / 删除这条记录）
            binding.root.setOnLongClickListener {
                val position = adapterPosition
                if (position != RecyclerView.NO_POSITION) {
                    onRecordLongClick(getItem(position))
                }
                true
            }
        }

        fun bind(record: TrainingRecord) {
            val exerciseName = exerciseMap[record.exerciseId]?.name ?: "动作"
            val time = timeFormat.format(Date(record.date))
            binding.textViewRecordDate.text = "$exerciseName · $time"

            if (exerciseName == REST_EXERCISE_NAME) {
                // 休息没有重量 / 组数 / 次数，也不该再挂一行底部小字，
                // 否则卡片上全是「0.0kg 0组 0次/组」这种无意义的深色小字
                binding.textViewRecordWeight.visibility = View.GONE
                binding.textViewRecordSets.visibility = View.GONE
                binding.textViewRecordReps.visibility = View.GONE
                binding.textViewRecordRemark.visibility = View.GONE
                return
            }

            binding.textViewRecordWeight.visibility = View.VISIBLE
            binding.textViewRecordSets.visibility = View.VISIBLE
            binding.textViewRecordReps.visibility = View.VISIBLE
            binding.textViewRecordRemark.visibility = View.VISIBLE

            binding.textViewRecordWeight.text = "${record.weight}kg"
            binding.textViewRecordSets.text = "${record.sets}组"
            binding.textViewRecordReps.text = "${record.avgReps}次/组"

            val remark = if (record.isProfessionalRecord()) "专业逐组记录" else record.remark
            binding.textViewRecordRemark.text = remark
        }
    }

    class DiffCallback : DiffUtil.ItemCallback<TrainingRecord>() {
        override fun areItemsTheSame(oldItem: TrainingRecord, newItem: TrainingRecord): Boolean {
            return oldItem.id == newItem.id
        }

        override fun areContentsTheSame(oldItem: TrainingRecord, newItem: TrainingRecord): Boolean {
            return oldItem == newItem
        }
    }
}
