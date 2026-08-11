package com.bc.app.ui.record

import android.app.Dialog
import android.os.Bundle
import android.text.TextWatcher
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.bc.app.data.db.BrawnCatalogDatabase
import com.bc.app.data.entity.Exercise
import com.bc.app.data.entity.SetDetail
import com.bc.app.data.entity.TrainingRecord
import com.bc.app.data.repository.ExerciseRepository
import com.bc.app.data.repository.TrainingRecordRepository
import com.bc.app.databinding.ActivityAddRecordBinding
import com.bc.app.databinding.ItemSetDetailBinding
import com.bc.app.R
import com.bc.app.ui.plate.PlateCalculatorDialog
import com.bc.app.utils.JsonUtils
import com.bc.app.utils.OneRmCalculator
import kotlinx.coroutines.launch

/**
 * 新增训练记录页
 * 支持双颗粒度：
 *  - 快速记录：填写重量/组数/平均次数
 *  - 专业逐组：逐组填写每组重量/次数/RPE
 * 有氧动作：简化记录，只填总时长，不做逐组/杠铃片
 */
class AddRecordActivity : AppCompatActivity() {

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

    // 专业模式的逐组行绑定列表
    private val setDetailBindings = mutableListOf<ItemSetDetailBinding>()

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
            // 有氧：隐藏模式切换、杠铃片计算器、重量/组数/平均次数；显示总时长
            binding.toggleGroupMode.visibility = View.GONE
            binding.buttonPlateCalculator.visibility = View.GONE
            binding.layoutQuickMode.visibility = View.GONE
            binding.layoutProMode.visibility = View.GONE
            binding.textInputDuration.visibility = View.VISIBLE
        }
    }

    /**
     * 加载上次记录并预填表单
     */
    private suspend fun loadLastRecord() {
        val lastRecord = trainingRecordRepository.getLatestByExerciseId(exerciseId)
            lastRecord?.let {
                // 有氧：预填总时长（时长存在 weight 字段中）；不做逐组/重量预填
                if (isCardio) {
                    binding.editTextDuration.setText(it.weight.takeIf { w -> w > 0 }?.toInt()?.toString() ?: "")
                    return
                }
                // 如果上次是专业记录，切到专业模式并预填
                if (it.isProfessionalRecord()) {
                    binding.toggleGroupMode.check(binding.buttonProMode.id)
                    switchToProMode(true)
                    val details = it.setDetails?.let { d -> JsonUtils.jsonToSetDetails(d) }
                    if (!details.isNullOrEmpty()) {
                        setDetailBindings.forEach { b ->
                            binding.containerSetDetails.removeView(b.root)
                        }
                        setDetailBindings.clear()
                        details.forEach { d -> addSetRow(d) }
                    }
                } else {
                    binding.editTextWeight.setText(it.weight.toString())
                    binding.editTextSets.setText(it.sets.toString())
                    binding.editTextAvgReps.setText(it.avgReps.toString())
                }
            }
    }

    private fun setupListeners() {
        binding.buttonAddSet.setOnClickListener {
            addSetRow(null)
        }

        // 模式切换
        binding.toggleGroupMode.addOnButtonCheckedListener { _, checkedId, isChecked ->
            if (isChecked) {
                switchToProMode(checkedId == binding.buttonProMode.id)
            }
        }

        binding.buttonSaveRecord.setOnClickListener {
            saveRecord()
        }

        // 杠铃片计算器（iOS 风格底部弹层，带动毛玻璃背景）
        binding.buttonPlateCalculator.setOnClickListener {
            PlateCalculatorDialog(this).show()
        }
    }

    private fun switchToProMode(isPro: Boolean) {
        binding.layoutQuickMode.visibility = if (isPro) View.GONE else View.VISIBLE
        binding.layoutProMode.visibility = if (isPro) View.VISIBLE else View.GONE
        if (isPro && setDetailBindings.isEmpty()) {
            addSetRow(null)
        }
    }

    /**
     * 添加一组逐组明细行
     */
    private fun addSetRow(prefill: SetDetail?) {
        val rowBinding = ItemSetDetailBinding.inflate(LayoutInflater.from(this), binding.containerSetDetails, false)
        val rowNumber = setDetailBindings.size + 1
        rowBinding.textSetNumber.text = "第${rowNumber}组"

        prefill?.let { d ->
            rowBinding.editSetWeight.setText(d.weight.takeIf { it > 0 }?.toString() ?: "")
            rowBinding.editSetReps.setText(d.reps.takeIf { it > 0 }?.toString() ?: "")
            rowBinding.editSetRpe.setText(d.rpe?.toString() ?: "")
        }

        // 输入时实时更新估算 1RM
        val updateOneRm = {
            val w = rowBinding.editSetWeight.text.toString().toFloatOrNull()
            val r = rowBinding.editSetReps.text.toString().toIntOrNull()
            val oneRm = if (w != null && r != null) OneRmCalculator.estimate(w, r) else 0f
            rowBinding.textSetOneRm.text = "估算 1RM: ${OneRmCalculator.format(oneRm)}"
        }
        rowBinding.editSetWeight.addTextChangedListener(object : TextWatcher {
            override fun afterTextChanged(s: android.text.Editable?) = updateOneRm()
            override fun beforeTextChanged(s: CharSequence?, a: Int, b: Int, c: Int) {}
            override fun onTextChanged(s: CharSequence?, a: Int, b: Int, c: Int) {}
        })
        rowBinding.editSetReps.addTextChangedListener(object : TextWatcher {
            override fun afterTextChanged(s: android.text.Editable?) = updateOneRm()
            override fun beforeTextChanged(s: CharSequence?, a: Int, b: Int, c: Int) {}
            override fun onTextChanged(s: CharSequence?, a: Int, b: Int, c: Int) {}
        })
        updateOneRm()

        rowBinding.buttonRemoveSet.setOnClickListener {
            binding.containerSetDetails.removeView(rowBinding.root)
            setDetailBindings.remove(rowBinding)
            renumberRows()
        }

        binding.containerSetDetails.addView(rowBinding.root)
        setDetailBindings.add(rowBinding)
        renumberRows()
    }

    /**
     * 重新编号所有组行
     */
    private fun renumberRows() {
        setDetailBindings.forEachIndexed { index, b ->
            b.textSetNumber.text = "第${index + 1}组"
        }
    }

    private fun saveRecord() {
        // 有氧：只填总时长，保存为 1 组记录（weight 存时长分钟，sets=1），统计仍按 1 次计
        if (isCardio) {
            saveCardioRecord()
            return
        }

        // 判断当前模式
        val isProMode = binding.toggleGroupMode.checkedButtonId == binding.buttonProMode.id

        if (isProMode) {
            saveProRecord()
        } else {
            saveQuickRecord()
        }
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
     * 保存快速模式记录
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

    /**
     * 保存专业模式记录
     */
    private fun saveProRecord() {
        // 收集每组的有效数据
        val details = mutableListOf<SetDetail>()
        setDetailBindings.forEachIndexed { index, b ->
            val w = b.editSetWeight.text.toString().toFloatOrNull()
            val r = b.editSetReps.text.toString().toIntOrNull()
            // 跳过完全空白的行
            if (w == null && r == null) return@forEachIndexed
            if (w == null || r == null) {
                Toast.makeText(this, "第${index + 1}组数据不完整", Toast.LENGTH_SHORT).show()
                return
            }
            details.add(
                SetDetail(
                    setNumber = index + 1,
                    weight = w,
                    reps = r,
                    rpe = b.editSetRpe.text.toString().toIntOrNull()
                )
            )
        }

        if (details.isEmpty()) {
            Toast.makeText(this, "请至少填写一组数据", Toast.LENGTH_SHORT).show()
            return
        }

        // 汇总统计数据
        val avgWeight = details.map { it.weight }.average().toFloat()
        val avgReps = details.map { it.reps }.average().toInt()
        val remark = binding.editTextRemark.text.toString()

        val record = TrainingRecord(
            exerciseId = exerciseId,
            date = System.currentTimeMillis(),
            weight = avgWeight,
            sets = details.size,
            avgReps = avgReps,
            remark = remark,
            setDetails = JsonUtils.setDetailsToJson(details),
            rpe = details.mapNotNull { it.rpe }.takeIf { it.isNotEmpty() }?.average()?.toInt()
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
        dlg.setContentView(com.bc.app.R.layout.dialog_pr_celebration)
        dlg.window?.apply {
            setLayout(
                (resources.displayMetrics.widthPixels * 0.9f).toInt(),
                ViewGroup.LayoutParams.WRAP_CONTENT
            )
            setBackgroundDrawableResource(com.bc.app.R.drawable.bg_dialog_card)
        }

        dlg.findViewById<android.widget.TextView>(com.bc.app.R.id.textPrTitle)?.text = title
        dlg.findViewById<android.widget.TextView>(com.bc.app.R.id.textPrMessage)?.text = msg
        dlg.findViewById<View>(com.bc.app.R.id.buttonClose)?.setOnClickListener {
            dlg.dismiss(); finish()
        }
        dlg.findViewById<View>(com.bc.app.R.id.buttonPrConfirm)?.setOnClickListener {
            dlg.dismiss(); finish()
        }

        val backdrop = binding.root
        backdrop.alpha = 0.6f
        dlg.setOnDismissListener { backdrop.alpha = 1f; finish() }
        dlg.show()
    }
}
