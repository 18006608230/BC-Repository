package com.bc.app.ui.calendar

import com.bc.app.ui.base.BaseActivity

import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.bc.app.data.db.BrawnCatalogDatabase
import com.bc.app.data.entity.Exercise
import com.bc.app.data.entity.TrainingPlanItem
import com.bc.app.data.entity.TrainingRecord
import com.bc.app.databinding.ActivityWorkoutBinding
import com.bc.app.utils.JsonUtils
import com.bc.app.utils.OneRmCalculator
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Locale

/**
 * 训练执行页（日历「开始训练」闭环核心）
 * 逐组记录重量/组数/次数，完成后自动生成训练记录
 *
 * 支持两种启动方式：
 *  - 传入 planId（可选 dayId）：按计划（或某训练日）执行
 *  - 传入 exerciseIds：日历单日自由选动作执行
 */
class WorkoutActivity : BaseActivity() {

    companion object {
        const val EXTRA_PLAN_ID = "extra_plan_id"
        const val EXTRA_DAY_ID = "extra_day_id"          // Long, -1 表示全部
        const val EXTRA_DATE = "extra_date"              // Long 时间戳
        const val EXTRA_EXERCISE_IDS = "extra_exercise_ids"
        const val EXTRA_READ_ONLY = "extra_read_only"    // true=查看当日训练计划（只读）
    }

    private lateinit var binding: ActivityWorkoutBinding
    private lateinit var adapter: WorkoutExerciseAdapter

    private var planId: Long = -1
    private var dayId: Long = -1
    private var targetDate: Long = System.currentTimeMillis()
    private var readOnly: Boolean = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityWorkoutBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // 页面返回按钮
        binding.buttonBack.setOnClickListener { finish() }

        planId = intent.getLongExtra(EXTRA_PLAN_ID, -1)
        dayId = intent.getLongExtra(EXTRA_DAY_ID, -1)
        targetDate = intent.getLongExtra(EXTRA_DATE, System.currentTimeMillis())
        readOnly = intent.getBooleanExtra(EXTRA_READ_ONLY, false)

        val exIds = intent.getLongArrayExtra(EXTRA_EXERCISE_IDS)

        binding.recyclerViewWorkout.layoutManager = LinearLayoutManager(this)

        // 只读模式（查看当日训练计划）：隐藏底部“完成训练”按钮
        if (readOnly) {
            binding.textViewWorkoutTitle.text = "当日训练计划"
            binding.buttonFinishWorkout.visibility = View.GONE
        }

        // 标题
        val dateText = SimpleDateFormat("yyyy年M月d日", Locale.getDefault()).format(java.util.Date(targetDate))
        binding.textViewWorkoutDate.text = dateText

        if (exIds != null && exIds.isNotEmpty()) {
            // 自由选动作模式
            binding.textViewWorkoutTitle.text = "自由训练"
            loadExercisesByIds(exIds)
        } else if (planId > 0) {
            // 按计划执行
            loadPlanItems()
        } else {
            // 默认：当前启用计划
            binding.textViewWorkoutTitle.text = "开始训练"
            loadActivePlan()
        }

