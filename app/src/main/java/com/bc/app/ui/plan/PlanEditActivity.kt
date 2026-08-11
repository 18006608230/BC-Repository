package com.bc.app.ui.plan

import android.os.Bundle
import android.view.LayoutInflater
import android.widget.ArrayAdapter
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.bc.app.data.db.BrawnCatalogDatabase
import com.bc.app.data.entity.Exercise
import com.bc.app.data.entity.TrainingPlan
import com.bc.app.data.entity.TrainingPlanItem
import com.bc.app.databinding.ActivityPlanEditBinding
import com.bc.app.databinding.ItemPlanEditBinding
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * 新建/编辑训练计划
 */
class PlanEditActivity : AppCompatActivity() {

    companion object {
        const val EXTRA_PLAN_ID = "extra_plan_id"
    }

    private lateinit var binding: ActivityPlanEditBinding

    private var planId: Long = -1
    private var existingPlan: TrainingPlan? = null

    // 可用动作列表（id -> 名称）
    private val exerciseList = mutableListOf<Exercise>()
    private val itemBindings = mutableListOf<ItemPlanEditBinding>()

    // 自定义动作默认归属部位（取第一个部位）
    private var defaultBodyPartId: Long = 1L

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityPlanEditBinding.inflate(layoutInflater)
        setContentView(binding.root)

        planId = intent.getLongExtra(EXTRA_PLAN_ID, -1)
        title = if (planId > 0) "编辑计划" else "新建计划"

        // 页面返回按钮
        binding.buttonBack.setOnClickListener { finish() }

        setupListeners()
        loadPlanAndExercises()
    }

    private fun setupListeners() {
        binding.buttonAddItem.setOnClickListener { addPlanItemRow(null, -1) }
        binding.buttonSavePlan.setOnClickListener { savePlan() }
    }

    private fun loadPlanAndExercises() {
        lifecycleScope.launch {
            val database = BrawnCatalogDatabase.getDatabase(this@PlanEditActivity)

            // 加载所有动作用于选择器
            val exercises = withContext(Dispatchers.IO) { database.exerciseDao().getAllSyncSafe() }
            exerciseList.clear()
            exerciseList.addAll(exercises)

            // 记录默认部位（自定义动作用）
            val bodyParts = withContext(Dispatchers.IO) { database.bodyPartDao().getAllSync() }
            if (bodyParts.isNotEmpty()) defaultBodyPartId = bodyParts.first().id

            if (planId > 0) {
                existingPlan = withContext(Dispatchers.IO) { database.trainingPlanDao().getPlanById(planId) }
                existingPlan?.let { plan ->
                    binding.editTextPlanName.setText(plan.name)
                    binding.editTextPlanDescription.setText(plan.description)
                    binding.switchActivePlan.isChecked = plan.isActive

                    val items = withContext(Dispatchers.IO) {
                        database.trainingPlanDao().getItemsByPlanSync(plan.id)
                    }
                    for (item in items) {
                        addPlanItemRow(item, item.exerciseId)
                    }
                }
            }
        }
    }

    /**
     * 添加一个计划动作行
     */
    private fun addPlanItemRow(item: TrainingPlanItem?, exerciseId: Long) {
        val rowBinding = ItemPlanEditBinding.inflate(LayoutInflater.from(this), binding.containerPlanItems, false)

        // 设置动作选择器
        val names = exerciseList.map { it.name }
        val adapter = ArrayAdapter(this, android.R.layout.simple_dropdown_item_1line, names)
        rowBinding.editExerciseSelector.setAdapter(adapter)

        if (item != null) {
            val exercise = exerciseList.find { it.id == exerciseId }
            rowBinding.editExerciseSelector.setText(exercise?.name ?: "", false)
            rowBinding.editItemSets.setText(item.sets.toString())
            rowBinding.editItemReps.setText(item.reps.toString())
        } else if (exerciseId > 0) {
            val exercise = exerciseList.find { it.id == exerciseId }
            rowBinding.editExerciseSelector.setText(exercise?.name ?: "", false)
        } else {
            rowBinding.editItemSets.setText("3")
            rowBinding.editItemReps.setText("10")
        }

        rowBinding.buttonRemoveItem.setOnClickListener {
            binding.containerPlanItems.removeView(rowBinding.root)
            itemBindings.remove(rowBinding)
        }

        binding.containerPlanItems.addView(rowBinding.root)
        itemBindings.add(rowBinding)
    }

    /**
     * 保存计划
     */
    private fun savePlan() {
        val name = binding.editTextPlanName.text.toString().trim()
        if (name.isEmpty()) {
            Toast.makeText(this, "请输入计划名称", Toast.LENGTH_SHORT).show()
            return
        }

        // 收集计划条目
        val itemsToSave = mutableListOf<Triple<Exercise, Int, Int>>()
        for (b in itemBindings) {
            val exerciseName = b.editExerciseSelector.text.toString().trim()
            if (exerciseName.isEmpty()) {
                Toast.makeText(this, "请填写每个动作的名称", Toast.LENGTH_SHORT).show()
                return
            }
            val exercise = exerciseList.find { it.name == exerciseName }
            if (exercise == null) {
                // 支持自定义动作名称：不存在则新建自定义动作
                itemsToSave.add(Triple(
                    Exercise(bodyPartId = defaultBodyPartId, name = exerciseName, isCustom = true),
                    b.editItemSets.text.toString().toIntOrNull() ?: 3,
                    b.editItemReps.text.toString().toIntOrNull() ?: 10
                ))
            } else {
                val sets = b.editItemSets.text.toString().toIntOrNull() ?: 3
                val reps = b.editItemReps.text.toString().toIntOrNull() ?: 10
                itemsToSave.add(Triple(exercise, sets, reps))
            }
        }

        if (itemsToSave.isEmpty()) {
            Toast.makeText(this, "请至少添加一个动作", Toast.LENGTH_SHORT).show()
            return
        }

        lifecycleScope.launch {
            val database = BrawnCatalogDatabase.getDatabase(this@PlanEditActivity)

            // 若设为当前计划，先取消所有计划的使用状态
            if (binding.switchActivePlan.isChecked) {
                database.trainingPlanDao().deactivateAll()
            }

            val plan = if (existingPlan != null) {
                existingPlan!!.copy(
                    name = name,
                    description = binding.editTextPlanDescription.text.toString().trim(),
                    isActive = binding.switchActivePlan.isChecked
                )
            } else {
                TrainingPlan(
                    name = name,
                    description = binding.editTextPlanDescription.text.toString().trim(),
                    isActive = binding.switchActivePlan.isChecked
                )
            }

            val savedPlanId = if (plan.id > 0) {
                database.trainingPlanDao().updatePlan(plan)
                plan.id
            } else {
                database.trainingPlanDao().insertPlan(plan)
            }

            // 清空旧条目并写入新条目
            database.trainingPlanDao().deleteItemsByPlan(savedPlanId)
            val newItems = itemsToSave.mapIndexed { index, (ex, sets, reps) ->
                // 自定义动作（id=0）：先入库拿到真实 id
                val finalExerciseId = if (ex.id > 0) ex.id else {
                    database.exerciseDao().insert(ex)
                }
                TrainingPlanItem(
                    planId = savedPlanId,
                    exerciseId = finalExerciseId,
                    sets = sets,
                    reps = reps,
                    sortOrder = index
                )
            }
            database.trainingPlanDao().insertItems(newItems)

            Toast.makeText(this@PlanEditActivity, "计划已保存", Toast.LENGTH_SHORT).show()
            finish()
        }
    }
}
