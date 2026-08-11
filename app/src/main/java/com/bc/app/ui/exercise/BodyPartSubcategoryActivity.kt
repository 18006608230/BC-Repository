package com.bc.app.ui.exercise

import android.content.Intent
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.GridLayoutManager
import com.bc.app.data.entity.Exercise
import com.bc.app.databinding.ActivityCardioSubcategoryBinding

/**
 * 部位子分类页（二级卡片）
 * 点击任一训练部位（胸/肩/背/手臂/腿/核心/有氧）后进入，
 * 以卡片网格展示该部位的子分类（杠铃/哑铃/徒手/器械；核心徒手/器械；有氧 6 类），
 * 点选子分类卡后进入该子分类下的动作列表。
 */
class BodyPartSubcategoryActivity : AppCompatActivity() {

    companion object {
        const val EXTRA_BODY_PART_ID = "extra_body_part_id"
        const val EXTRA_BODY_PART_NAME = "extra_body_part_name"
    }

    private lateinit var binding: ActivityCardioSubcategoryBinding

    private var bodyPartId: Long = -1
    private var bodyPartName: String = "训练"

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityCardioSubcategoryBinding.inflate(layoutInflater)
        setContentView(binding.root)

        bodyPartId = intent.getLongExtra(EXTRA_BODY_PART_ID, -1)
        bodyPartName = intent.getStringExtra(EXTRA_BODY_PART_NAME) ?: "训练"

        binding.textViewScreenTitle.text = bodyPartName
        binding.textViewSubtitle.text = buildSubtitle()

        // 页面返回按钮
        binding.buttonBack.setOnClickListener { finish() }

        val subs = Exercise.subcategoriesForBodyPart(bodyPartName)
        val items = subs.map { sub -> sub to Exercise.subcategoryDescForBodyPart(bodyPartName, sub) }

        binding.recyclerViewCardioSubs.apply {
            layoutManager = GridLayoutManager(this@BodyPartSubcategoryActivity, 2)
            adapter = CardioSubcategoryAdapter(items) { subName ->
                val intent = Intent(this@BodyPartSubcategoryActivity, ExerciseListActivity::class.java).apply {
                    putExtra(ExerciseListActivity.EXTRA_BODY_PART_ID, bodyPartId)
                    putExtra(ExerciseListActivity.EXTRA_BODY_PART_NAME, subName)
                    putExtra(ExerciseListActivity.EXTRA_SUBCATEGORY, subName)
                }
                startActivity(intent)
            }
        }
    }

    private fun buildSubtitle(): String = when (bodyPartName) {
        "有氧" -> "选择有氧训练类型"
        "核心" -> "选择核心训练方式"
        else -> "选择训练器械类型"
    }
}
