package com.bc.app.ui.exercise

import com.bc.app.ui.base.BaseActivity

import android.content.Intent
import android.os.Bundle
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.GridLayoutManager
import com.bc.app.data.db.BrawnCatalogDatabase
import com.bc.app.data.entity.Exercise
import com.bc.app.data.repository.ExerciseRepository
import com.bc.app.databinding.ActivityCardioSubcategoryBinding
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

/**
 * 部位子分类页（二级卡片）
 * 点击任一训练部位（胸/肩/背/手臂/腿/核心/有氧）后进入，
 * 以卡片网格展示该部位下「数据库真实存在」的子分类（仅显示非空子分类，数据驱动），
 * 点选子分类卡后进动作列表。
 */
class BodyPartSubcategoryActivity : BaseActivity() {

    companion object {
        const val EXTRA_BODY_PART_ID = "extra_body_part_id"
        const val EXTRA_BODY_PART_NAME = "extra_body_part_name"
    }

    private lateinit var binding: ActivityCardioSubcategoryBinding
    private lateinit var exerciseRepository: ExerciseRepository

    private var bodyPartId: Long = -1
    private var bodyPartName: String = "训练"

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityCardioSubcategoryBinding.inflate(layoutInflater)
        setContentView(binding.root)

        bodyPartId = intent.getLongExtra(EXTRA_BODY_PART_ID, -1)
        bodyPartName = intent.getStringExtra(EXTRA_BODY_PART_NAME) ?: "训练"

        binding.textViewScreenTitle.text = bodyPartName
        binding.textViewSubtitle.text = buildSubtitle()

        // 页面返回按钮
        binding.buttonBack.setOnClickListener { finish() }

        // 右上角五角星：查看该部位已收藏的动作
        binding.buttonShowFavorites.setOnClickListener { openFavorites() }

        exerciseRepository = ExerciseRepository(BrawnCatalogDatabase.getDatabase(this).exerciseDao())

        // 默认占位，待数据就绪后刷新
        setupRecyclerView(emptyList())
        observeSubcategories()
    }

    private fun setupRecyclerView(items: List<Pair<String, String>>) {
        binding.recyclerViewCardioSubs.apply {
            layoutManager = GridLayoutManager(this@BodyPartSubcategoryActivity, 2)
            adapter = CardioSubcategoryAdapter(items) { subName -> openExerciseList(subName) }
        }
    }

    /**
     * 数据驱动：只读数据库里该部位真实存在的子分类（按全局展示顺序排序，隐藏空子分类）。
     */
    private fun observeSubcategories() {
        lifecycleScope.launch {
            exerciseRepository.countBySubcategory(bodyPartId).collectLatest { counts ->
                val dbSubs = counts.map { it.groupKey }.filter { it.isNotBlank() }.toSet()

                // 兜底：极端情况下数据库未就绪，仍展示全量候选
                val finalSubs = if (dbSubs.isEmpty()) {
                    Exercise.subcategoriesForBodyPart(bodyPartName).toSet()
                } else {
                    dbSubs
                }

                val ordered = Exercise.sortSubcategories(finalSubs)
                val items = ordered.map { sub ->
                    sub to Exercise.subcategoryDescForBodyPart(bodyPartName, sub)
                }

                val adapter = binding.recyclerViewCardioSubs.adapter as? CardioSubcategoryAdapter
                if (adapter != null) adapter.updateItems(items) else setupRecyclerView(items)
            }
        }
    }

    private fun openExerciseList(subName: String) {
        val intent = Intent(this, ExerciseListActivity::class.java).apply {
            putExtra(ExerciseListActivity.EXTRA_BODY_PART_ID, bodyPartId)
            putExtra(ExerciseListActivity.EXTRA_BODY_PART_NAME, subName)
            putExtra(ExerciseListActivity.EXTRA_SUBCATEGORY, subName)
        }
        startActivity(intent)
    }

    /**
     * 打开该部位的收藏动作列表（复用动作列表页，仅显示已收藏）
     */
    private fun openFavorites() {
        val intent = Intent(this, ExerciseListActivity::class.java).apply {
            putExtra(ExerciseListActivity.EXTRA_BODY_PART_ID, bodyPartId)
            putExtra(ExerciseListActivity.EXTRA_BODY_PART_NAME, bodyPartName)
            putExtra(ExerciseListActivity.EXTRA_FAVORITES_ONLY, true)
        }
        startActivity(intent)
    }

    private fun buildSubtitle(): String = when (bodyPartName) {
        "有氧" -> "选择有氧训练类型"
        "核心" -> "选择核心训练方式"
        else -> "选择训练器械类型"
    }
}
