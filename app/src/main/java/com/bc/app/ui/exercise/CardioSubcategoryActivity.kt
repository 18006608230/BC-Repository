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
 * 有氧子分类页（数据驱动）
 * 展示该部位下数据库真实存在的有氧子分类，点选后进入对应动作列表。
 * 注意：当前首页统一走 BodyPartSubcategoryActivity，本页作为有氧直达入口保留。
 */
class CardioSubcategoryActivity : BaseActivity() {

    companion object {
        const val EXTRA_BODY_PART_ID = "extra_body_part_id"
        const val EXTRA_BODY_PART_NAME = "extra_body_part_name"
    }

    private lateinit var binding: ActivityCardioSubcategoryBinding
    private lateinit var exerciseRepository: ExerciseRepository

    private var bodyPartId: Long = -1
    private var bodyPartName: String = "有氧"

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityCardioSubcategoryBinding.inflate(layoutInflater)
        setContentView(binding.root)

        bodyPartId = intent.getLongExtra(EXTRA_BODY_PART_ID, -1)
        bodyPartName = intent.getStringExtra(EXTRA_BODY_PART_NAME) ?: "有氧"

        binding.textViewScreenTitle.text = bodyPartName
        binding.textViewSubtitle.text = "选择有氧训练类型"

        // 页面返回按钮
        binding.buttonBack.setOnClickListener { finish() }

        // 右上角五角星：查看该部位已收藏的动作
        binding.buttonShowFavorites.setOnClickListener {
            startActivity(
                Intent(this, ExerciseListActivity::class.java).apply {
                    putExtra(ExerciseListActivity.EXTRA_BODY_PART_ID, bodyPartId)
                    putExtra(ExerciseListActivity.EXTRA_BODY_PART_NAME, bodyPartName)
                    putExtra(ExerciseListActivity.EXTRA_FAVORITES_ONLY, true)
                }
            )
        }

        exerciseRepository = ExerciseRepository(BrawnCatalogDatabase.getDatabase(this).exerciseDao())

        setupRecyclerView(emptyList())
        observeSubcategories()
    }

    private fun setupRecyclerView(items: List<Pair<String, String>>) {
        binding.recyclerViewCardioSubs.apply {
            layoutManager = GridLayoutManager(this@CardioSubcategoryActivity, 2)
            adapter = CardioSubcategoryAdapter(items) { subName -> openExerciseList(subName) }
        }
    }

    private fun observeSubcategories() {
        lifecycleScope.launch {
            // 数据库无对应子分类时（bodyPartId 无效或数据未就绪）回退到全量子分类
            exerciseRepository.countBySubcategory(bodyPartId).collectLatest { counts ->
                val dbSubs = counts.map { it.groupKey }.filter { it.isNotBlank() }.toSet()
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
}
