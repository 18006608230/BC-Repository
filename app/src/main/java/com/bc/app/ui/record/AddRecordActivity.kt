package com.bc.app.ui.record

import com.bc.app.ui.base.BaseActivity

import android.app.Dialog
import android.os.Bundle
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.lifecycle.lifecycleScope
import com.bc.app.R
import com.bc.app.data.db.BrawnCatalogDatabase
import com.bc.app.data.entity.Exercise
import com.bc.app.data.entity.TrainingRecord
import com.bc.app.data.repository.ExerciseRepository
import com.bc.app.data.repository.TrainingRecordRepository
import com.bc.app.databinding.ActivityAddRecordBinding
import com.bc.app.utils.OneRmCalculator
import kotlinx.coroutines.launch

/**
 * 新增训练记录页（仅「快速记录」：重量/组数/平均次数）
 * 有氧动作：只填总时长（分钟）
 */
class AddRecordActivity : BaseActivity() {

    companion object {
        const val EXTRA_EXERCISE_ID = "extra_exercise_id"
        const val EXTRA_EXERCISE_NAME = "extra_exercise_name"
    }

    private lateinit var binding: ActivityAddRecordBinding
    private lateinit var trainingRecordRepository: TrainingRecordRepository
    private lateinit var exerciseRepository: ExerciseRepository

    private var exerciseId: Long = -1

    // 是否为有氧动作（bodyPartId=7），有氧记录只填总时长
    private var isCardio: Boolean = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityAddRecordBinding.inflate(layoutInflater)
        setContentView(binding.root)

        exerciseId = intent.getLongExtra(EXTRA_EXERCISE_ID, -1)
        val exerciseName = intent.getStringExtra(EXTRA_EXERCISE_NAME) ?: "记录训练"

        title = exerciseName
        supportActionBar?.title = exerciseName

        initRepository()
        setupListeners()
        // 先确定是否为有氧，再预填上次记录（避免竞态）
        lifecycleScope.launch {
            detectCardio()
            loadLastRecord()
        }

