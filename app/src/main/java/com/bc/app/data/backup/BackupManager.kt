package com.bc.app.data.backup

import com.bc.app.data.db.BrawnCatalogDatabase
import com.google.gson.Gson
import com.google.gson.GsonBuilder
import com.google.gson.JsonParseException
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream

/**
 * 数据备份管理器
 * 负责将数据库全部数据导出为 JSON，以及从 JSON 恢复数据
 */
object BackupManager {

    private val gson: Gson = GsonBuilder().setPrettyPrinting().create()

    /**
     * 导出所有数据到输出流
     * @param database 数据库实例
     * @param output 目标输出流
     * @return 导出的备份数据（供调用方展示统计信息）
     */
    @Throws(IOException::class)
    suspend fun export(database: BrawnCatalogDatabase, output: OutputStream): BackupData {
        val bodyParts = database.bodyPartDao().getAllSync()
        val exercises = database.exerciseDao().getAllSyncSafe()
        val trainingRecords = database.trainingRecordDao().getAllSyncSafe()
        val trainingPlans = database.trainingPlanDao().getAllPlansSync()
        // 收集所有计划条目
        val trainingPlanItems = mutableListOf<com.bc.app.data.entity.TrainingPlanItem>()
        for (plan in trainingPlans) {
            trainingPlanItems.addAll(database.trainingPlanDao().getItemsByPlanSync(plan.id))
        }
        val bodyMetrics = database.bodyMetricDao().getAllSync()

        val backup = BackupData(
            bodyParts = bodyParts,
            exercises = exercises,
            trainingRecords = trainingRecords,
            trainingPlans = trainingPlans,
            trainingPlanItems = trainingPlanItems,
            bodyMetrics = bodyMetrics
        )

        val json = gson.toJson(backup)
        output.use { it.write(json.toByteArray(Charsets.UTF_8)) }
        return backup
    }

    /**
     * 从输入流导入数据
     * 会清空现有数据并写入备份数据
     * @param database 数据库实例
     * @param input 备份文件输入流
     * @return 恢复的备份数据
     */
    @Throws(IOException::class, JsonParseException::class)
    suspend fun import(database: BrawnCatalogDatabase, input: InputStream): BackupData {
        val json = input.use { it.readBytes().toString(Charsets.UTF_8) }
        val backup = gson.fromJson(json, BackupData::class.java)
            ?: throw JsonParseException("备份文件解析失败：内容为空")

        // 清空现有数据
        database.clearAll()

        // 批量写入备份数据。使用 REPLACE 策略，保留原有 id，保证外键关系完整
        if (backup.bodyParts.isNotEmpty()) {
            database.bodyPartDao().insertAll(backup.bodyParts)
        }
        if (backup.exercises.isNotEmpty()) {
            database.exerciseDao().insertAll(backup.exercises)
        }
        if (backup.trainingRecords.isNotEmpty()) {
            database.trainingRecordDao().insertAll(backup.trainingRecords)
        }
        if (backup.trainingPlans.isNotEmpty()) {
            backup.trainingPlans.forEach { database.trainingPlanDao().insertPlan(it) }
        }
        if (backup.trainingPlanItems.isNotEmpty()) {
            database.trainingPlanDao().insertItems(backup.trainingPlanItems)
        }
        if (backup.bodyMetrics.isNotEmpty()) {
            backup.bodyMetrics.forEach { database.bodyMetricDao().insert(it) }
        }

        return backup
    }
}
