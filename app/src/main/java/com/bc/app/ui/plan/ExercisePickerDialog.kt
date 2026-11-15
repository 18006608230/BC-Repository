package com.bc.app.ui.plan

import android.content.Context
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.util.TypedValue
import android.view.KeyEvent
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.widget.ImageButton
import android.widget.TextView
import android.widget.Toast
import androidx.annotation.AttrRes
import androidx.appcompat.app.AlertDialog
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.bc.app.R
import com.bc.app.data.entity.BodyPart
import com.bc.app.data.entity.Exercise
import com.bc.app.databinding.ItemPickerExerciseCardBinding
import com.bc.app.databinding.ItemPickerGridCardBinding
import com.bc.app.utils.loadExerciseImage
import com.google.android.material.button.MaterialButton

/**
 * 计划编辑页 · 添加动作选择器（全屏弹窗）
 *
 * 三级钻取，与主页「部位 → 子分类 → 动作列表」浏览路径一致：
 * 1. 部位方形卡片（天卡已选标签的部位排前并高亮描边）
 * 2. 该部位的子分类卡片（数据驱动，只显示库里真实存在的分类）
 * 3. 动作列表卡片（与主页动作卡设计一致，右侧五角星换成深色主题色粗加号，
 *    点击添加到当天，可连续添加；已添加的显示对勾）
 *
 * 顶栏左上角「完成添加」关闭弹窗；二级/三级右上角有返回上级箭头，
 * 系统返回键同样逐级返回。
 */
