package com.bc.app.ui.plan

import com.bc.app.ui.base.BaseActivity

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.widget.Toast
import androidx.lifecycle.lifecycleScope
import com.bc.app.R
import com.bc.app.data.db.BrawnCatalogDatabase
import com.bc.app.data.entity.BodyPart
import com.bc.app.data.entity.Exercise
import com.bc.app.data.entity.PlanDay
import com.bc.app.data.entity.TrainingPlan
import com.bc.app.data.entity.TrainingPlanItem
import com.bc.app.databinding.ActivityPlanEditBinding
import com.bc.app.databinding.ItemPlanDayEditBinding
import com.bc.app.databinding.ItemPlanEditBinding
import com.bc.app.utils.loadExerciseImage
import com.google.android.material.chip.Chip
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * 新建/编辑训练计划
 *
 * v13 重新设计：
 * - 先填「周期天数」，点「生成天卡」生成对应数量的天卡
 * - 每张天卡通过部位标签 chip（胸部 / 肩部 / 背部 ...，可多选）命名，
 *   选中的标签自动组合成日名（如 胸部+肩部）
 * - 非休息日的天卡点「+ 添加动作」弹出分类选择器：先选部位，再选该部位的动作
 * - 不再有「未分组动作」概念，所有动作都归属某一天
 */
class PlanEditActivity : BaseActivity() {

    companion object {
        const val EXTRA_PLAN_ID = "extra_plan_id"
        /** 周期天数上限，防止误输入巨大数字导致卡死 */
        private const val MAX_CYCLE_DAYS = 31
        /** 训练日部位标签（与预置 body_parts 一致；「颈部」已下线） */
        private val PRESET_DAY_TAGS = listOf(
            "胸部", "背部", "肩部", "手臂", "腿部", "核心", "有氧"
        )
    }

    private lateinit var binding: ActivityPlanEditBinding

    private var planId: Long = -1
    private var existingPlan: TrainingPlan? = null

    // 可用动作列表（按部位顺序分组排序后）
    private val exerciseList = mutableListOf<Exercise>()
    private var bodyPartList: List<BodyPart> = emptyList()

    // 已加载的训练日与条目（编辑模式用）
    private var loadedDays: List<PlanDay> = emptyList()
    private var loadedItems: List<TrainingPlanItem> = emptyList()

    // 训练日分组（按显示顺序）
    private data class DaySection(
        var originalId: Long,                    // 已存在的 dayId；新建则为 0
        var dayIndex: Int,
        /** 选中的部位标签（按勾选顺序），拼接后即为训练日名称 */
        val tags: LinkedHashSet<String> = LinkedHashSet(),
        var isRestDay: Boolean,
        val itemBindings: MutableList<ItemRow> = mutableListOf(),
        var binding: ItemPlanDayEditBinding? = null
    )

    /** 一行动作卡片：布局绑定 + 当前选中的动作 id */
    private data class ItemRow(
        val binding: ItemPlanEditBinding,
        var exerciseId: Long? = null
    )

    private val daySections = mutableListOf<DaySection>()

    /** 当前训练日名称：选中标签按顺序拼接（如 胸部+肩部） */
    private fun DaySection.displayName(): String = tags.joinToString("+")

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityPlanEditBinding.inflate(layoutInflater)
        setContentView(binding.root)

        planId = intent.getLongExtra(EXTRA_PLAN_ID, -1)
        title = if (planId > 0) "编辑计划" else "新建计划"

