package com.bc.app.ui.plan

import com.bc.app.ui.base.BaseActivity

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.bc.app.data.db.BrawnCatalogDatabase
import com.bc.app.data.entity.Exercise
import com.bc.app.databinding.ActivityPlanDayExercisesBinding
import com.bc.app.ui.exercise.ExerciseDetailActivity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * 计划详情 · 某训练日的动作卡片列表
 * 展示当日动作（含配图、组×次），点击卡片进入动作详情
 */
class PlanDayExercisesActivity : BaseActivity() {

    companion object {
        const val EXTRA_PLAN_ID = "extra_plan_id"
        const val EXTRA_DAY_ID = "extra_day_id"
        const val EXTRA_DAY_NAME = "extra_day_name"
    }

    private lateinit var binding: ActivityPlanDayExercisesBinding
    private lateinit var adapter: PlanDayExerciseAdapter

    private var dayId: Long = -1

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityPlanDayExercisesBinding.inflate(layoutInflater)
        setContentView(binding.root)

        dayId = intent.getLongExtra(EXTRA_DAY_ID, -1)
        val dayName = intent.getStringExtra(EXTRA_DAY_NAME) ?: "训练日"
        binding.textViewDayName.text = dayName

        binding.buttonBack.setOnClickListener { finish() }

        adapter = PlanDayExerciseAdapter { exercise -> openExerciseDetail(exercise) }
        binding.recyclerViewExercises.layoutManager = LinearLayoutManager(this)
        binding.recyclerViewExercises.adapter = adapter

        if (dayId <= 0) {
            Toast.makeText(this, "训练日不存在", Toast.LENGTH_SHORT).show()
            finish()
            return
        }
        loadDayExercises()
    }

    private fun loadDayExercises() {
        lifecycleScope.launch {
            val database = BrawnCatalogDatabase.getDatabase(this@PlanDayExercisesActivity)
            val day = withContext(Dispatchers.IO) { database.planDayDao().getDayById(dayId) }
            if (day == null) {
                Toast.makeText(this@PlanDayExercisesActivity, "训练日不存在", Toast.LENGTH_SHORT).show()
                finish()
                return@launch
            }

            // 休息日：无动作，显示空状态
            if (day.isRestDay) {
                binding.textViewDayMeta.text = "休息日"
                binding.textViewEmpty.visibility = View.VISIBLE
                return@launch
            }

            val items = withContext(Dispatchers.IO) {
                database.trainingPlanDao().getItemsByDaySync(dayId)
            }
            if (items.isEmpty()) {
                binding.textViewDayMeta.text = "暂无动作"
                binding.textViewEmpty.visibility = View.VISIBLE
                binding.textViewEmpty.text = "该训练日还没有动作"
                return@launch
            }

            val exercises = withContext(Dispatchers.IO) {
                database.exerciseDao().getAllSyncSafe().associateBy { it.id }
            }
            binding.textViewDayMeta.text = "${items.size} 个动作"

            val list = items.mapNotNull { item ->
                val ex = exercises[item.exerciseId] ?: return@mapNotNull null
                ex to (item.sets to item.reps)
            }
            adapter.submit(list)
        }
    }

    private fun openExerciseDetail(exercise: Exercise) {
        val intent = Intent(this, ExerciseDetailActivity::class.java).apply {
            putExtra(ExerciseDetailActivity.EXTRA_EXERCISE_ID, exercise.id)
            putExtra(ExerciseDetailActivity.EXTRA_EXERCISE_NAME, exercise.name)
        }
        startActivity(intent)
    }
}