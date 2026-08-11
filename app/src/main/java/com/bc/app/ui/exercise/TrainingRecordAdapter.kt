package com.bc.app.ui.exercise

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.bc.app.data.entity.TrainingRecord
import com.bc.app.databinding.ItemTrainingRecordBinding
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * 训练记录列表适配器
 */
class TrainingRecordAdapter(
    // 是否为有氧：有氧记录展示总时长（分钟），不展示重量/组数/次数
    private val isCardio: Boolean = false
) : ListAdapter<TrainingRecord, TrainingRecordAdapter.ViewHolder>(DiffCallback()) {

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

        private val dateFormat = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())

        fun bind(record: TrainingRecord) {
            binding.textViewRecordDate.text = dateFormat.format(Date(record.date))
            if (isCardio) {
                // 有氧：展示总时长（分钟），隐藏组数/次数
                binding.textViewRecordWeight.text = "${record.weight.toInt()} 分钟"
                binding.textViewRecordSets.text = ""
                binding.textViewRecordReps.text = ""
            } else {
                binding.textViewRecordWeight.text = "${record.weight}kg"
                binding.textViewRecordSets.text = "${record.sets}组"
                binding.textViewRecordReps.text = "${record.avgReps}次/组"
            }

            if (record.remark.isNotEmpty()) {
                binding.textViewRecordRemark.text = record.remark
            } else {
                binding.textViewRecordRemark.text = ""
            }
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
