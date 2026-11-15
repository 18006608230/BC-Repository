package com.bc.app.ui.exercise

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.bc.app.databinding.ItemCardioSubcategoryBinding

/**
 * 子分类卡片适配器
 * item：(子分类名, 简述)
 */
class CardioSubcategoryAdapter(
    private var items: List<Pair<String, String>>, // (子分类名, 简述)
    private val onItemClick: (String) -> Unit
) : RecyclerView.Adapter<CardioSubcategoryAdapter.ViewHolder>() {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val binding = ItemCardioSubcategoryBinding.inflate(
            LayoutInflater.from(parent.context), parent, false
        )
        return ViewHolder(binding)
    }

    override fun getItemCount(): Int = items.size

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.bind(items[position])
    }

    /** 数据变化（子分类集合变化）时整体刷新 */
    fun updateItems(newItems: List<Pair<String, String>>) {
        items = newItems
        notifyDataSetChanged()
    }

    inner class ViewHolder(private val binding: ItemCardioSubcategoryBinding) :
        RecyclerView.ViewHolder(binding.root) {
        init {
            binding.root.setOnClickListener {
                val pos = adapterPosition
                if (pos != RecyclerView.NO_POSITION) onItemClick(items[pos].first)
            }
        }

        fun bind(item: Pair<String, String>) {
            binding.textViewSubName.text = item.first
            binding.textViewSubDesc.text = item.second
        }
    }
}
