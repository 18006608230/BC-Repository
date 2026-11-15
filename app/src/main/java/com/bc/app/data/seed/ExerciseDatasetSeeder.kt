package com.bc.app.data.seed

import android.content.Context
import android.util.Log
import com.bc.app.data.db.BrawnCatalogDatabase
import com.bc.app.data.entity.BodyPart
import com.bc.app.data.entity.Exercise
import com.google.gson.stream.JsonReader
import com.google.gson.stream.JsonToken
import java.io.InputStreamReader

/**
 * 扩展动作库播种器
 *
 * 数据来源：exercises-dataset（https://github.com/hasaneyldrm/exercises-dataset）
 * 仅使用其**文字部分**（动作名 / 分类 / 肌群 / 中文分步说明），该部分以 MIT 许可发布。
 *
 * 配图：数据集内的图片与 GIF 版权归 **Gym visual**（https://gymvisual.com/）所有。
 * 本项目已按用户“仅个人、非商用”的用途，将 GIF 配图下载至 assets/exercise_images/
 * （字段 img / imagePath 指向文件名，如 "0001-2gPfomN.gif"），离线展示。
 * 若用于任何形式的分发/商用，请先获得 Gym visual 授权或替换为自有素材。
 *
 * 资产文件：assets/exercise_dataset.json（1285 条，约 600 KB）
 * 字段缩写：n=中文名 e=英文原名 b=部位sortOrder s=子分类
 *          tg=目标肌群 sm=次要肌群 st=中文分步说明 note=补充说明 img=GIF文件名
 *
 * 采用 Gson 流式解析 + 分批入库，避免一次性构建千级对象造成内存峰值，
 * 也避免把 1285 条硬编码进 Kotlin 触发单方法 64KB 字节码上限。
 */
object ExerciseDatasetSeeder {

    private const val TAG = "ExerciseSeeder"
    private const val ASSET_NAME = "exercise_dataset.json"
    private const val BATCH_SIZE = 250

    /** 数据集动作的排序权重（大于 0 即为扩展库，排在精选之后） */
    private const val DATASET_SORT_WEIGHT = 1

    /**
     * 把资产里的动作导入数据库。
     *
     * 去重/覆盖规则：数据库中已存在同名动作时，用数据集的丰富字段覆盖该记录
     * （保留其 id 与 isCustom，置顶权重置 0 维持“精选”位置），使原预置动作
     * 与数据集显示一致；用户自定义动作（isCustom=1）不被覆盖。
     *
     * @return 实际新增条数
     */
    suspend fun seed(context: Context, database: BrawnCatalogDatabase): Int {
        // 1) 部位 sortOrder -> 真实 id
        val partIdBySort = resolveBodyPartIds(database)
        if (partIdBySort.isEmpty()) {
            Log.w(TAG, "部位表为空，跳过扩展动作库导入")
            return 0
        }

        // 2) 已有动作名（去重基准）
        val existingNames = HashSet(database.exerciseDao().getAllNames())

        var inserted = 0
        var overridden = 0
        val batch = ArrayList<Exercise>(BATCH_SIZE)

        context.assets.open(ASSET_NAME).use { input ->
            JsonReader(InputStreamReader(input, Charsets.UTF_8)).use { reader ->
                reader.beginArray()
                while (reader.hasNext()) {
                    val item = readItem(reader) ?: continue
                    val realPartId = partIdBySort[item.bodyPartSort] ?: continue

                    if (existingNames.add(item.name)) {
                        // 新动作：直接入库（扩展库，置底）
                        batch += item.toEntity(realPartId)
                        if (batch.size >= BATCH_SIZE) {
                            database.exerciseDao().insertAll(batch)
                            inserted += batch.size
                            batch.clear()
                        }
                    } else {
                        // 同名：用数据集的丰富字段覆盖已有记录，保留 id 与 isCustom，
                        // 置顶权重 0 维持“精选”位置，使原预置动作与数据集显示一致。
                        // 用户自定义动作（isCustom=1）不被覆盖，直接跳过。
                        val existing = database.exerciseDao().getByName(item.name) ?: continue
                        if (existing.isCustom) continue
                        database.exerciseDao().update(
                            existing.copy(
                                bodyPartId = realPartId,
                                name = item.name,
                                tips = "",
                                subcategory = item.subcategory,
                                isCustom = existing.isCustom,
                                nameEn = item.nameEn,
                                target = item.target,
                                secondaryMuscles = item.secondaryMuscles,
                                steps = item.steps,
                                note = item.note,
                                imagePath = item.imagePath,
                                sortWeight = 0
                            )
                        )
                        overridden++
                    }
                }
                reader.endArray()
            }
        }

        if (batch.isNotEmpty()) {
            database.exerciseDao().insertAll(batch)
            inserted += batch.size
        }

        Log.d(TAG, "扩展动作库导入完成：新增 $inserted 条，覆盖 $overridden 条")
        return inserted
    }

