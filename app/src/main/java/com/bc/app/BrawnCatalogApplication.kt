package com.bc.app

import android.app.Application
import android.util.Log
import coil.Coil
import coil.ImageLoader
import coil.decode.GifDecoder
import com.bc.app.data.db.BrawnCatalogDatabase
import com.bc.app.data.entity.BodyPart
import com.bc.app.data.entity.Exercise
import com.bc.app.data.entity.PlanDay
import com.bc.app.data.entity.TrainingPlan
import com.bc.app.data.entity.TrainingPlanItem
import com.bc.app.data.seed.ExerciseDatasetSeeder
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
        private const val KEY_SEED_VERSION = "seed_exercise_version"

        /** 已下线部位的清理标记（只需跑一次） */
        private const val KEY_RETIRED_PARTS_CLEANED = "retired_body_parts_cleaned"
        /** 已下线的部位名：「颈部」没有预置动作，也不参与推拉腿与周覆盖统计 */
        private const val RETIRED_PART_NECK = "颈部"

        /**
         * 内置动作库版本
         * v5：预设计划模板
         * v6：全量扩充动作库（exercises-dataset 1285 条）+ 颈部部位 + 器械子分类细分
         * v7：导入扩展动作库（同名原预置动作以数据集字段覆盖）
         * v8：为原有 111 条预置动作补全英文名/目标肌群（与数据集动作显示风格一致）；
         *     因预置动作与数据集零重名，覆盖逻辑不会触发，故新增 enrichPresets 回填
         * v9：为动作接入 GIF 配图（assets/exercise_images/，个人使用，图片 © Gym visual），
         *     新增 imagePath 字段；enrichPresets 额外回填预置动作配图
         * v10：补全此前因匹配过严漏掉的 102 条数据集动作配图（如「地面飞鸟」「胸点地俯卧撑」），
         *      改为更宽松的英文名匹配（精确 → 核心词子集 → 子串），已验证均为高置信匹配
         * v11：计划训练日新增休息日 isRestDay 字段（v9→v10 迁移）；预置新增「潭师精选三分化」模板
         *      （3 个训练日、15 个动作），按名称幂等补齐，老用户亦可见
         * v12：新增「休息」部位（sortOrder 8）。点击直接记一次休息、不进子分类页，
         *      统计口径与普通训练部位一致（周覆盖目标每周 1 次）。老库按 sortOrder 幂等补齐
         */
        private const val SEED_VERSION = 12
    }

    override fun onCreate() {
        super.onCreate()

        // 注册 GIF 解码器，使动作配图（assets 中的 GIF）正常播放动画而非只显示第一帧
        Coil.setImageLoader(
            ImageLoader.Builder(this)
                .components { add(GifDecoder.Factory()) }
                .build()
        )

        // 强制非暗黑模式资源（颜色主题已用 BaseActivity 在每个 Activity onCreate 前 setTheme() 应用，
        // 此处固定 MODE_NIGHT_NO 以防系统暗黑模式影响 values-night 资源覆盖主题色）
        androidx.appcompat.app.AppCompatDelegate.setDefaultNightMode(
            androidx.appcompat.app.AppCompatDelegate.MODE_NIGHT_NO
        )

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
                        exercise.copy(
                            bodyPartId = actualBodyPartId,
                            isCustom = false,
                            steps = exercise.tips,
                            tips = "",
                            imagePath = com.bc.app.data.seed.PresetImageMap.MAP[exercise.nameEn] ?: ""
                        )
                    }

                    database.exerciseDao().insertAll(exercisesWithCorrectIds)

                    // 导入扩展动作库（assets/exercise_dataset.json，1285 条）
                    val datasetCount = ExerciseDatasetSeeder.seed(this@BrawnCatalogApplication, database)

                    // 按名称幂等写入预置训练计划模板（首装创建全部，已存在的跳过）
                    seedPresetTemplates(database)

                    prefs.edit()
                        .putBoolean(KEY_INITIALIZED, true)
                        .putInt(KEY_SEED_VERSION, SEED_VERSION)
                        .apply()
                    Log.d(
                        TAG,
                        "预置数据初始化完成: ${bodyParts.size}个部位, " +
                            "${exercisesWithCorrectIds.size}个精选动作, $datasetCount 个扩展动作"
                    )
                } catch (e: Exception) {
                    Log.e(TAG, "预置数据初始化失败", e)
                }
            }
        } else {
            // 已初始化：增量补齐内置动作库（按名称去重，避免重复插入）
            seedMissingExercises(prefs)
        }

        // 已下线的部位：老库里还留着，一次性清掉
        removeRetiredBodyParts(prefs)
    }

    /**
     * 清理已下线的部位（一次性）。
     *
     * 「颈部」已下线：既没有预置动作，也不参与推拉腿和周覆盖统计，
     * 留着只会让部位列表多一项、统计页多一行永远欠练的提示。
     *
     * 这里删掉部位本身和挂在它下面的动作。**训练记录有意保留** ——
     * 删数据是不可逆的，而孤儿记录在统计口径里都会被
     * （`exerciseMap[record.exerciseId] ?: continue`）跳过，不影响任何展示。
     */
    private fun removeRetiredBodyParts(prefs: android.content.SharedPreferences) {
        if (prefs.getBoolean(KEY_RETIRED_PARTS_CLEANED, false)) return

        CoroutineScope(Dispatchers.IO).launch {
            try {
                val database = BrawnCatalogDatabase.getDatabase(this@BrawnCatalogApplication)
                val neck = database.bodyPartDao().getAllSync()
                    .firstOrNull { it.name == RETIRED_PART_NECK }
                if (neck == null) {
                    prefs.edit().putBoolean(KEY_RETIRED_PARTS_CLEANED, true).apply()
                    return@launch
                }

                val neckExercises = database.exerciseDao().getAllSyncSafe()
                    .filter { it.bodyPartId == neck.id }
                neckExercises.forEach { database.exerciseDao().delete(it) }
                database.bodyPartDao().delete(neck)

                Log.d(TAG, "已下线部位清理：删除「颈部」及其 ${neckExercises.size} 个动作（训练记录保留）")
                prefs.edit().putBoolean(KEY_RETIRED_PARTS_CLEANED, true).apply()
            } catch (e: Exception) {
                Log.e(TAG, "清理已下线部位失败", e)
            }
        }
    }

    /**
     * 增量补齐内置动作库：已初始化的老用户也能获得新增的预置动作
     * 1) 确保 6 个标准部位存在（按 sortOrder 去重）
     * 2) 按动作名称去重，已存在的（无论是否自定义）不重复插入。
     */
    private fun seedMissingExercises(prefs: android.content.SharedPreferences) {
        if (prefs.getInt(KEY_SEED_VERSION, 0) >= SEED_VERSION) return

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
                    exercise.copy(
                        bodyPartId = realId,
                        isCustom = false,
                        steps = exercise.tips,
                        tips = "",
                        imagePath = com.bc.app.data.seed.PresetImageMap.MAP[exercise.nameEn] ?: ""
                    )
                }

                if (toInsert.isNotEmpty()) {
                    database.exerciseDao().insertAll(toInsert)
                    Log.d(TAG, "增量补齐内置动作库: 新增 ${toInsert.size} 个动作")
                }

                // 3) 导入扩展动作库（同名的原预置动作用数据集字段覆盖）
                val datasetCount = ExerciseDatasetSeeder.seed(this@BrawnCatalogApplication, database)
                if (datasetCount > 0) {
                    Log.d(TAG, "扩展动作库补齐: 新增 $datasetCount 个动作")
                }

                // 3.5) 将仍保留旧格式（tips）的内置动作迁移为 steps，统一显示
                database.exerciseDao().migrateBuiltInTipsToSteps()

                // 3.6) 为原有预置动作补全英文名/目标肌群（与新数据集动作风格一致）
                ExerciseDatasetSeeder.enrichPresets(database)

                // 4) 按名称幂等补齐预置计划模板（首装创建全部，老用户仅补未有的）
                seedPresetTemplates(database)

                prefs.edit().putInt(KEY_SEED_VERSION, SEED_VERSION).apply()
            } catch (e: Exception) {
                Log.e(TAG, "增量补齐内置动作库失败", e)
            }
        }
    }

    /**
     * 预置经典训练计划模板（阶段三：预设计划模板）
     * 按名称幂等写入：已存在同名计划则跳过（保证老用户增量补齐时不会重复创建）。
     * 模板动作按名称匹配内置动作库（含数据集），匹配不到则跳过该条。
     */
    private suspend fun seedPresetTemplates(database: BrawnCatalogDatabase) {
        try {
            val exercises = database.exerciseDao().getAllSyncSafe()
            val byName = exercises.associateBy { it.name }

            // 模板定义：计划名 -> 训练日列表（日名 -> 动作条目列表(name, sets, reps)）
            // sets/reps 取整数简化（详细强度描述由用户在编辑页自行调整）
            val templates = listOf(
                "推拉腿分化" to listOf(
                    "推日" to listOf(
                        Triple("杠铃卧推", 4, 10),
                        Triple("上斜哑铃卧推", 4, 12),
                        Triple("哑铃肩推", 4, 10),
                        Triple("侧平举", 3, 15)
                    ),
                    "拉日" to listOf(
                        Triple("杠铃划船", 4, 10),
                        Triple("高位下拉", 4, 10),
                        Triple("杠铃弯举", 3, 12)
                    ),
                    "腿日" to listOf(
                        Triple("杠铃深蹲", 4, 8),
                        Triple("腿举", 4, 12),
                        Triple("站姿提踵", 4, 15)
                    )
                ),
                "五分化训练" to listOf(
                    "胸日" to listOf(
                        Triple("杠铃卧推", 4, 10),
                        Triple("上斜杠铃卧推", 4, 10),
                        Triple("哑铃飞鸟", 3, 12)
                    ),
                    "背日" to listOf(
                        Triple("引体向上", 4, 8),
                        Triple("杠铃划船", 4, 10),
                        Triple("直臂下压", 3, 12)
                    ),
                    "肩日" to listOf(
                        Triple("杠铃肩推", 4, 10),
                        Triple("侧平举", 4, 12),
                        Triple("俯身飞鸟", 3, 12)
                    ),
                    "手臂日" to listOf(
                        Triple("杠铃弯举", 3, 12),
                        Triple("绳索下压", 3, 12),
                        Triple("哑铃锤式弯举", 3, 12)
                    ),
                    "腿日" to listOf(
                        Triple("杠铃深蹲", 4, 8),
                        Triple("罗马尼亚硬拉", 3, 10),
                        Triple("腿举", 4, 12)
                    )
                ),
                "潭师精选三分化" to listOf(
                    "胸+三角肌中束+三头" to listOf(
                        Triple("杠铃卧推", 4, 12),
                        Triple("上斜哑铃卧推", 4, 12),
                        Triple("双杠臂屈伸（三头）", 4, 12),
                        Triple("仰卧臂屈伸", 4, 15),
                        Triple("Y字上举", 3, 10)
                    ),
                    "背+三角肌后束+二头" to listOf(
                        Triple("绳索单臂下拉", 4, 12),
                        Triple("窄握高位下拉", 4, 10),
                        Triple("器械划船", 4, 10),
                        Triple("坐姿划船", 4, 12),
                        Triple("绳索弯举", 3, 12)
                    ),
                    "臀+股四+腘绳肌" to listOf(
                        Triple("杠铃单腿硬拉", 4, 12),
                        Triple("保加利亚分腿蹲", 4, 10),
                        Triple("前蹲", 3, 15),
                        Triple("罗马尼亚硬拉", 3, 12),
                        Triple("山羊挺身", 3, 8)
                    )
                )
            )

            for ((planName, days) in templates) {
                seedOnePreset(database, planName, days, byName)
            }
            Log.d(TAG, "预置计划模板完成: ${templates.size} 个计划")
        } catch (e: Exception) {
            Log.e(TAG, "预置计划模板失败", e)
        }
    }

    /**
     * 按名称幂等写入一个预置计划：
     *  - 已存在同名计划则跳过（保证老用户增量补齐时不会重复创建）
     *  - 动作用 byName 查找，匹配不到的跳过该条目
     */
    private suspend fun seedOnePreset(
        database: BrawnCatalogDatabase,
        planName: String,
        days: List<Pair<String, List<Triple<String, Int, Int>>>>,
        byName: Map<String, com.bc.app.data.entity.Exercise>
    ) {
        val existing = database.trainingPlanDao().getAllPlansSync().any { it.name == planName }
        if (existing) {
            Log.d(TAG, "预置计划已存在，跳过: $planName")
            return
        }

        val plan = TrainingPlan(name = planName, description = "预置模板，可编辑或复制后使用", isActive = false)
        val planId = database.trainingPlanDao().insertPlan(plan)

        var dayIndex = 1
        for ((dayName, items) in days) {
            val dayId = database.planDayDao().insertDay(
                PlanDay(planId = planId, dayIndex = dayIndex, name = dayName, sortOrder = dayIndex)
            )
            dayIndex++
            var itemSort = 0
            for ((exName, sets, reps) in items) {
                val ex = byName[exName]
                if (ex == null) {
                    Log.w(TAG, "预置[$planName/$dayName] 动作「$exName」未找到，已跳过")
                    continue
                }
                database.trainingPlanDao().insertItem(
                    TrainingPlanItem(
                        planId = planId,
                        exerciseId = ex.id,
                        sets = sets,
                        reps = reps,
                        sortOrder = itemSort++,
                        dayId = dayId
                    )
                )
            }
        }
        Log.d(TAG, "预置计划已创建: $planName (${days.size} 天)")
    }
}
