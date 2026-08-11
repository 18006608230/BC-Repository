package com.bc.app.ui.calendar

import android.text.Editable
import android.text.TextWatcher
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.bc.app.data.entity.Exercise
import com.bc.app.data.entity.TrainingPlanItem
import com.bc.app.databinding.ItemWorkoutExerciseBinding

/**
 * 训练执行页 · 动作列表适配器
 * 每个动作一张卡片，含上次记录/PR 提示 + 重量/组数/次数输入
 *
 * 输入通过「单一共享 TextWatcher + View tag」持久化到 inputs map：
 *  - 每个 EditText 在 onCreateViewHolder 时绑定一次共享 watcher（不随回收累积）
 *  - 每个 EditText 用 tag 记录其对应的 WorkoutInput 与字段类型，输入时更新对应条目
 *  - 视图回收后重新绑定会回填 inputs 中的值，保证数据不丢、不串
 */
class WorkoutExerciseAdapter(
    private val lastRecordMap: Map<Long, String>,  // exerciseId -> 上次记录摘要
    private val onExerciseClick: ((Exercise) -> Unit)? = null, // 点击动作卡跳转到动作详情
    private val readOnly: Boolean = false,          // 查看模式：输入框禁用
    private val cardioIds: Set<Long> = emptySet()   // 有氧动作 id 集合：仅填总时长
) : RecyclerView.Adapter<WorkoutExerciseAdapter.ViewHolder>() {

    // 单个动作的输入数据（重量kg / 组数 / 次数；有氧仅用时长分钟）
    class WorkoutInput(var weight: Float? = null, var sets: Int? = null, var reps: Int? = null, var duration: Int? = null)

    // 字段类型标识，用于 tag 区分
    private object Field {
        const val WEIGHT = 1
        const val SETS = 2
        const val REPS = 3
        const val DURATION = 4
    }

    // exerciseId -> 输入数据；回收时回填，完成时读取
    val inputs = HashMap<Long, WorkoutInput>()

    private val items = mutableListOf<TrainingPlanItem>()
    private val exerciseMap = HashMap<Long, Exercise>()

    fun setData(planItems: List<TrainingPlanItem>, exercises: List<Exercise>) {
        items.clear()
        items.addAll(planItems)
        exerciseMap.clear()
        for (e in exercises) exerciseMap[e.id] = e
        notifyDataSetChanged()
    }

    fun getExerciseList(): List<Exercise> = items.mapNotNull { exerciseMap[it.exerciseId] }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val binding = ItemWorkoutExerciseBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        // 每个 EditText 只绑定一次共享 watcher
        bindSharedWatcher(binding.editWorkoutWeight, Field.WEIGHT)
        bindSharedWatcher(binding.editWorkoutSets, Field.SETS)
        bindSharedWatcher(binding.editWorkoutReps, Field.REPS)
        bindSharedWatcher(binding.editWorkoutDuration, Field.DURATION)
        return ViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val item = items[position]
        val exercise = exerciseMap[item.exerciseId]
        if (exercise == null) {
            holder.binding.root.visibility = android.view.View.GONE
            return
        }
        holder.binding.root.visibility = android.view.View.VISIBLE
        holder.binding.textViewWorkoutExerciseName.text = exercise.name

        // 动作卡点击 → 跳转动作详情（两种记录方式之一）
        holder.binding.root.isClickable = onExerciseClick != null
        holder.binding.root.setOnClickListener {
            onExerciseClick?.invoke(exercise)
        }

        // 只读模式禁用输入框，仅作查看
        val editable = !readOnly

        val input = inputs.getOrPut(exercise.id) { WorkoutInput() }
        // 用 tag 关联：第一层 = WorkoutInput，第二层 = 字段类型
        setTag(holder.binding.editWorkoutWeight, input)
        setTag(holder.binding.editWorkoutSets, input)
        setTag(holder.binding.editWorkoutReps, input)
        setTag(holder.binding.editWorkoutDuration, input)

        // 有氧：隐藏重量/组数/次数，只填总时长
        val isCardio = exercise.id in cardioIds

        holder.binding.layoutWorkoutWeight.isEnabled = editable
        holder.binding.layoutWorkoutSets.isEnabled = editable
        holder.binding.layoutWorkoutReps.isEnabled = editable
        holder.binding.layoutWorkoutDuration.isEnabled = editable

        if (isCardio) {
            holder.binding.layoutWorkoutWeight.visibility = android.view.View.GONE
            holder.binding.layoutWorkoutSets.visibility = android.view.View.GONE
            holder.binding.layoutWorkoutReps.visibility = android.view.View.GONE
            holder.binding.layoutWorkoutDuration.visibility = android.view.View.VISIBLE
        } else {
            holder.binding.layoutWorkoutWeight.visibility = android.view.View.VISIBLE
            holder.binding.layoutWorkoutSets.visibility = android.view.View.VISIBLE
            holder.binding.layoutWorkoutReps.visibility = android.view.View.VISIBLE
            holder.binding.layoutWorkoutDuration.visibility = android.view.View.GONE
        }

        // 上次记录 / 目标组次提示
        val last = lastRecordMap[exercise.id]
        holder.binding.textViewWorkoutLastPr.text =
            if (last != null) "上次 $last" else if (isCardio) "有氧训练" else "目标 ${item.sets}组×${item.reps}次"

        // 回填（避免回收后输入丢失/串位）
        holder.binding.editWorkoutWeight.setText(
            input.weight?.let { if (it == it.toInt().toFloat()) it.toInt().toString() else it.toString() } ?: ""
        )
        holder.binding.editWorkoutSets.setText(input.sets?.toString() ?: "")
        holder.binding.editWorkoutReps.setText(input.reps?.toString() ?: "")
        holder.binding.editWorkoutDuration.setText(input.duration?.toString() ?: "")
    }

    /**
     * 绑定一个共享 watcher，通过 EditText 的 tag（[input, field] 数组）更新对应 WorkoutInput
     */
    private fun bindSharedWatcher(
        edit: com.google.android.material.textfield.TextInputEditText,
        field: Int
    ) {
        edit.removeTextChangedListener(sharedWatcher)
        edit.addTextChangedListener(object : TextWatcher {
            override fun afterTextChanged(s: Editable?) {
                val tag = edit.tag
                if (tag !is Array<*>) return
                val input = tag[0] as? WorkoutInput ?: return
                val value = s?.toString().orEmpty()
                when (field) {
                    Field.WEIGHT -> input.weight = value.toFloatOrNull()
                    Field.SETS -> input.sets = value.toIntOrNull()
                    Field.REPS -> input.reps = value.toIntOrNull()
                    Field.DURATION -> input.duration = value.toIntOrNull()
                }
            }
            override fun beforeTextChanged(s: CharSequence?, a: Int, b: Int, c: Int) {}
            override fun onTextChanged(s: CharSequence?, a: Int, b: Int, c: Int) {}
        })
    }

    private fun setTag(edit: com.google.android.material.textfield.TextInputEditText, input: WorkoutInput) {
        edit.tag = arrayOf<Any>(input)
    }

    override fun getItemCount(): Int = items.size

    inner class ViewHolder(
        val binding: ItemWorkoutExerciseBinding
    ) : RecyclerView.ViewHolder(binding.root)

    companion object {
        // 占位 watcher，仅用于 removeTextChangedListener 避免重复上报（实际逻辑用匿名 watcher + tag）
        private val sharedWatcher = object : TextWatcher {
            override fun afterTextChanged(s: Editable?) {}
            override fun beforeTextChanged(s: CharSequence?, a: Int, b: Int, c: Int) {}
            override fun onTextChanged(s: CharSequence?, a: Int, b: Int, c: Int) {}
        }
    }
}
