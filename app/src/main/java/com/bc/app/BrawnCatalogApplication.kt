package com.bc.app

import android.app.Application
import android.util.Log
import com.bc.app.data.db.BrawnCatalogDatabase
import com.bc.app.data.entity.BodyPart
import com.bc.app.data.entity.Exercise
import com.bc.app.data.entity.PlanDay
import com.bc.app.data.entity.TrainingPlan
import com.bc.app.data.entity.TrainingPlanItem
import com.bc.app.utils.SettingsUtils
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/**
 * 应用入口类
 * 负责初始化数据库和预置数据
 */
class BrawnCatalogApplication : Application() {

    companion object {
        private const val TAG = "BrawnCatalogApp"
        private const val PREFS_NAME = "brawn_catalog_prefs"
        private const val KEY_INITIALIZED = "initialized"
    }

    override fun onCreate() {
        super.onCreate()
        
        // 应用用户偏好主题（暗黑模式）
        SettingsUtils.applyTheme(this, SettingsUtils.isDarkMode(this))
        
        // 初始化预置数据（仅首次启动）
        initializeData()
    }

    /**
     * 初始化预置数据
     * 仅在首次启动时执行
     */
    private fun initializeData() {
        val prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE)

        if (!prefs.getBoolean(KEY_INITIALIZED, false)) {
            CoroutineScope(Dispatchers.IO).launch {
                try {
                    val database = BrawnCatalogDatabase.getDatabase(this@BrawnCatalogApplication)

                    // 插入预置部位
                    val bodyParts = BodyPart.getPredefinedParts()
                    val bodyPartIds = database.bodyPartDao().insertAll(bodyParts)

                    // 插入预置动作（使用实际插入后的id）
                    val predefinedExercises = Exercise.getPredefinedExercises()
                    val exercisesWithCorrectIds = predefinedExercises.mapIndexed { index, exercise ->
                        val bodyPartIndex = bodyParts.indexOfFirst { it.sortOrder == exercise.bodyPartId.toInt() }
                        val actualBodyPartId = if (bodyPartIndex != -1) {
                            bodyPartIds[bodyPartIndex]
                        } else {
                            exercise.bodyPartId
                        }
                        exercise.copy(bodyPartId = actualBodyPartId, isCustom = false)
                    }

                    database.exerciseDao().insertAll(exercisesWithCorrectIds)

                    prefs.edit().putBoolean(KEY_INITIALIZED, true).apply()
                    Log.d(TAG, "预置数据初始化完成: ${bodyParts.size}个部位, ${exercisesWithCorrectIds.size}个动作")
                } catch (e: Exception) {
                    Log.e(TAG, "预置数据初始化失败", e)
                }
            }
        } else {
            // 已初始化：增量补齐内置动作库（按名称去重，避免重复插入）
            seedMissingExercises(prefs)
        }
    }

    /**
     * 增量补齐内置动作库：已初始化的老用户也能获得新增的预置动作
     * 1) 确保 6 个标准部位存在（按 sortOrder 去重）
     * 2) 按动作名称去重，已存在的（无论是否自定义）不重复插入。
     */
    private fun seedMissingExercises(prefs: android.content.SharedPreferences) {
        val seedVersion = 5 // 内置动作库版本（v5：新增预设计划模板，仅当计划为空时创建）
        val KEY_SEED_VERSION = "seed_exercise_version"
        if (prefs.getInt(KEY_SEED_VERSION, 0) >= seedVersion) return

        CoroutineScope(Dispatchers.IO).launch {
            try {
                val database = BrawnCatalogDatabase.getDatabase(this@BrawnCatalogApplication)

                // 0) 若数据库被清空（版本升级 destructive 迁移），先补全预置部位
                if (database.bodyPartDao().getCount() == 0) {
                    val parts = BodyPart.getPredefinedParts()
                    database.bodyPartDao().insertAll(parts)
                    Log.d(TAG, "检测到空库，已重新插入 ${parts.size} 个预置部位")
                }

                // 1) 确保标准部位存在，返回 sortOrder -> 真实id 映射
                val dbParts = database.bodyPartDao().getAllSync()
                val idBySortOrder = dbParts.associateBy { it.sortOrder }.toMutableMap()
                val predefinedParts = BodyPart.getPredefinedParts()
                for (p in predefinedParts) {
                    if (idBySortOrder[p.sortOrder] == null) {
                        val newId = database.bodyPartDao().insert(p)
                        idBySortOrder[p.sortOrder] = p.copy(id = newId)
                    }
                }

                // 2) 补齐缺失动作（按名称去重；保留用户自定义动作）
                val existingNames = database.exerciseDao().getAllSyncSafe().map { it.name }.toHashSet()
                val toInsert = Exercise.getPredefinedExercises().mapNotNull { exercise ->
                    if (exercise.name in existingNames) return@mapNotNull null
                    val realId = idBySortOrder[exercise.bodyPartId.toInt()]?.id
                    if (realId == null || realId <= 0) return@mapNotNull null
                    exercise.copy(bodyPartId = realId, isCustom = false)
                }

                if (toInsert.isNotEmpty()) {
                    database.exerciseDao().insertAll(toInsert)
                    Log.d(TAG, "增量补齐内置动作库: 新增 ${toInsert.size} 个动作")
                }

                // 3) 若当前没有任何训练计划，则预置经典模板（阶段三：预设计划模板）
                if (database.trainingPlanDao().getAllPlansSync().isEmpty()) {
                    seedPresetTemplates(database)
                }

                prefs.edit().putInt(KEY_SEED_VERSION, seedVersion).apply()
            } catch (e: Exception) {
                Log.e(TAG, "增量补齐内置动作库失败", e)
            }
        }
    }

    /**
     * 预置经典训练计划模板（阶段三：预设计划模板）
     * 仅在计划列表为空时创建，带训练日分组。
     * 模板动作按名称匹配内置动作库，匹配不到则跳过该条。
     */
    private suspend fun seedPresetTemplates(database: BrawnCatalogDatabase) {
        try {
            val exercises = database.exerciseDao().getAllSyncSafe()
            val byName = exercises.associateBy { it.name }

            // 模板定义：计划名 -> 训练日列表（日名 -> 动作名列表）
            val templates = listOf(
                "推拉腿分化" to listOf(
                    "推日" to listOf("杠铃卧推", "上斜哑铃卧推", "哑铃肩推", "侧平举"),
                    "拉日" to listOf("俯身杠铃划船", "高位下拉", "杠铃弯举"),
                    "腿日" to listOf("杠铃深蹲", "腿举", "站姿提踵")
                ),
                "五分化训练" to listOf(
                    "胸日" to listOf("杠铃卧推", "上斜杠铃卧推", "哑铃飞鸟"),
                    "背日" to listOf("引体向上", "俯身杠铃划船", "直臂下压"),
                    "肩日" to listOf("杠铃肩推", "侧平举", "俯身飞鸟"),
                    "手臂日" to listOf("杠铃弯举", "绳索下压", "锤式弯举"),
                    "腿日" to listOf("杠铃深蹲", "罗马尼亚硬拉", "腿举")
                )
            )

            for ((planName, days) in templates) {
                val plan = TrainingPlan(name = planName, description = "预置模板，可编辑或复制后使用", isActive = false)
                val planId = database.trainingPlanDao().insertPlan(plan)

                var sortOrder = 0
                var dayIndex = 1
                for ((dayName, exerciseNames) in days) {
                    val dayId = database.planDayDao().insertDay(
                        com.bc.app.data.entity.PlanDay(
                            planId = planId,
                            dayIndex = dayIndex,
                            name = dayName,
                            sortOrder = dayIndex
                        )
                    )
                    dayIndex++
                    var itemSort = 0
                    for (exName in exerciseNames) {
                        val ex = byName[exName] ?: continue
                        database.trainingPlanDao().insertItem(
                            com.bc.app.data.entity.TrainingPlanItem(
                                planId = planId,
                                exerciseId = ex.id,
                                sets = 4,
                                reps = 10,
                                sortOrder = sortOrder++,
                                dayId = dayId
                            )
                        )
                        itemSort++
                    }
                }
            }
            Log.d(TAG, "预置计划模板完成: ${templates.size} 个计划")
        } catch (e: Exception) {
            Log.e(TAG, "预置计划模板失败", e)
        }
    }
}