        // 页面返回按钮
        binding.buttonBack.setOnClickListener { finish() }
    }

    private fun initRepository() {
        val database = BrawnCatalogDatabase.getDatabase(this)
        trainingRecordRepository = TrainingRecordRepository(database.trainingRecordDao())
        exerciseRepository = ExerciseRepository(database.exerciseDao())
    }

    /**
     * 判断当前动作是否为有氧（bodyPartId=7），并据此切换表单形态
     */
    private suspend fun detectCardio() {
        val ex = exerciseRepository.getById(exerciseId)
        isCardio = ex?.bodyPartId == Exercise.CARDIO_BODY_PART_SORT
        if (isCardio) {
            // 有氧：隐藏力量表单；显示总时长
            binding.layoutQuickMode.visibility = View.GONE
            binding.textInputDuration.visibility = View.VISIBLE
        }
    }

    /**
     * 加载上次记录并预填表单（仅快速模式 / 有氧两种）
     */
    private suspend fun loadLastRecord() {
        val lastRecord = trainingRecordRepository.getLatestByExerciseId(exerciseId)
        lastRecord?.let {
            if (isCardio) {
                binding.editTextDuration.setText(it.weight.takeIf { w -> w > 0 }?.toInt()?.toString() ?: "")
                return
            }
            // 快速记录
            binding.editTextWeight.setText(it.weight.toString())
            binding.editTextSets.setText(it.sets.toString())
            binding.editTextAvgReps.setText(it.avgReps.toString())
        }
    }

    private fun setupListeners() {
        binding.buttonSaveRecord.setOnClickListener {
            saveRecord()
        }
    }

    private fun saveRecord() {
        // 有氧：只填总时长，保存为 1 组记录（weight 存时长分钟，sets=1），统计仍按 1 次计
        if (isCardio) {
            saveCardioRecord()
            return
        }
        saveQuickRecord()
    }

    /**
     * 保存有氧记录：填写总时长（分钟），无重量/组数/平均次数
     */
    private fun saveCardioRecord() {
        val durationStr = binding.editTextDuration.text.toString()
        val duration = durationStr.toIntOrNull()
        if (duration == null || duration <= 0) {
            Toast.makeText(this, "请填写总时长（分钟）", Toast.LENGTH_SHORT).show()
            return
        }
        val remark = binding.editTextRemark.text.toString()

        // weight 字段存有氧时长(分钟)，sets=1 表示完成一次，统计按天去重后计 1 次
        val record = TrainingRecord(
            exerciseId = exerciseId,
            date = System.currentTimeMillis(),
            weight = duration.toFloat(),
            sets = 1,
            avgReps = 0,
            remark = remark
        )
        insertRecord(record, isCardio = true)
    }

    /**
     * 保存快速记录
     */
    private fun saveQuickRecord() {
        val weightStr = binding.editTextWeight.text.toString()
        val setsStr = binding.editTextSets.text.toString()
        val avgRepsStr = binding.editTextAvgReps.text.toString()
        val remark = binding.editTextRemark.text.toString()

        val weight = weightStr.toFloatOrNull()
        val sets = setsStr.toIntOrNull()
        val avgReps = avgRepsStr.toIntOrNull()

        if (weight == null || sets == null || avgReps == null) {
            Toast.makeText(this, "请填写重量、组数、次数", Toast.LENGTH_SHORT).show()
            return
        }

        val record = TrainingRecord(
            exerciseId = exerciseId,
            date = System.currentTimeMillis(),
            weight = weight,
            sets = sets,
            avgReps = avgReps,
            remark = remark
        )
        insertRecord(record)
    }

    private fun insertRecord(record: TrainingRecord, isCardio: Boolean = false) {
        lifecycleScope.launch {
            // 有氧：不做 PR 检测，直接保存并返回
            if (isCardio) {
                trainingRecordRepository.insert(record)
                Toast.makeText(this@AddRecordActivity, "记录保存成功", Toast.LENGTH_SHORT).show()
                finish()
                return@launch
            }

            // 记录插入前的历史 PR
            val oldMaxWeight = trainingRecordRepository.getMaxWeightByExerciseId(exerciseId)
            val oldMaxOneRm = trainingRecordRepository.getMaxOneRmByExerciseId(exerciseId)

            // 计算本次记录的代表重量与 1RM
            val newOneRm = OneRmCalculator.fromRecord(record)

            // 插入记录
            trainingRecordRepository.insert(record)

            // PR 检测与庆祝
            val prTypes = OneRmCalculator.checkPr(
                newRecordWeight = record.weight,
                newOneRm = newOneRm,
                oldMaxWeight = oldMaxWeight,
                oldMaxOneRm = oldMaxOneRm
            )

            Toast.makeText(this@AddRecordActivity, "记录保存成功", Toast.LENGTH_SHORT).show()
            if (prTypes.isNotEmpty()) {
                showPrCelebration(prTypes, newOneRm)
            } else {
                finish()
            }
        }
    }

    /**
     * 展示打破个人记录的庆祝（普通卡片弹窗，右上角可关闭）
     */
    private fun showPrCelebration(prTypes: Set<OneRmCalculator.PrType>, oneRm: Float) {
        val title = if (prTypes.size > 1) "🏆 双项新纪录！" else "🏆 新纪录！"
        val msg = buildString {
            append("太强了，你刚刚打破了：\n\n")
            prTypes.forEach { append("• ").append(it.label()).append("\n") }
            if (OneRmCalculator.PrType.ONE_RM in prTypes && oneRm > 0) {
                append("\n当前估算 1RM：").append(OneRmCalculator.format(oneRm))
            }
        }

        val dlg = Dialog(this, R.style.Theme_BrawnCatalog_CardDialog)
        dlg.setContentView(R.layout.dialog_pr_celebration)
        dlg.window?.apply {
            setLayout(
                (resources.displayMetrics.widthPixels * 0.9f).toInt(),
                ViewGroup.LayoutParams.WRAP_CONTENT
            )
            setBackgroundDrawableResource(R.drawable.bg_dialog_card)
        }

        dlg.findViewById<android.widget.TextView>(R.id.textPrTitle)?.text = title
        dlg.findViewById<android.widget.TextView>(R.id.textPrMessage)?.text = msg
        dlg.findViewById<View>(R.id.buttonClose)?.setOnClickListener {
            dlg.dismiss(); finish()
        }
        dlg.findViewById<View>(R.id.buttonPrConfirm)?.setOnClickListener {
            dlg.dismiss(); finish()
        }

        val backdrop = binding.root
        backdrop.alpha = 0.6f
        dlg.setOnDismissListener { backdrop.alpha = 1f; finish() }
        dlg.show()
    }
}
