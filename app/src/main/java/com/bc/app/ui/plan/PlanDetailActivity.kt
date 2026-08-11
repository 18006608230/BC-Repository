package com.bc.app.ui.plan

import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.bc.app.data.db.BrawnCatalogDatabase
import com.bc.app.data.entity.TrainingPlan
import com.bc.app.data.entity.TrainingPlanItem
import com.bc.app.databinding.ActivityPlanDetailBinding
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * 计划详情页
 * 展示训练日分组、动作清单；支持设为当前、复制、编辑
 */
class PlanDetailActivity : AppCompatActivity() {

    companion object {
        const val EXTRA_PLAN_ID = "extra_plan_id"
    }

    private lateinit var binding: ActivityPlanDetailBinding
    private lateinit var adapter: PlanDayAdapter

    private var planId: Long = -1

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityPlanDetailBinding.inflate(layoutInflater)
        setContentView(binding.root)
        title = "计划详情"

        // 页面返回按钮
        binding.buttonBack.setOnClickListener { finish() }

        planId = intent.getLongExtra(EXTRA_PLAN_ID, -1)

        adapter = PlanDayAdapter(emptyMap())
        binding.recyclerViewDays.layoutManager = LinearLayoutManager(this)
        binding.recyclerViewDays.adapter = adapter

        binding.buttonSetActive.setOnClickListener { setActive() }
        binding.buttonCopyPlan.setOnClickListener { copyPlan() }
        binding.buttonEditPlan.setOnClickListener { editPlan() }
        binding.buttonSyncCalendar.setOnClickListener { syncCalendar() }

        loadPlan()
    }

    private fun loadPlan() {
        lifecycleScope.launch {
            val database = BrawnCatalogDatabase.getDatabase(this@PlanDetailActivity)
            val plan = withContext(Dispatchers.IO) {
                database.trainingPlanDao().getPlanById(planId)
            } ?: run {
                Toast.makeText(this@PlanDetailActivity, "计划不存在", Toast.LENGTH_SHORT).show()
                finish()
                return@launch
            }

            binding.textViewPlanName.text = plan.name
            binding.textViewPlanDescription.text = plan.description.ifEmpty { "暂无说明" }

            val days = withContext(Dispatchers.IO) {
                database.planDayDao().getDaysByPlanSync(planId)
            }
            val items = withContext(Dispatchers.IO) {
                database.trainingPlanDao().getItemsByPlanSync(planId)
            }
            val exercises = withContext(Dispatchers.IO) {
                database.exerciseDao().getAllSyncSafe().associateBy { it.id }
            }

            binding.textViewPlanMeta.text =
                if (days.isNotEmpty()) "${days.size} 个训练日 · ${items.size} 个动作"
                else "${items.size} 个动作"

            // 刷新适配器（更换 exerciseMap）
            adapter = PlanDayAdapter(exercises)
            binding.recyclerViewDays.adapter = adapter
            adapter.setData(days, items)

            // 右上角按钮：当前使用中（主蓝填充）→ 不可再点；设为当前（描边）→ 可点
            binding.buttonSetActive.isEnabled = !plan.isActive
            binding.buttonSetActive.text = if (plan.isActive) "✓ 当前使用中" else "设为当前"
            binding.buttonSetActive.backgroundTintList =
                android.content.res.ColorStateList.valueOf(
                    if (plan.isActive) getColor(com.bc.app.R.color.bc_primary)
                    else android.graphics.Color.TRANSPARENT
                )
            binding.buttonSetActive.setTextColor(
                if (plan.isActive) android.graphics.Color.WHITE
                else getColor(com.bc.app.R.color.bc_primary)
            )
        }
    }

    private fun setActive() {
        lifecycleScope.launch {
            val database = BrawnCatalogDatabase.getDatabase(this@PlanDetailActivity)
            withContext(Dispatchers.IO) {
                database.trainingPlanDao().clearActive()
                database.trainingPlanDao().setActive(planId)
            }
            Toast.makeText(this@PlanDetailActivity, "已设为当前使用计划", Toast.LENGTH_SHORT).show()
            loadPlan()
        }
    }

    private fun copyPlan() {
        lifecycleScope.launch {
            val database = BrawnCatalogDatabase.getDatabase(this@PlanDetailActivity)
            val source = withContext(Dispatchers.IO) {
                database.trainingPlanDao().getPlanById(planId)
            } ?: return@launch

            withContext(Dispatchers.IO) {
                val newPlan = TrainingPlan(
                    name = source.name + "（副本）",
                    description = source.description,
                    isActive = false
                )
                val newPlanId = database.trainingPlanDao().insertPlan(newPlan)

                // 复制训练日
                val srcDays = database.planDayDao().getDaysByPlanSync(source.id)
                val dayIdMap = HashMap<Long, Long>()
                for (d in srcDays) {
                    val newDayId = database.planDayDao().insertDay(
                        d.copy(id = 0, planId = newPlanId)
                    )
                    dayIdMap[d.id] = newDayId
                }

                // 复制条目（保留 dayId 映射）
                val srcItems = database.trainingPlanDao().getItemsByPlanSync(source.id)
                val newItems = srcItems.map { item ->
                    item.copy(
                        id = 0,
                        planId = newPlanId,
                        dayId = item.dayId?.let { dayIdMap[it] }
                    )
                }
                database.trainingPlanDao().insertItems(newItems)
            }

            Toast.makeText(this@PlanDetailActivity, "计划已复制", Toast.LENGTH_SHORT).show()
            loadPlan()
        }
    }

    private fun editPlan() {
        val intent = Intent(this, PlanEditActivity::class.java)
        intent.putExtra(PlanEditActivity.EXTRA_PLAN_ID, planId)
        startActivity(intent)
    }

    /**
     * 同步到日历：加载训练日列表并弹出配置对话框
     */
    private fun syncCalendar() {
        lifecycleScope.launch {
            val database = BrawnCatalogDatabase.getDatabase(this@PlanDetailActivity)
            val days = withContext(Dispatchers.IO) {
                database.planDayDao().getDaysByPlanSync(planId)
            }
            ScheduleSyncHelper.show(
                context = this@PlanDetailActivity,
                planId = planId,
                planName = binding.textViewPlanName.text.toString().ifEmpty { "计划" },
                days = days
            ) {
                Toast.makeText(this@PlanDetailActivity, "已同步到日历", Toast.LENGTH_SHORT).show()
            }
        }
    }

    override fun onResume() {
        super.onResume()
        if (planId > 0) loadPlan()
    }
}
