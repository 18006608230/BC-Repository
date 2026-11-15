package com.bc.app.ui.exercise

import com.bc.app.ui.base.BaseActivity

import android.app.AlertDialog
import android.content.Intent
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.View
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.bc.app.data.db.BrawnCatalogDatabase
import com.bc.app.data.entity.Exercise
import com.bc.app.data.repository.ExerciseRepository
import com.bc.app.databinding.ActivityExerciseListBinding
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.launch

/**
 * 动作列表页
 * 展示某个部位下的所有动作（力量部位按子分类分组；有氧则按子分类过滤展示）
 * 支持右上角新建自定义动作、长按编辑/删除动作
 */
class ExerciseListActivity : BaseActivity() {

    companion object {
        const val EXTRA_BODY_PART_ID = "extra_body_part_id"
        const val EXTRA_BODY_PART_NAME = "extra_body_part_name"
        const val EXTRA_SUBCATEGORY = "extra_subcategory"
        /** 仅显示已收藏动作（二级卡片页右上角五角星入口） */
        const val EXTRA_FAVORITES_ONLY = "extra_favorites_only"
    }

    private lateinit var binding: ActivityExerciseListBinding
    private lateinit var groupedAdapter: GroupedExerciseAdapter
    private lateinit var flatAdapter: ExerciseAdapter
    private lateinit var exerciseRepository: ExerciseRepository

    private var bodyPartId: Long = -1
    private var bodyPartName: String = "动作列表"
    private var subcategoryFilter: String? = null
    /** 仅看收藏模式：二级卡片页点五角星进入 */
    private var favoritesOnly: Boolean = false

    /** 搜索关键词（动作列表页搜索框） */
    private val searchQuery = MutableStateFlow("")

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityExerciseListBinding.inflate(layoutInflater)
        setContentView(binding.root)

        bodyPartId = intent.getLongExtra(EXTRA_BODY_PART_ID, -1)
        bodyPartName = intent.getStringExtra(EXTRA_BODY_PART_NAME) ?: "动作列表"
        subcategoryFilter = intent.getStringExtra(EXTRA_SUBCATEGORY)
        favoritesOnly = intent.getBooleanExtra(EXTRA_FAVORITES_ONLY, false)

        binding.textViewScreenTitle.text =
            if (favoritesOnly) "$bodyPartName · 收藏" else bodyPartName

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
        initSearchBox()
        observe()

        // 页面返回按钮
        binding.buttonBack.setOnClickListener { finish() }
    }

    private fun initSearchBox() {
        binding.editTextSearch.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                searchQuery.value = s?.toString()?.trim().orEmpty()
            }
            override fun afterTextChanged(s: Editable?) {}
        })
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
        // 卡片右侧小五角星 → 快速收藏切换；Flow 会自动重新发射并刷新列表（★ 收藏 分组也会跟随）
        val favoriteToggle: (Exercise) -> Unit = { exercise ->
            lifecycleScope.launch {
                exerciseRepository.setFavorite(exercise.id, !exercise.isFavorite)
            }
        }

        groupedAdapter = GroupedExerciseAdapter(click, longClick, favoriteToggle)
        flatAdapter = ExerciseAdapter(click, favoriteToggle)

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
            // 搜索框（200ms 防抖）：非空 → 搜索态；空 → 恢复分组/子分类过滤
            searchQuery.debounce(200).collectLatest { q ->
                val searching = q.isNotBlank()
                // 搜索态或子分类下钻 → 扁平列表；否则按子分类分组
                binding.recyclerViewExercises.adapter =
                    if (searching || !subcategoryFilter.isNullOrEmpty()) flatAdapter else groupedAdapter

                when {
                    searching -> {
                        val flow = if (bodyPartId > 0) exerciseRepository.searchInBodyPart(bodyPartId, q)
                        else exerciseRepository.searchAll(q)
                        flow.collectLatest { flatAdapter.submitList(filterFavorites(it)) }
                    }
                    !subcategoryFilter.isNullOrEmpty() -> {
                        exerciseRepository.getByBodyPartAndSubcategory(bodyPartId, subcategoryFilter!!)
                            .collectLatest { flatAdapter.submitList(filterFavorites(it)) }
                    }
                    else -> {
                        exerciseRepository.getByBodyPartId(bodyPartId)
                            .collectLatest { groupedAdapter.submitGroups(groupExercises(filterFavorites(it))) }
                    }
                }
            }
        }
    }

    /**
     * 仅看收藏模式下过滤出已收藏动作；否则原样返回。
     */
    private fun filterFavorites(list: List<Exercise>): List<Exercise> =
        if (favoritesOnly) list.filter { it.isFavorite } else list

    /**
     * 按数据库真实存在的子分类分组，并按全局展示顺序排序（数据驱动）。
     * 收藏的动作单独拎到顶部「★ 收藏」分组，实现“收藏置顶展示”。
     */
    private fun groupExercises(exercises: List<Exercise>): List<Pair<String, List<Exercise>>> {
        val favorites = exercises.filter { it.isFavorite }
        val rest = exercises.filterNot { it.isFavorite }
        val groups = mutableListOf<Pair<String, List<Exercise>>>()
        if (favorites.isNotEmpty()) {
            groups.add("★ 收藏" to favorites)
        }
        val bySub = rest.groupBy { it.subcategory.ifBlank { "徒手" } }
        val ordered = Exercise.sortSubcategories(bySub.keys)
        ordered.mapNotNullTo(groups) { sub ->
            val list = bySub[sub]
            if (list.isNullOrEmpty()) null else sub to list
        }
        return groups
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
