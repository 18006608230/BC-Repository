package com.bc.app.data.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * 计划训练日分组实体
 * 对应数据库表：plan_days
 * 一个计划包含多个训练日（如 第1天 胸+三头、第2天 背+二头）
 */
@Entity(
    tableName = "plan_days",
    foreignKeys = [
        ForeignKey(
            entity = TrainingPlan::class,
            parentColumns = ["id"],
            childColumns = ["planId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index(value = ["planId"])]
)
data class PlanDay(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val planId: Long,          // 所属计划
    val dayIndex: Int,         // 第几天（1-based）
    val name: String,          // 训练日名称，如「第1天 胸+三头」
    val sortOrder: Int = 0     // 排序
)
