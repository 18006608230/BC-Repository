package com.bc.app.ui.exercise

import android.app.AlertDialog
import android.content.Intent
import android.os.Bundle
import android.view.View
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.bc.app.data.db.BrawnCatalogDatabase
import com.bc.app.data.entity.BodyPart
import com.bc.app.data.entity.Exercise
import com.bc.app.data.repository.ExerciseRepository
import com.bc.app.databinding.ActivityExerciseListBinding
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

/**
 * 动作列表页
 * 展示某个部位下的所有动作（力量部位按子分类分组；有氧则按子分类过滤展示）
 * 支持右上角新建自定义动作、长按编辑/删除动作
 */
class ExerciseListActivity : AppCompatActivity() {

    companion object {
        const val EXTRA_BODY_PART_ID = "extra_body_part_id"
        const val EXTRA_BODY_PART_NAME = "extra_body_part_name"
        const val EXTRA_SUBCATEGORY = "extra_subcategory"
    }

    private lateinit var binding: ActivityExerciseListBinding
    private lateinit var groupedAdapter: GroupedExerciseAdapter
    private lateinit var flatAdapter: ExerciseAdapter
    private lateinit var exerciseRepository: ExerciseRepository

    private var bodyPartId: Long = -1
    private var bodyPartName: String = "动作列表"
    private var subcategoryFilter: String? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityExerciseListBinding.inflate(layoutInflater)
        setContentView(binding.root)

        bodyPartId = intent.getLongExtra(EXTRA_BODY_PART_ID, -1)
        bodyPartName = intent.getStringExtra(EXTRA_BODY_PART_NAME) ?: "动作列表"
        subcategoryFilter = intent.getStringExtra(EXTRA_SUBCATEGORY)

        binding.textViewScreenTitle.text = bodyPartName

        // 有氧子分类下钻：显示当前子分类名
        if (!subcategoryFilter.isNullOrEmpty()) {
            binding.textViewSubcategoryInfo.visibility = View.VISIBLE
            binding.textViewSubcategoryInfo.text = subcategoryFilter
        }

        title = bodyPartName
        supportActionBar?.title = bodyPartName

        initRepository()
        initRecyclerView()
        initAddButton()
        observe()

        // 页面返回按钮
        binding.buttonBack.setOnClickListener { finish() }
    }

    private fun initRepository() {
        val database = BrawnCatalogDatabase.getDatabase(this)
        exerciseRepository = ExerciseRepository(database.exerciseDao())
    }

    private fun initRecyclerView() {
        // 点击 → 详情；长按 → 编辑/删除
        val click: (Exercise) -> Unit = { exercise ->
            startActivity(
                Intent(this, ExerciseDetailActivity::class.java).apply {
                    putExtra(ExerciseDetailActivity.EXTRA_EXERCISE_ID, exercise.id)
                    putExtra(ExerciseDetailActivity.EXTRA_EXERCISE_NAME, exercise.name)
                }
            )
        }
        val longClick: (Exercise) -> Unit = { exercise ->
            showEditDeleteDialog(exercise)
        }

        groupedAdapter = GroupedExerciseAdapter(click, longClick)
        flatAdapter = ExerciseAdapter(click)

        binding.recyclerViewExercises.apply {
            layoutManager = LinearLayoutManager(this@ExerciseListActivity)
            adapter = if (subcategoryFilter.isNullOrEmpty()) groupedAdapter else flatAdapter
        }
    }

    private fun initAddButton() {
        binding.buttonAddExercise.setOnClickListener {
            startActivity(
                Intent(this, ExerciseEditActivity::class.java).apply {
                    putExtra(ExerciseEditActivity.EXTRA_BODY_PART_ID, bodyPartId)
                    putExtra(ExerciseEditActivity.EXTRA_BODY_PART_NAME, bodyPartName)
                    // 携带当前子分类：从二级卡片进入时自动锁定部位+子分类，不可修改
                    subcategoryFilter?.let {
                        putExtra(ExerciseEditActivity.EXTRA_SUBCATEGORY, it)
                    }
                }
            )
        }
    }

    private fun observe() {
        lifecycleScope.launch {
            // 有氧子分类：过滤 + 平铺
            if (!subcategoryFilter.isNullOrEmpty()) {
                exerciseRepository.getByBodyPartAndSubcategory(bodyPartId, subcategoryFilter!!)
                    .collectLatest { exercises ->
                        flatAdapter.submitList(exercises)
                    }
                return@launch
            }

            // 力量部位：按子分类分组
            exerciseRepository.getByBodyPartId(bodyPartId).collectLatest { exercises ->
                val groups = groupExercises(exercises)
                groupedAdapter.submitGroups(groups)
            }
        }
    }

    private fun groupExercises(exercises: List<Exercise>): List<Pair<String, List<Exercise>>> {
        val order = if (bodyPartName == "核心") Exercise.CORE_SUBCATEGORIES
        else Exercise.STRENGTH_SUBCATEGORIES
        val bySub = exercises.groupBy { it.subcategory.ifBlank { "徒手" } }
        return order.mapNotNull { sub ->
            val list = bySub[sub]
            if (list.isNullOrEmpty()) null else sub to list
        }
    }

    private fun showEditDeleteDialog(exercise: Exercise) {
        val options = arrayOf("编辑动作", "删除动作", "取消")
        AlertDialog.Builder(this)
            .setTitle(exercise.name)
            .setItems(options) { _, which ->
                when (which) {
                    0 -> openEdit(exercise)
                    1 -> confirmDelete(exercise)
                }
            }
            .show()
    }

    private fun openEdit(exercise: Exercise) {
        startActivity(
            Intent(this, ExerciseEditActivity::class.java).apply {
                putExtra(ExerciseEditActivity.EXTRA_EXERCISE_ID, exercise.id)
                putExtra(ExerciseEditActivity.EXTRA_BODY_PART_ID, bodyPartId)
                putExtra(ExerciseEditActivity.EXTRA_BODY_PART_NAME, bodyPartName)
            }
        )
    }

    private fun confirmDelete(exercise: Exercise) {
        AlertDialog.Builder(this)
            .setTitle("删除动作")
            .setMessage("确定删除「${exercise.name}」吗？历史记录会保留，但无法再进入该动作。")
            .setPositiveButton("删除") { _, _ ->
                lifecycleScope.launch {
                    exerciseRepository.delete(exercise)
                }
            }
            .setNegativeButton("取消", null)
            .show()
    }
}
