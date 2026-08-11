package com.bc.app.ui.exercise

import android.content.Intent
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.GridLayoutManager
import com.bc.app.data.entity.Exercise
import com.bc.app.databinding.ActivityCardioSubcategoryBinding

/**
 * 有氧子分类页
 * 点击有氧板块后进入，展示 6 张有氧子分类卡片，点选后进入对应动作列表
 */
class CardioSubcategoryActivity : AppCompatActivity() {

    companion object {
        const val EXTRA_BODY_PART_ID = "extra_body_part_id"
        const val EXTRA_BODY_PART_NAME = "extra_body_part_name"
    }

    private lateinit var binding: ActivityCardioSubcategoryBinding

    private var bodyPartId: Long = -1
    private var bodyPartName: String = "有氧"

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityCardioSubcategoryBinding.inflate(layoutInflater)
        setContentView(binding.root)

        bodyPartId = intent.getLongExtra(EXTRA_BODY_PART_ID, -1)
        bodyPartName = intent.getStringExtra(EXTRA_BODY_PART_NAME) ?: "有氧"

        binding.textViewScreenTitle.text = bodyPartName
        binding.textViewSubtitle.text = "选择有氧训练类型"

        // 页面返回按钮
        binding.buttonBack.setOnClickListener { finish() }

        val items = Exercise.CARDIO_SUBCATEGORIES.map { sub ->
            sub to Exercise.cardioSubcategoryDesc(sub)
        }

        binding.recyclerViewCardioSubs.apply {
            layoutManager = GridLayoutManager(this@CardioSubcategoryActivity, 2)
            adapter = CardioSubcategoryAdapter(items) { subName ->
                val intent = Intent(this@CardioSubcategoryActivity, ExerciseListActivity::class.java).apply {
                    putExtra(ExerciseListActivity.EXTRA_BODY_PART_ID, bodyPartId)
                    putExtra(ExerciseListActivity.EXTRA_BODY_PART_NAME, subName)
                    putExtra(ExerciseListActivity.EXTRA_SUBCATEGORY, subName)
                }
                startActivity(intent)
            }
        }
    }
}