    /**
     * 回填预置动作的英文名、目标肌群与配图（v8/v9 升级）。
     * 预置动作与数据集零重名，原“覆盖”逻辑不会触发，故此处单独把
     * getPredefinedExercises() 里已补全的 nameEn/target 写回老库对应的预置记录，
     * 并使原有 111 条精选与新加入的数据集动作显示风格一致（含 GIF 配图）。
     * 仅更新“非自定义”的记录。
     */
    suspend fun enrichPresets(database: BrawnCatalogDatabase) {
        val dao = database.exerciseDao()
        var updated = 0
        for (p in Exercise.getPredefinedExercises()) {
            if (p.nameEn.isBlank()) continue
            val existing = dao.getByName(p.name) ?: continue
            if (existing.isCustom) continue
            // 英文名/肌群仅在尚未写入时回填，避免覆盖用户手动编辑
            if (existing.nameEn.isBlank()) {
                dao.updatePresetInfo(existing.id, p.nameEn, p.target)
            }
            // 配图：从预置映射取 GIF 文件名写入（个人使用，图片 © Gym visual）
            val img = PresetImageMap.MAP[p.nameEn]
            if (!img.isNullOrBlank() && existing.imagePath != img) {
                dao.updateImagePath(existing.id, img)
                updated++
            }
        }
        if (updated > 0) Log.d(TAG, "预置动作配图回填完成：$updated 条")
    }

    /**
     * 确保 8 个预置部位都存在，返回 sortOrder -> 真实 id
     */
    private suspend fun resolveBodyPartIds(database: BrawnCatalogDatabase): Map<Int, Long> {
        val dao = database.bodyPartDao()
        val map = dao.getAllSync().associate { it.sortOrder to it.id }.toMutableMap()
        for (p in BodyPart.getPredefinedParts()) {
            if (map[p.sortOrder] == null) {
                val newId = dao.insert(p)
                if (newId > 0) map[p.sortOrder] = newId
            }
        }
        return map.filterValues { it > 0 }
    }

    // ---------------------------------------------------------------
    // 流式读取单条
    // ---------------------------------------------------------------

    private class RawItem(
        val name: String,
        val nameEn: String,
        val bodyPartSort: Int,
        val subcategory: String,
        val target: String,
        val secondaryMuscles: String,
        val steps: String,
        val note: String,
        val imagePath: String
    ) {
        fun toEntity(bodyPartId: Long) = Exercise(
            bodyPartId = bodyPartId,
            name = name,
            tips = "",
            subcategory = subcategory,
            isCustom = false,
            nameEn = nameEn,
            target = target,
            secondaryMuscles = secondaryMuscles,
            steps = steps,
            note = note,
            imagePath = imagePath,
            sortWeight = DATASET_SORT_WEIGHT
        )
    }

    private fun readItem(reader: JsonReader): RawItem? {
        var name = ""
        var nameEn = ""
        var bodyPartSort = 0
        var sub = ""
        var target = ""
        var secondary = ""
        var steps = ""
        var note = ""
        var imagePath = ""

        reader.beginObject()
        while (reader.hasNext()) {
            when (reader.nextName()) {
                "n" -> name = reader.nextString()
                "e" -> nameEn = reader.nextString()
                "b" -> bodyPartSort = reader.nextInt()
                "s" -> sub = reader.nextString()
                "tg" -> target = reader.nextString()
                "sm" -> secondary = reader.nextString()
                "st" -> steps = readStringArray(reader)
                "note" -> note = reader.nextString()
                "img" -> imagePath = reader.nextString()
                else -> reader.skipValue()
            }
        }
        reader.endObject()

        if (name.isBlank() || bodyPartSort <= 0) return null
        return RawItem(name, nameEn, bodyPartSort, sub, target, secondary, steps, note, imagePath)
    }

    /** 字符串数组 -> 换行拼接 */
    private fun readStringArray(reader: JsonReader): String {
        if (reader.peek() != JsonToken.BEGIN_ARRAY) {
            reader.skipValue()
            return ""
        }
        val sb = StringBuilder()
        reader.beginArray()
        while (reader.hasNext()) {
            val s = reader.nextString()
            if (s.isNotBlank()) {
                if (sb.isNotEmpty()) sb.append('\n')
                sb.append(s.trim())
            }
        }
        reader.endArray()
        return sb.toString()
    }
}