        binding.buttonFinishWorkout.setOnClickListener { finishWorkout() }
    }

    private fun setupAdapter() {
        adapter = WorkoutExerciseAdapter(emptyMap())
        binding.recyclerViewWorkout.adapter = adapter
    }

    private fun loadActivePlan() {
        lifecycleScope.launch {
            val db = BrawnCatalogDatabase.getDatabase(this@WorkoutActivity)
            val plan = withContext(Dispatchers.IO) { db.trainingPlanDao().getActivePlanSync() }
            if (plan != null) {
                planId = plan.id
                binding.textViewWorkoutTitle.text = plan.name
                loadPlanItems()
            } else {
                binding.textViewWorkoutTitle.text = "尚未设置当前计划"
                binding.textViewWorkoutMeta.text = "请先在「计划」中设为当前计划"
                binding.buttonFinishWorkout.isEnabled = false
            }
        }
    }

    private fun loadPlanItems() {
        lifecycleScope.launch {
            val db = BrawnCatalogDatabase.getDatabase(this@WorkoutActivity)
            val (items, exercises, dayName) = withContext(Dispatchers.IO) {
                val allItems = db.trainingPlanDao().getItemsByPlanSync(planId)
                val filtered = if (dayId > 0)
                    allItems.filter { it.dayId == dayId }
                else
                    allItems
                val exs = db.exerciseDao().getAllSyncSafe()
                // 训练日名
                var dayLabel = "全部训练日"
                if (dayId > 0) {
                    val d = db.planDayDao().getDaysByPlanSync(planId).find { it.id == dayId }
                    dayLabel = d?.name ?: dayLabel
                }
                Triple(filtered, exs, dayLabel)
            }

            binding.textViewWorkoutMeta.text = "共 ${items.size} 个动作 · $dayName"

            // 有氧动作 id 集合：有氧只填总时长
            val cardioIds = exercises.filter { it.bodyPartId == com.bc.app.data.entity.Exercise.CARDIO_BODY_PART_SORT }
                .map { it.id }.toSet()

            // 加载每个动作的最近记录，作预填与 PR 提示
            val lastMap = HashMap<Long, String>()
            for (item in items) {
                val last = db.trainingRecordDao().getLatestByExerciseId(item.exerciseId)
                if (last != null) {
                    lastMap[item.exerciseId] =
                        if (item.exerciseId in cardioIds)
                            "${last.weight.toInt()} 分钟"
                        else
                            "${last.weight}kg × ${last.sets}组×${last.avgReps}次"
                }
            }
            adapter = WorkoutExerciseAdapter(lastMap, { exercise ->
                // 点击动作卡 → 跳转动作详情页
                val intent = android.content.Intent(this@WorkoutActivity, com.bc.app.ui.exercise.ExerciseDetailActivity::class.java).apply {
                    putExtra(com.bc.app.ui.exercise.ExerciseDetailActivity.EXTRA_EXERCISE_ID, exercise.id)
                }
                startActivity(intent)
            }, readOnly, cardioIds)
            binding.recyclerViewWorkout.adapter = adapter
            adapter.setData(items, exercises)
        }
    }

    private fun loadExercisesByIds(exIds: LongArray) {
        lifecycleScope.launch {
            val db = BrawnCatalogDatabase.getDatabase(this@WorkoutActivity)
            val (items, exercises, lastMap) = withContext(Dispatchers.IO) {
                val exs = db.exerciseDao().getAllSyncSafe()
                    .filter { exIds.contains(it.id) }
                val fakeItems = exs.mapIndexed { i, e ->
                    TrainingPlanItem(planId = 0, exerciseId = e.id, sets = 3, reps = 10, sortOrder = i)
                }
                val lm = HashMap<Long, String>()
                for (e in exs) {
                    val last = db.trainingRecordDao().getLatestByExerciseId(e.id)
                    if (last != null) {
                        lm[e.id] =
                            if (e.bodyPartId == com.bc.app.data.entity.Exercise.CARDIO_BODY_PART_SORT)
                                "${last.weight.toInt()} 分钟"
                            else
                                "${last.weight}kg × ${last.sets}组×${last.avgReps}次"
                    }
                }
                Triple(fakeItems, exs, lm)
            }
            binding.textViewWorkoutMeta.text = "共 ${items.size} 个动作"
            val cardioIds = exercises.filter { it.bodyPartId == com.bc.app.data.entity.Exercise.CARDIO_BODY_PART_SORT }
                .map { it.id }.toSet()
            adapter = WorkoutExerciseAdapter(lastMap, { exercise ->
                val intent = android.content.Intent(this@WorkoutActivity, com.bc.app.ui.exercise.ExerciseDetailActivity::class.java).apply {
                    putExtra(com.bc.app.ui.exercise.ExerciseDetailActivity.EXTRA_EXERCISE_ID, exercise.id)
                }
                startActivity(intent)
            }, readOnly, cardioIds)
            binding.recyclerViewWorkout.adapter = adapter
            adapter.setData(items, exercises)
        }
    }

    /**
     * 完成训练：收集所有已填动作，生成训练记录
     */
    private fun finishWorkout() {
        if (!::adapter.isInitialized) return
        val exerciseList = adapter.getExerciseList()
        val exerciseNameMap = exerciseList.associateBy { it.id }

        // 收集有效动作（从 inputs map 读取，避免回收串位）
        val records = mutableListOf<TrainingRecord>()
        val now = targetDate
        val cardioIds = exerciseList.filter { it.bodyPartId == com.bc.app.data.entity.Exercise.CARDIO_BODY_PART_SORT }
            .map { it.id }.toSet()
        for ((exerciseId, input) in adapter.inputs) {
            // 有氧：只填总时长，保存为 1 组记录（weight 存时长分钟，sets=1），统计按 1 次计
            if (exerciseId in cardioIds) {
                val d = input.duration
                if (d == null || d <= 0) {
                    val name = exerciseNameMap[exerciseId]?.name ?: "动作"
                    Toast.makeText(this, "「$name」请填写总时长（分钟）", Toast.LENGTH_SHORT).show()
                    return
                }
                records.add(
                    TrainingRecord(
                        exerciseId = exerciseId,
                        date = now,
                        weight = d.toFloat(),
                        sets = 1,
                        avgReps = 0,
                        remark = "训练执行"
                    )
                )
                continue
            }
            val w = input.weight
            val s = input.sets
            val r = input.reps
            if (w == null && s == null && r == null) continue // 完全空白，跳过
            if (w == null || s == null || r == null) {
                val name = exerciseNameMap[exerciseId]?.name ?: "动作"
                Toast.makeText(this, "「$name」数据不完整", Toast.LENGTH_SHORT).show()
                return
            }
            val details = (1..s).map { setNo ->
                com.bc.app.data.entity.SetDetail(
                    setNumber = setNo,
                    reps = r,
                    weight = w,
                    rpe = null,
                    restSeconds = null,
                    completed = true,
                    note = null
                )
            }
            records.add(
                TrainingRecord(
                    exerciseId = exerciseId,
                    date = now,
                    weight = w,
                    sets = s,
                    avgReps = r,
                    remark = "训练执行",
                    setDetails = JsonUtils.setDetailsToJson(details)
                )
            )
        }

        if (records.isEmpty()) {
            Toast.makeText(this, "请至少填写一个动作", Toast.LENGTH_SHORT).show()
            return
        }

        lifecycleScope.launch {
            val db = BrawnCatalogDatabase.getDatabase(this@WorkoutActivity)
            // 完成训练前检测各动作历史 PR（在插入前记录旧最大值，避免误报）
            val prMessages = mutableListOf<String>()
            withContext(Dispatchers.IO) {
                val oldMaxMap = HashMap<Long, Float?>()
                for (rec in records) {
                    oldMaxMap[rec.exerciseId] = db.trainingRecordDao().getMaxWeightByExerciseId(rec.exerciseId)
                }
                db.trainingRecordDao().insertAll(records)
                for (rec in records) {
                    // 有氧不参与 PR 判定
                    if (rec.exerciseId in cardioIds) continue
                    val oldMax = oldMaxMap[rec.exerciseId]
                    if (oldMax != null && rec.weight > oldMax) {
                        val ex = db.exerciseDao().getById(rec.exerciseId)
                        prMessages.add("${ex?.name ?: "动作"} 突破 ${rec.weight}kg")
                    }
                }
            }

            Toast.makeText(this@WorkoutActivity, "训练完成，已记录 ${records.size} 个动作", Toast.LENGTH_SHORT).show()
            if (prMessages.isNotEmpty()) {
                androidx.appcompat.app.AlertDialog.Builder(this@WorkoutActivity)
                    .setTitle("🏆 新纪录")
                    .setMessage(prMessages.joinToString("\n"))
                    .setPositiveButton("太棒了") { _, _ -> finish() }
                    .show()
            } else {
                setResult(android.app.Activity.RESULT_OK)
                finish()
            }
        }
    }
}
