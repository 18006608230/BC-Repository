package com.bc.app.data.backup

import com.bc.app.data.entity.BodyMetric
import com.bc.app.data.entity.BodyPart
import com.bc.app.data.entity.Exercise
import com.bc.app.data.entity.TrainingPlan
import com.bc.app.data.entity.TrainingPlanItem
import com.bc.app.data.entity.TrainingRecord

/**
 * 备份数据模型
 * 用于 JSON 导出/导入的数据容器
 */
data class BackupData(
    val version: Int = CURRENT_VERSION,
    val exportedAt: Long = System.currentTimeMillis(),
    val bodyParts: List<BodyPart> = emptyList(),
    val exercises: List<Exercise> = emptyList(),
    val trainingRecords: List<TrainingRecord> = emptyList(),
    val trainingPlans: List<TrainingPlan> = emptyList(),
    val trainingPlanItems: List<TrainingPlanItem> = emptyList(),
    val bodyMetrics: List<BodyMetric> = emptyList()
) {
    companion object {
        /** 3：动作实体新增数据集字段（nameEn/target/steps 等），向下兼容 v1/v2 备份 */
        const val CURRENT_VERSION = 3
    }
}
