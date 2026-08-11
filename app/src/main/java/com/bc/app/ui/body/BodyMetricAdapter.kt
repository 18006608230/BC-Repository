package com.bc.app.ui.body

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.bc.app.data.entity.BodyMetric
import com.bc.app.databinding.ItemBodyMetricBinding
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * 身体数据列表适配器
 */
class BodyMetricAdapter : ListAdapter<BodyMetric, BodyMetricAdapter.ViewHolder>(DiffCallback()) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val binding = ItemBodyMetricBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return ViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    inner class ViewHolder(
        private val binding: ItemBodyMetricBinding
    ) : RecyclerView.ViewHolder(binding.root) {

        private val dateFormat = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())

        fun bind(metric: BodyMetric) {
            binding.textViewMetricDate.text = dateFormat.format(Date(metric.date))

            val weightText = metric.weight?.let { "体重 ${it}kg" } ?: "体重 --"
            binding.textViewMetricWeight.text = weightText

            val extras = buildList {
                metric.bodyFat?.let { add("体脂 ${it}%") }
                metric.muscle?.let { add("肌肉 ${it}kg") }
                metric.waist?.let { add("腰围 ${it}cm") }
            }
            binding.textViewMetricExtras.text = if (extras.isEmpty()) "无其他数据" else extras.joinToString(" · ")
        }
    }

    class DiffCallback : DiffUtil.ItemCallback<BodyMetric>() {
        override fun areItemsTheSame(oldItem: BodyMetric, newItem: BodyMetric): Boolean {
            return oldItem.id == newItem.id
        }

        override fun areContentsTheSame(oldItem: BodyMetric, newItem: BodyMetric): Boolean {
            return oldItem == newItem
        }
    }
}
