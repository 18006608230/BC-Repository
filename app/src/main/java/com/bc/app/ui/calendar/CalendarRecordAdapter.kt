package com.bc.app.ui.calendar

import android.view.LayoutInflater
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
class CalendarRecordAdapter : ListAdapter<TrainingRecord, CalendarRecordAdapter.ViewHolder>(DiffCallback()) {

    var exerciseMap: Map<Long, Exercise> = emptyMap()

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

        fun bind(record: TrainingRecord) {
            val exerciseName = exerciseMap[record.exerciseId]?.name ?: "动作"
            val time = timeFormat.format(Date(record.date))
            binding.textViewRecordDate.text = "$exerciseName · $time"
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
