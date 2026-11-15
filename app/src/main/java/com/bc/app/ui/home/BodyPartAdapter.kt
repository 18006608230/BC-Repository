package com.bc.app.ui.home

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.animation.ObjectAnimator
import android.view.animation.DecelerateInterpolator
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.bc.app.data.entity.BodyPart
import com.bc.app.databinding.ItemBodyPartBinding

/**
 * 部位列表适配器
 */
class BodyPartAdapter(
    private val onItemClick: (BodyPart, View) -> Unit
) : ListAdapter<BodyPart, BodyPartAdapter.ViewHolder>(DiffCallback()) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val binding = ItemBodyPartBinding.inflate(
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
        private val binding: ItemBodyPartBinding
    ) : RecyclerView.ViewHolder(binding.root) {

        init {
            // 点击：先缩小到 96% 再弹簧回弹，模拟 iOS 按键触感
            binding.root.setOnClickListener {
                val view = binding.root
                view.animate().cancel()
                view.scaleX = 0.96f
                view.scaleY = 0.96f
                view.animate()
                    .scaleX(1f)
                    .scaleY(1f)
                    .setDuration(220)
                    .setInterpolator(DecelerateInterpolator(2.2f))
                    .start()

                val position = adapterPosition
                if (position != RecyclerView.NO_POSITION) {
                    onItemClick(getItem(position), view)
                }
            }
        }

        fun bind(bodyPart: BodyPart) {
            binding.textViewBodyPartName.text = bodyPart.name
            binding.textViewBodyPartDesc.text = BodyPart.partDesc(bodyPart.name)
        }
    }

    class DiffCallback : DiffUtil.ItemCallback<BodyPart>() {
        override fun areItemsTheSame(oldItem: BodyPart, newItem: BodyPart): Boolean {
            return oldItem.id == newItem.id
        }

        override fun areContentsTheSame(oldItem: BodyPart, newItem: BodyPart): Boolean {
            return oldItem == newItem
        }
    }
}