class ExercisePickerDialog(
    private val context: Context,
    private val bodyParts: List<BodyPart>,
    private val exercises: List<Exercise>,
    /** 当前天卡已勾选的部位标签：对应部位卡片排前 + 高亮 */
    private val selectedTags: Set<String>,
    /** 选中动作回调（可多次回调实现连续添加） */
    private val onPick: (Exercise) -> Unit
) {

    private var dialog: AlertDialog? = null

    // 当前层级：0 部位 / 1 子分类 / 2 动作列表
    private var currentLevel = 0
    private var currentPart: BodyPart? = null
    private var currentSub: String? = null

    private lateinit var recyclerParts: RecyclerView
    private lateinit var recyclerSubs: RecyclerView
    private lateinit var recyclerExercises: RecyclerView
    private lateinit var titleView: TextView
    private lateinit var buttonBack: ImageButton

    /** 部位/子分类卡的数据 */
    private data class GridItem(
        val title: String,
        val desc: String,
        val highlighted: Boolean
    )

    /** 外部主动关闭（用于"更换动作"场景，选中后立即关闭） */
    fun dismiss() {
        dialog?.dismiss()
    }

    fun show() {
        val view = LayoutInflater.from(context).inflate(R.layout.dialog_exercise_picker, null)
        val created = AlertDialog.Builder(context)
            .setView(view)
            .create()
        dialog = created

        recyclerParts = view.findViewById(R.id.recyclerPickerParts)
        recyclerSubs = view.findViewById(R.id.recyclerPickerSubs)
        recyclerExercises = view.findViewById(R.id.recyclerPickerExercises)
        titleView = view.findViewById(R.id.textViewPickerTitle)
        buttonBack = view.findViewById(R.id.buttonPickerBack)

        view.findViewById<MaterialButton>(R.id.buttonPickerDone).setOnClickListener {
            created.dismiss()
        }
        buttonBack.setOnClickListener { navigateUp() }

        // 系统返回键：逐级返回，最后一级才关闭弹窗
        created.setOnKeyListener { _, keyCode, event ->
            if (keyCode == KeyEvent.KEYCODE_BACK && event.action == KeyEvent.ACTION_UP) {
                if (currentLevel > 0) navigateUp() else created.dismiss()
                true
            } else {
                false
            }
        }

        created.show()
        created.window?.apply {
            setLayout(
                WindowManager.LayoutParams.MATCH_PARENT,
                WindowManager.LayoutParams.MATCH_PARENT
            )
            // 视图根布局自带主题窗口底色，弹窗背景置透明避免双层背景
            setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
        }

        showParts()
    }

    /** 一级：部位方卡 */
    private fun showParts() {
        currentLevel = 0
        currentPart = null
        currentSub = null
        titleView.text = "选择部位"
        buttonBack.visibility = View.GONE
        recyclerParts.visibility = View.VISIBLE
        recyclerSubs.visibility = View.GONE
        recyclerExercises.visibility = View.GONE

        // 天卡已选标签的部位排前
        val tagged = bodyParts.filter { it.name in selectedTags }
        val others = bodyParts.filter { it.name !in selectedTags }
        val ordered = tagged + others

        recyclerParts.layoutManager = GridLayoutManager(context, 3)
        recyclerParts.adapter = GridCardAdapter(
            ordered.map { part ->
                val count = exercises.count { it.bodyPartId == part.id }
                GridItem(
                    title = part.name,
                    desc = if (part.name in selectedTags) "已选 · $count 个动作" else "$count 个动作",
                    highlighted = part.name in selectedTags
                )
            }
        ) { position ->
            showSubs(ordered[position])
        }
    }

    /** 二级：该部位的子分类卡（数据驱动，只展示库里真实存在的） */
    private fun showSubs(part: BodyPart) {
        currentLevel = 1
        currentPart = part
        currentSub = null
        titleView.text = "${part.name} · 选择分类"
        buttonBack.visibility = View.VISIBLE
        recyclerParts.visibility = View.GONE
        recyclerSubs.visibility = View.VISIBLE
        recyclerExercises.visibility = View.GONE

        val partExercises = exercises.filter { it.bodyPartId == part.id }
        val subsFromDb = partExercises
            .map { it.subcategory.ifBlank { "其他" } }
            .toSet()
        val subs = if (subsFromDb.isEmpty()) {
            // 兜底：数据库未就绪时展示全量候选
            Exercise.subcategoriesForBodyPart(part.name)
        } else {
            Exercise.sortSubcategories(subsFromDb)
        }

        recyclerSubs.layoutManager = GridLayoutManager(context, 3)
        recyclerSubs.adapter = GridCardAdapter(
            subs.map { sub ->
                val count = partExercises.count {
                    it.subcategory.ifBlank { "其他" } == sub
                }
                GridItem(
                    title = sub,
                    desc = if (count > 0) "$count 个动作"
                    else Exercise.subcategoryDescForBodyPart(part.name, sub),
                    highlighted = false
                )
            }
        ) { position ->
            showExercises(part, subs[position])
        }
    }

    /** 三级：动作列表（卡片与主页一致，右侧粗加号添加） */
    private fun showExercises(part: BodyPart, sub: String) {
        currentLevel = 2
        currentSub = sub
        titleView.text = "${part.name} · $sub"
        buttonBack.visibility = View.VISIBLE
        recyclerParts.visibility = View.GONE
        recyclerSubs.visibility = View.GONE
        recyclerExercises.visibility = View.VISIBLE

        val list = exercises.filter {
            it.bodyPartId == part.id && it.subcategory.ifBlank { "其他" } == sub
        }
        recyclerExercises.layoutManager = LinearLayoutManager(context)
        recyclerExercises.adapter = ExerciseCardAdapter(list) { exercise ->
            onPick(exercise)
            Toast.makeText(context, "已添加：${exercise.name}", Toast.LENGTH_SHORT).show()
        }
    }

    private fun navigateUp() {
        when (currentLevel) {
            1 -> showParts()
            2 -> currentPart?.let { showSubs(it) }
        }
    }

    private fun resolveAttrColor(@AttrRes attr: Int): Int {
        val typedValue = TypedValue()
        context.theme.resolveAttribute(attr, typedValue, true)
        return typedValue.data
    }

    /** 部位 / 子分类方卡适配器 */
    private inner class GridCardAdapter(
        private val items: List<GridItem>,
        private val onItemClick: (Int) -> Unit
    ) : RecyclerView.Adapter<GridCardAdapter.ViewHolder>() {

        private val highlightColor = resolveAttrColor(R.attr.bcPrimaryLight)
        private val highlightStrokeWidth =
            (2 * context.resources.displayMetrics.density).toInt()

        inner class ViewHolder(
            private val binding: ItemPickerGridCardBinding
        ) : RecyclerView.ViewHolder(binding.root) {

            init {
                binding.root.setOnClickListener {
                    val position = bindingAdapterPosition
                    if (position != RecyclerView.NO_POSITION) {
                        onItemClick(position)
                    }
                }
            }

            fun bind(item: GridItem) {
                binding.textViewPickerCardTitle.text = item.title
                if (item.desc.isBlank()) {
                    binding.textViewPickerCardDesc.visibility = View.GONE
                } else {
                    binding.textViewPickerCardDesc.visibility = View.VISIBLE
                    binding.textViewPickerCardDesc.text = item.desc
                }
                // 高亮：与天卡已选标签对应的部位用浅主题色描边
                if (item.highlighted) {
                    binding.cardPickerGrid.strokeWidth = highlightStrokeWidth
                    binding.cardPickerGrid.strokeColor = highlightColor
                } else {
                    binding.cardPickerGrid.strokeWidth = 0
                }
            }
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
            val binding = ItemPickerGridCardBinding.inflate(
                LayoutInflater.from(parent.context), parent, false
            )
            return ViewHolder(binding)
        }

        override fun onBindViewHolder(holder: ViewHolder, position: Int) {
            holder.bind(items[position])
        }

        override fun getItemCount(): Int = items.size
    }

    /** 动作卡适配器：与主页动作卡一致，右侧粗加号点击添加，已添加变对勾 */
    private inner class ExerciseCardAdapter(
        private val items: List<Exercise>,
        private val onAdd: (Exercise) -> Unit
    ) : RecyclerView.Adapter<ExerciseCardAdapter.ViewHolder>() {

        private val addedIds = mutableSetOf<Long>()

        inner class ViewHolder(
            private val binding: ItemPickerExerciseCardBinding
        ) : RecyclerView.ViewHolder(binding.root) {

            init {
                binding.buttonPickerAddExercise.setOnClickListener {
                    val position = bindingAdapterPosition
                    if (position != RecyclerView.NO_POSITION) {
                        val exercise = items[position]
                        if (exercise.id in addedIds) {
                            Toast.makeText(
                                context, "「${exercise.name}」已添加过", Toast.LENGTH_SHORT
                            ).show()
                        } else {
                            addedIds.add(exercise.id)
                            onAdd(exercise)
                            notifyItemChanged(position)
                        }
                    }
                }
            }

            fun bind(exercise: Exercise) {
                binding.textViewPickerExerciseName.text = exercise.name
                if (exercise.nameEn.isNotBlank()) {
                    binding.textViewPickerExerciseNameEn.visibility = View.VISIBLE
                    binding.textViewPickerExerciseNameEn.text = exercise.nameEn
                } else {
                    binding.textViewPickerExerciseNameEn.visibility = View.GONE
                }
                binding.imageViewPickerExercise.loadExerciseImage(exercise.imagePath)
                binding.buttonPickerAddExercise.setImageResource(
                    if (exercise.id in addedIds) R.drawable.ic_check_bold
                    else R.drawable.ic_add_bold
                )
            }
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
            val binding = ItemPickerExerciseCardBinding.inflate(
                LayoutInflater.from(parent.context), parent, false
            )
            return ViewHolder(binding)
        }

        override fun onBindViewHolder(holder: ViewHolder, position: Int) {
            holder.bind(items[position])
        }

        override fun getItemCount(): Int = items.size
    }
}