        binding.buttonBack.setOnClickListener { finish() }
        setupListeners()
        loadPlanAndExercises()
    }

    private fun setupListeners() {
        // 生成天卡：读取周期天数，重建对应数量的天卡
        binding.buttonGenerateDays.setOnClickListener { generateDaysFromInput() }
        binding.buttonSavePlan.setOnClickListener { savePlan() }
    }

    private fun loadPlanAndExercises() {
        lifecycleScope.launch {
            val database = BrawnCatalogDatabase.getDatabase(this@PlanEditActivity)

            val bodyParts = withContext(Dispatchers.IO) { database.bodyPartDao().getAllSync() }
            bodyPartList = bodyParts.sortedBy { it.sortOrder }

            // 动作按部位顺序排序，弹窗"全部"视图下按部位分组井然有序
            val partOrder = bodyPartList.associate { it.id to it.sortOrder }
            val exercises = withContext(Dispatchers.IO) { database.exerciseDao().getAllSyncSafe() }
            exerciseList.clear()
            exerciseList.addAll(exercises.sortedBy { partOrder[it.bodyPartId] ?: 999 })

            if (planId > 0) {
                existingPlan = withContext(Dispatchers.IO) { database.trainingPlanDao().getPlanById(planId) }
                existingPlan?.let { plan ->
                    binding.editTextPlanName.setText(plan.name)
                    binding.editTextPlanDescription.setText(plan.description)
                    binding.switchActivePlan.isChecked = plan.isActive

                    loadedDays = withContext(Dispatchers.IO) { database.planDayDao().getDaysByPlanSync(plan.id) }
                    loadedItems = withContext(Dispatchers.IO) { database.trainingPlanDao().getItemsByPlanSync(plan.id) }
                }
            }
            renderLoadedData()
        }
    }

    /**
     * 把已加载的天/条目渲染到页面。
     * - 编辑已有计划：按已有天数回填周期天数输入框，并按日渲染天卡 + 当日动作
     *   （旧的自由文本日名会被解析成部位标签，解析不了的保留为自定义标签）
     * - 新建计划：周期天数留空，等待用户填写后点「生成天卡」
     */
    private fun renderLoadedData() {
        clearAllDaySections()

        if (loadedDays.isNotEmpty()) {
            // 回填周期天数
            binding.editTextCycleDays.setText(loadedDays.size.toString())
            binding.editTextCycleDays.setSelection(binding.editTextCycleDays.text?.length ?: 0)

            val itemsByDay = loadedItems.groupBy { it.dayId }
            for (day in loadedDays) {
                val section = addDaySection(
                    prefillTags = parseDayNameTags(day.name),
                    prefillRest = day.isRestDay,
                    dayIndex = day.dayIndex
                )
                section.originalId = day.id
                val dayItems = itemsByDay[day.id].orEmpty().sortedBy { it.sortOrder }
                for (item in dayItems) {
                    addItemRow(item, section)
                }
            }
            // 旧数据中 dayId = null 的条目：归并到第 1 天，避免数据丢失
            val ungrouped = loadedItems.filter { it.dayId == null }.sortedBy { it.sortOrder }
            if (ungrouped.isNotEmpty()) {
                val firstSection = daySections.firstOrNull()
                if (firstSection != null) {
                    for (item in ungrouped) addItemRow(item, firstSection)
                    Toast.makeText(this, "已把 ${ungrouped.size} 个未分组动作并入第 1 天", Toast.LENGTH_SHORT).show()
                }
            }
        } else if (loadedItems.isNotEmpty()) {
            // 旧计划完全没有天：按 1 天重建，把全部动作放入第 1 天
            binding.editTextCycleDays.setText("1")
            val section = addDaySection(dayIndex = 1)
            for (item in loadedItems.sortedBy { it.sortOrder }) {
                addItemRow(item, section)
            }
        }
    }

    /**
     * 把旧的自由文本日名解析成部位标签：
     * - 「胸部+肩部」→ [胸部, 肩部]
     * - 「胸日」/「腿日」→ 去"部"宽松匹配（胸 / 腿）
     * - 剩余无法识别的文本（长度 >= 2）保留为自定义标签，避免名称信息丢失
     */
    private fun parseDayNameTags(name: String?): List<String> {
        if (name.isNullOrBlank() || name == "休息日") return emptyList()
        val result = LinkedHashSet<String>()
        var rest = name.orEmpty()
        for (tag in PRESET_DAY_TAGS) {
            if (rest.contains(tag)) {
                result.add(tag)
                rest = rest.replace(tag, "")
            } else if (tag.length >= 2 && rest.contains(tag.dropLast(1))) {
                // 宽松匹配：如「胸日」包含「胸」→ 匹配「胸部」
                result.add(tag)
                rest = rest.replace(tag.dropLast(1), "")
            }
        }
        // 清理分隔符等噪声后，剩余有意义文本作为自定义标签保留
        val leftover = rest.replace(Regex("[+·、,，/\\s日（）()]+"), "")
        if (leftover.length >= 2) result.add(leftover)
        return result.toList()
    }

    /**
     * 点击「生成天卡」：读取输入的周期天数，把天卡数量调整到目标值。
     * 已存在的天卡会保留其名称 / 休息日 / 动作，避免用户重新录入。
     */
    private fun generateDaysFromInput() {
        val input = binding.editTextCycleDays.text?.toString()?.trim().orEmpty()
        val count = input.toIntOrNull()
        if (count == null || count <= 0) {
            Toast.makeText(this, "请输入周期天数（1 ~ $MAX_CYCLE_DAYS）", Toast.LENGTH_SHORT).show()
            return
        }
        if (count > MAX_CYCLE_DAYS) {
            Toast.makeText(this, "周期天数最多 $MAX_CYCLE_DAYS 天", Toast.LENGTH_SHORT).show()
            return
        }
        resizeDaySections(count)
        Toast.makeText(this, "已生成 $count 张天卡，可在下方逐天编辑", Toast.LENGTH_SHORT).show()

        // 生成后滚动到天卡区域，让用户立刻看到可编辑的每一天的卡片
        binding.containerDays.post {
            binding.scrollViewPlanEdit.smoothScrollTo(0, binding.containerDays.top)
        }
    }

    /**
     * 把天卡数量调整到 [targetCount]：
     * - 多出 → 移除尾部天卡
     * - 不足 → 追加新天卡
     * 已有的天卡（含其内部动作）保持不变。
     */
    private fun resizeDaySections(targetCount: Int) {
        while (daySections.size > targetCount) {
            val last = daySections.removeAt(daySections.size - 1)
            last.binding?.let { binding.containerDays.removeView(it.root) }
        }
        while (daySections.size < targetCount) {
            addDaySection(dayIndex = daySections.size + 1)
        }
        // 刷新所有天卡的「第 N 天」序号
        refreshDayIndexes()
    }

    private fun refreshDayIndexes() {
        for ((idx, section) in daySections.withIndex()) {
            section.dayIndex = idx + 1
            section.binding?.textViewDayIndex?.text = "第 ${idx + 1} 天"
        }
    }

    private fun clearAllDaySections() {
        daySections.forEach { section ->
            section.binding?.let { binding.containerDays.removeView(it.root) }
        }
        daySections.clear()
    }

    /**
     * 新增一个训练日分组卡
     * @param prefillTags 预选中的部位标签（编辑回填 / 旧名称解析结果）
     */
    private fun addDaySection(
        prefillTags: List<String> = emptyList(),
        prefillRest: Boolean = false,
        dayIndex: Int = -1
    ): DaySection {
        val dayBinding = ItemPlanDayEditBinding.inflate(
            LayoutInflater.from(this), binding.containerDays, false
        )

        val nextIndex = if (dayIndex > 0) dayIndex else daySections.size + 1
        val section = DaySection(
            originalId = 0,
            dayIndex = nextIndex,
            isRestDay = prefillRest
        )
        section.binding = dayBinding

        dayBinding.textViewDayIndex.text = "第 $nextIndex 天"
        dayBinding.switchRestDay.isChecked = section.isRestDay
        applyRestDayUi(section)

        // 部位标签 chips：预置 8 部位 + 预选中里解析不了的自定义标签
        val tagChips = buildList {
            addAll(PRESET_DAY_TAGS)
            addAll(prefillTags.filter { it !in PRESET_DAY_TAGS })
        }
        for (tag in tagChips) {
            val chip = buildTagChip(tag)
            chip.isChecked = tag in prefillTags
            if (chip.isChecked) section.tags.add(tag)
            chip.setOnCheckedChangeListener { _, checked ->
                if (checked) section.tags.add(tag) else section.tags.remove(tag)
            }
            dayBinding.chipGroupDayTags.addView(chip)
        }

        dayBinding.switchRestDay.setOnCheckedChangeListener { _, checked ->
            section.isRestDay = checked
            applyRestDayUi(section)
        }
        dayBinding.buttonRemoveDay.setOnClickListener {
            binding.containerDays.removeView(dayBinding.root)
            daySections.remove(section)
            // 重新整理后续序号
            refreshDayIndexes()
        }
        dayBinding.buttonAddItem.setOnClickListener {
            showExercisePicker(section)
        }

        binding.containerDays.addView(dayBinding.root)
        daySections.add(section)
        return section
    }

    /** 构建一个可勾选的部位标签 chip（复用统计页 Chip.Range 主题色样式） */
    private fun buildTagChip(text: String): Chip {
        val chip = layoutInflater.inflate(
            R.layout.item_tag_chip, null, false
        ) as Chip
        chip.text = text
        chip.isCheckable = true
        return chip
    }

    private fun applyRestDayUi(section: DaySection) {
        val b = section.binding ?: return
        if (section.isRestDay) {
            b.textViewDayTagsLabel.visibility = View.GONE
            b.scrollViewDayTags.visibility = View.GONE
            b.textViewDayItemsLabel.visibility = View.GONE
            b.containerDayItems.visibility = View.GONE
            b.buttonAddItem.visibility = View.GONE
        } else {
            b.textViewDayTagsLabel.visibility = View.VISIBLE
            b.scrollViewDayTags.visibility = View.VISIBLE
            b.textViewDayItemsLabel.visibility = View.VISIBLE
            b.containerDayItems.visibility = View.VISIBLE
            b.buttonAddItem.visibility = View.VISIBLE
        }
    }

    /**
     * 分类选择动作（全屏弹窗，三级钻取）：
     * 部位方卡（已选标签高亮排前）→ 子分类卡 → 动作列表卡（右侧粗加号添加），
     * 左上角「完成添加」关闭。可连续添加多个动作。
     * @param section 归属的训练日
     * @param replaceRow 非空时表示"更换该行动作"（选中后替换并关闭弹窗）；null 表示追加新行
     */
    private fun showExercisePicker(section: DaySection, replaceRow: ItemRow? = null) {
        lateinit var picker: ExercisePickerDialog
        picker = ExercisePickerDialog(
            context = this,
            bodyParts = bodyPartList,
            exercises = exerciseList,
            selectedTags = section.tags.toSet(),
            onPick = { exercise ->
                if (replaceRow != null) {
                    // 更换动作：更新名称与配图，保持组数/次数
                    replaceRow.exerciseId = exercise.id
                    replaceRow.binding.textViewItemExercise.text = exercise.name
                    replaceRow.binding.imageViewItemExercise.loadExerciseImage(exercise.imagePath)
                    picker.dismiss()
                } else {
                    addItemRow(null, section, presetName = exercise.name)
                }
            }
        )
        picker.show()
    }

    /**
     * 在某一天卡下新增一条动作卡片。
     * - 编辑回填（item != null）：按 exerciseId 查库自动填好名称/配图/组数/次数
     * - 选择器选中（presetName != null）：按名称查库填好名称/配图，组数次数默认 3×10
     * 点击卡片可打开选择器更换动作；右上 X 删除。
     */
    private fun addItemRow(
        item: TrainingPlanItem?,
        section: DaySection,
        presetName: String? = null
    ) {
        // 容器为 null 时无法 inflate（会丢失布局参数），直接跳过这一行
        val container = section.binding?.containerDayItems ?: return
        val rowBinding = ItemPlanEditBinding.inflate(
            LayoutInflater.from(this),
            container,
            false
        )

        val exercise = when {
            item != null -> exerciseList.find { it.id == item.exerciseId }
            presetName != null -> exerciseList.find { it.name == presetName }
            else -> null
        }
        val row = ItemRow(rowBinding, exercise?.id)

        // 卡片主体：名称 + 配图 + 组数/次数
        rowBinding.textViewItemExercise.text = exercise?.name ?: "选择动作"
        rowBinding.imageViewItemExercise.loadExerciseImage(exercise?.imagePath.orEmpty())
        rowBinding.editItemSets.setText(item?.sets?.toString() ?: "3")
        rowBinding.editItemReps.setText(item?.reps?.toString() ?: "10")

        // 点击卡片 → 打开选择器更换动作
        rowBinding.layoutPlanItemRow.setOnClickListener {
            showExercisePicker(section, replaceRow = row)
        }

        rowBinding.buttonRemoveItem.setOnClickListener {
            container.removeView(rowBinding.root)
            section.itemBindings.remove(row)
        }

        // 关键：把动作卡片真正挂到本日动作容器（inflate 不会自动 addView）
        container.addView(rowBinding.root)
        section.itemBindings.add(row)
    }

    /**
     * 保存计划：训练日（含休息日）+ 每天的动作条目
     */
    private fun savePlan() {
        val name = binding.editTextPlanName.text.toString().trim()
        if (name.isEmpty()) {
            Toast.makeText(this, "请输入计划名称", Toast.LENGTH_SHORT).show()
            return
        }
        if (daySections.isEmpty()) {
            Toast.makeText(this, "请先填写周期天数并生成天卡", Toast.LENGTH_SHORT).show()
            return
        }

        lifecycleScope.launch {
            val database = BrawnCatalogDatabase.getDatabase(this@PlanEditActivity)

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

            // 清空旧日/旧条目（外键级联会一并删除动作条目）
            database.trainingPlanDao().deleteItemsByPlan(savedPlanId)
            database.planDayDao().deleteDaysByPlan(savedPlanId)

            // 按当前 UI 顺序写入训练日与动作条目
            val newItems = mutableListOf<TrainingPlanItem>()
            for ((dayIdx, section) in daySections.withIndex()) {
                val index = dayIdx + 1
                val day = PlanDay(
                    planId = savedPlanId,
                    dayIndex = index,
                    // 标签拼接为日名（如 胸部+肩部）；未选标签时回落「第 N 天」/「休息日」
                    name = section.displayName().ifEmpty {
                        if (section.isRestDay) "休息日" else "第 $index 天"
                    },
                    sortOrder = index,
                    isRestDay = section.isRestDay
                )
                val newDayId = database.planDayDao().insertDay(day)
                if (section.isRestDay) continue  // 休息日不写动作条目

                section.itemBindings.forEachIndexed { idx, row ->
                    val exId = row.exerciseId ?: return@forEachIndexed
                    newItems += TrainingPlanItem(
                        planId = savedPlanId,
                        exerciseId = exId,
                        sets = row.binding.editItemSets.text.toString().toIntOrNull() ?: 3,
                        reps = row.binding.editItemReps.text.toString().toIntOrNull() ?: 10,
                        sortOrder = idx,
                        dayId = newDayId
                    )
                }
            }
            if (newItems.isNotEmpty()) database.trainingPlanDao().insertItems(newItems)

            Toast.makeText(this@PlanEditActivity, "计划已保存", Toast.LENGTH_SHORT).show()
            finish()
        }
    }
}
