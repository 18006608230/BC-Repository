package com.bc.app.ui.exercise

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.bc.app.data.db.BrawnCatalogDatabase
import com.bc.app.data.repository.ExerciseRepository
import com.bc.app.data.repository.TrainingRecordRepository
import com.bc.app.databinding.ActivityExerciseDetailBinding
import com.bc.app.ui.record.AddRecordActivity
import com.bc.app.ui.timer.TimerActivity
import com.bc.app.utils.OneRmCalculator
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

/**
 * 动作详情页
 * 展示动作基本信息和历史训练记录
 */
class ExerciseDetailActivity : AppCompatActivity() {

    companion object {
        const val EXTRA_EXERCISE_ID = "extra_exercise_id"
        const val EXTRA_EXERCISE_NAME = "extra_exercise_name"
    }

    private lateinit var binding: ActivityExerciseDetailBinding
    private lateinit var trainingRecordAdapter: TrainingRecordAdapter
    private lateinit var exerciseRepository: ExerciseRepository
    private lateinit var trainingRecordRepository: TrainingRecordRepository

    private var exerciseId: Long = -1
    private var exerciseName: String = ""
    // 是否为有氧动作：有氧展示总时长，不做 PR/重量
    private var isCardio: Boolean = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityExerciseDetailBinding.inflate(layoutInflater)
        setContentView(binding.root)

        exerciseId = intent.getLongExtra(EXTRA_EXERCISE_ID, -1)
        exerciseName = intent.getStringExtra(EXTRA_EXERCISE_NAME) ?: "动作详情"

        title = exerciseName
        supportActionBar?.title = exerciseName

        // 页面返回按钮
        binding.buttonBack.setOnClickListener { finish() }

        initRepository()
        initRecyclerView()
        loadExerciseDetails()
        loadPrData()
        observeTrainingRecords()
        setupListeners()
    }

    private fun initRepository() {
        val database = BrawnCatalogDatabase.getDatabase(this)
        exerciseRepository = ExerciseRepository(database.exerciseDao())
        trainingRecordRepository = TrainingRecordRepository(database.trainingRecordDao())
    }

    private fun initRecyclerView() {
        trainingRecordAdapter = TrainingRecordAdapter()

        binding.recyclerViewRecords.apply {
            layoutManager = androidx.recyclerview.widget.LinearLayoutManager(this@ExerciseDetailActivity)
            adapter = trainingRecordAdapter
        }
    }

    private fun loadExerciseDetails() {
        lifecycleScope.launch {
            val exercise = exerciseRepository.getById(exerciseId)
            exercise?.let {
                binding.textViewExerciseName.text = it.name
                binding.textViewExerciseTips.text = it.tips
                loadedExercise = it
                // 识别有氧并重建记录列表为有氧展示形态
                isCardio = it.bodyPartId == com.bc.app.data.entity.Exercise.CARDIO_BODY_PART_SORT
                if (isCardio) {
                    binding.recyclerViewRecords.adapter =
                        TrainingRecordAdapter(isCardio = true)
                    trainingRecordAdapter = binding.recyclerViewRecords.adapter as TrainingRecordAdapter
                }
                // 有氧：更新 PR 卡为累计时长展示
                loadPrData()
            }
        }
    }

    private var loadedExercise: com.bc.app.data.entity.Exercise? = null

    private fun loadPrData() {
        lifecycleScope.launch {
            if (isCardio) {
                // 有氧：展示累计总时长（分钟），隐藏 1RM
                val all = trainingRecordRepository.getByExerciseIdSync(exerciseId)
                val totalMin = all.sumOf { it.weight.toInt() }
                binding.textPrWeightValue.text = "$totalMin"
                binding.textPrOneRmValue.text = "分钟"
                return@launch
            }
            val maxWeight = trainingRecordRepository.getMaxWeightByExerciseId(exerciseId)
            val maxOneRm = trainingRecordRepository.getMaxOneRmByExerciseId(exerciseId)
            binding.textPrWeightValue.text = if (maxWeight != null) "${maxWeight} kg" else "-"
            binding.textPrOneRmValue.text = if (maxOneRm != null) OneRmCalculator.format(maxOneRm) else "-"
        }
    }

    /**
     * 从添加记录页返回后刷新 PR 卡片与历史记录
     */
    override fun onResume() {
        super.onResume()
        loadPrData()
    }

    private fun observeTrainingRecords() {
        lifecycleScope.launch {
            trainingRecordRepository.getByExerciseId(exerciseId).collectLatest { records ->
                trainingRecordAdapter.submitList(records)
            }
        }
    }

    private fun setupListeners() {
        // 添加记录
        binding.fabAddRecord.setOnClickListener {
            val intent = Intent(this, AddRecordActivity::class.java).apply {
                putExtra(AddRecordActivity.EXTRA_EXERCISE_ID, exerciseId)
                putExtra(AddRecordActivity.EXTRA_EXERCISE_NAME, exerciseName)
            }
            startActivity(intent)
        }

        // 组间计时器
        binding.buttonTimer.setOnClickListener {
            val intent = Intent(this, TimerActivity::class.java).apply {
                putExtra(TimerActivity.EXTRA_DEFAULT_SECONDS, 90)
            }
            startActivity(intent)
        }

        // 编辑动作
        binding.buttonEditExercise.setOnClickListener {
            val intent = Intent(this, ExerciseEditActivity::class.java).apply {
                putExtra(ExerciseEditActivity.EXTRA_EXERCISE_ID, exerciseId)
                loadedExercise?.let { putExtra(ExerciseEditActivity.EXTRA_BODY_PART_ID, it.bodyPartId) }
            }
            startActivity(intent)
        }

        // 删除确认
        binding.buttonDeleteExercise.setOnClickListener {
            showDeleteConfirmDialog()
        }
    }

    private fun showDeleteConfirmDialog() {
        AlertDialog.Builder(this)
            .setTitle("删除动作")
            .setMessage("确定要删除这个动作吗？相关的训练记录也会被删除。")
            .setPositiveButton("删除") { _, _ ->
                lifecycleScope.launch {
                    val exercise = exerciseRepository.getById(exerciseId)
                    exercise?.let {
                        exerciseRepository.delete(it)
                        Toast.makeText(this@ExerciseDetailActivity, "已删除", Toast.LENGTH_SHORT).show()
                        finish()
                    }
                }
            }
            .setNegativeButton("取消", null)
            .show()
    }
}
