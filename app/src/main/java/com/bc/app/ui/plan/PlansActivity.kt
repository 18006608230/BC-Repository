package com.bc.app.ui.plan

import android.content.Intent
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.bc.app.data.db.BrawnCatalogDatabase
import com.bc.app.data.entity.TrainingPlan
import com.bc.app.databinding.ActivityPlansBinding
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * 训练计划列表页
 */
class PlansActivity : AppCompatActivity() {

    private lateinit var binding: ActivityPlansBinding
    private lateinit var adapter: PlanAdapter

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityPlansBinding.inflate(layoutInflater)
        setContentView(binding.root)

        title = "训练计划"

        // 页面返回按钮
        binding.buttonBack.setOnClickListener { finish() }

        adapter = PlanAdapter(
            onClick = { plan -> openDetail(plan) },
            onLongClick = { plan -> showPlanActions(plan) }
        )
        binding.recyclerViewPlans.layoutManager = LinearLayoutManager(this)
        binding.recyclerViewPlans.adapter = adapter

        binding.fabAddPlan.setOnClickListener {
            openEdit(null)
        }

        observePlans()
    }

    /**
     * 长按计划：弹出操作菜单（设为当前 / 编辑 / 删除）
     */
    private fun showPlanActions(plan: TrainingPlan) {
        val options = mutableListOf<String>()
        if (!plan.isActive) options.add("设为当前使用")
        options.add("编辑")
        options.add("删除")

        android.app.AlertDialog.Builder(this)
            .setTitle(plan.name)
            .setItems(options.toTypedArray()) { _, which ->
                when (options[which]) {
                    "设为当前使用" -> setActive(plan)
                    "编辑" -> openEdit(plan)
                    "删除" -> confirmDelete(plan)
                }
            }
            .show()
    }

    private fun setActive(plan: TrainingPlan) {
        lifecycleScope.launch {
            val database = BrawnCatalogDatabase.getDatabase(this@PlansActivity)
            withContext(Dispatchers.IO) {
                database.trainingPlanDao().clearActive()
                database.trainingPlanDao().setActive(plan.id)
            }
            android.widget.Toast.makeText(this@PlansActivity, "已设为当前使用计划", android.widget.Toast.LENGTH_SHORT).show()
        }
    }

    private fun confirmDelete(plan: TrainingPlan) {
        android.app.AlertDialog.Builder(this)
            .setTitle("删除计划")
            .setMessage("确定删除「${plan.name}」吗？此操作不可撤销。")
            .setPositiveButton("删除") { _, _ ->
                lifecycleScope.launch {
                    val database = BrawnCatalogDatabase.getDatabase(this@PlansActivity)
                    withContext(Dispatchers.IO) {
                        database.trainingPlanDao().deletePlan(plan)
                    }
                }
            }
            .setNegativeButton("取消", null)
            .show()
    }

    private fun openDetail(plan: TrainingPlan) {
        val intent = Intent(this, PlanDetailActivity::class.java)
        intent.putExtra(PlanDetailActivity.EXTRA_PLAN_ID, plan.id)
        startActivity(intent)
    }

    private fun observePlans() {
        val database = BrawnCatalogDatabase.getDatabase(this)
        lifecycleScope.launch {
            database.trainingPlanDao().getAllPlans().collectLatest { plans ->
                refresh(plans, database)
            }
        }
    }

    private suspend fun refresh(plans: List<TrainingPlan>, database: BrawnCatalogDatabase) {
        val countMap = HashMap<Long, Int>()
        for (plan in plans) {
            countMap[plan.id] = database.trainingPlanDao().getItemsByPlanSync(plan.id).size
        }
        adapter.itemCountMap = countMap
        adapter.submitList(plans)
    }

    /**
     * 从详情页返回时，动作数量可能已变化（复制/编辑），重新同步计数
     */
    override fun onResume() {
        super.onResume()
        val database = BrawnCatalogDatabase.getDatabase(this)
        lifecycleScope.launch {
            val plans = database.trainingPlanDao().getAllPlansSync()
            refresh(plans, database)
        }
    }

    private fun openEdit(plan: TrainingPlan?) {
        val intent = Intent(this, PlanEditActivity::class.java)
        if (plan != null) {
            intent.putExtra(PlanEditActivity.EXTRA_PLAN_ID, plan.id)
        }
        startActivity(intent)
    }
}
