package com.bc.app.ui.exercise

import android.os.Bundle
import android.view.View
import android.widget.ArrayAdapter
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.bc.app.data.db.BrawnCatalogDatabase
import com.bc.app.data.entity.BodyPart
import com.bc.app.data.entity.Exercise
import com.bc.app.data.repository.BodyPartRepository
import com.bc.app.data.repository.ExerciseRepository
import com.bc.app.databinding.ActivityExerciseEditBinding
import com.google.android.material.chip.Chip
import kotlinx.coroutines.launch

/**
 * 新建/编辑动作页
 * - 新建自定义动作：可改部位 + 子分类
 * - 编辑已有动作（预存/自定义）：可改名称、要领、子分类
 */
class ExerciseEditActivity : AppCompatActivity() {

    companion object {
        const val EXTRA_EXERCISE_ID = "extra_exercise_id"
        const val EXTRA_BODY_PART_ID = "extra_body_part_id"
        const val EXTRA_BODY_PART_NAME = "extra_body_part_name"
        const val EXTRA_SUBCATEGORY = "extra_subcategory"
    }

    private lateinit var binding: ActivityExerciseEditBinding
    private lateinit var exerciseRepository: ExerciseRepository
    private lateinit var bodyPartRepository: BodyPartRepository

    private var bodyParts: List<BodyPart> = emptyList()
    private var currentSubcategories: List<String> = emptyList()

    // 当前编辑的动作（null = 新建）
    private var editingExercise: Exercise? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityExerciseEditBinding.inflate(layoutInflater)
        setContentView(binding.root)

        val database = BrawnCatalogDatabase.getDatabase(this)
        exerciseRepository = ExerciseRepository(database.exerciseDao())
        bodyPartRepository = BodyPartRepository(database.bodyPartDao())

        val exerciseId = intent.getLongExtra(EXTRA_EXERCISE_ID, -1)
        val presetBodyPartId = intent.getLongExtra(EXTRA_BODY_PART_ID, -1)
        val lockSubcategory = intent.getStringExtra(EXTRA_SUBCATEGORY)

        binding.textViewScreenTitle.text = if (exerciseId > 0) "编辑动作" else "新建动作"
        binding.buttonSaveExercise.text = if (exerciseId > 0) "保存修改" else "保存动作"

        // 页面返回按钮
        binding.buttonBack.setOnClickListener { finish() }

        // 新建动作时可选部位；有氧子分类下钻时锁定子分类
        loadData(exerciseId, presetBodyPartId, lockSubcategory)
    }

    private fun loadData(exerciseId: Long, presetBodyPartId: Long, lockSubcategory: String?) {
        lifecycleScope.launch {
            bodyParts = bodyPartRepository.getAllSync()

            // 目标部位：编辑时用动作原部位，否则用传入部位（或默认第一个）
            var targetBodyPartId = presetBodyPartId
            if (exerciseId > 0) {
                editingExercise = exerciseRepository.getById(exerciseId)
                targetBodyPartId = editingExercise?.bodyPartId ?: presetBodyPartId
            }

            setupBodyPartSpinner(targetBodyPartId)

            // 有氧下钻：锁定子分类
            if (!lockSubcategory.isNullOrEmpty()) {
                binding.spinnerBodyPart.isEnabled = false
                binding.textViewSubcategoryLabel.text = "子分类（锁定）"
                currentSubcategories = listOf(lockSubcategory)
                binding.chipGroupSubcategory.removeAllViews()
                currentSubcategories.forEach { addChip(it, true) }
            }

            // 预填编辑数据
            editingExercise?.let { ex ->
                binding.editExerciseName.setText(ex.name)
                binding.editExerciseTips.setText(ex.tips)
                if (lockSubcategory.isNullOrEmpty()) {
                    refreshSubcategoryChips(ex.subcategory)
                }
            }
        }
    }

    private fun setupBodyPartSpinner(selectedId: Long) {
        val names = bodyParts.map { it.name }
        binding.spinnerBodyPart.adapter = ArrayAdapter(
            this, android.R.layout.simple_spinner_item, names
        ).apply {
            // 下拉列表使用自定义美化的 item 样式
            setDropDownViewResource(com.bc.app.R.layout.item_spinner_dropdown)
        }
        val idx = bodyParts.indexOfFirst { it.id == selectedId }.coerceAtLeast(0)
        binding.spinnerBodyPart.setSelection(idx)

        // 编辑内置动作时锁定部位
        val lockedPart = editingExercise?.let { !it.isCustom && it.id > 0 } ?: false
        binding.spinnerBodyPart.isEnabled = !lockedPart

        binding.spinnerBodyPart.onItemSelectedListener =
            object : android.widget.AdapterView.OnItemSelectedListener {
                override fun onNothingSelected(parent: android.widget.AdapterView<*>?) {}
                override fun onItemSelected(
                    parent: android.widget.AdapterView<*>?,
                    view: View?,
                    position: Int,
                    id: Long
                ) {
                    refreshSubcategoryChips(null)
                }
            }
    }

    private fun refreshSubcategoryChips(preselect: String?) {
        val selectedSub = selectedSubcategory()
        binding.chipGroupSubcategory.removeAllViews()

        val selectedPart = bodyParts.getOrNull(binding.spinnerBodyPart.selectedItemPosition)
        currentSubcategories = when (selectedPart?.name) {
            "核心" -> Exercise.CORE_SUBCATEGORIES
            "有氧" -> Exercise.CARDIO_SUBCATEGORIES
            else -> Exercise.STRENGTH_SUBCATEGORIES
        }
        val pre = preselect ?: selectedSub
        currentSubcategories.forEach { addChip(it, it == pre) }
    }

    private fun addChip(text: String, checked: Boolean) {
        val chip = Chip(this).apply {
            this.text = text
            isCheckable = true
            isChecked = checked
        }
        binding.chipGroupSubcategory.addView(chip)
    }

    private fun selectedSubcategory(): String {
        for (i in 0 until binding.chipGroupSubcategory.childCount) {
            val c = binding.chipGroupSubcategory.getChildAt(i) as? Chip ?: continue
            if (c.isChecked) return c.text.toString()
        }
        return ""
    }

    private fun onSave() {
        val name = binding.editExerciseName.text?.toString()?.trim().orEmpty()
        if (name.isEmpty()) {
            Toast.makeText(this, "请输入动作名称", Toast.LENGTH_SHORT).show()
            return
        }
        val tips = binding.editExerciseTips.text?.toString()?.trim().orEmpty()
        val sub = selectedSubcategory().ifEmpty { currentSubcategories.firstOrNull().orEmpty() }
        val part = bodyParts.getOrNull(binding.spinnerBodyPart.selectedItemPosition)

        lifecycleScope.launch {
            val existing = editingExercise
            if (existing == null) {
                // 新建
                val bodyPartId = part?.id ?: return@launch
                exerciseRepository.insert(
                    Exercise(
                        bodyPartId = bodyPartId,
                        name = name,
                        tips = tips,
                        subcategory = sub,
                        isCustom = true
                    )
                )
                Toast.makeText(this@ExerciseEditActivity, "已创建动作「$name」", Toast.LENGTH_SHORT).show()
            } else {
                // 编辑
                exerciseRepository.update(
                    existing.copy(name = name, tips = tips, subcategory = sub)
                )
                Toast.makeText(this@ExerciseEditActivity, "已保存修改", Toast.LENGTH_SHORT).show()
            }
            finish()
        }
    }

    override fun onStart() {
        super.onStart()
        binding.buttonSaveExercise.setOnClickListener { onSave() }
    }
}
