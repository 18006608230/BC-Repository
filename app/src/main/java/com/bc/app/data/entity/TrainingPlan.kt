package com.bc.app.data.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * 训练计划实体类
 * 对应数据库表：training_plans
 */
@Entity(tableName = "training_plans")
data class TrainingPlan(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val name: String,              // 计划名称，如「推日」「周一胸」
    val description: String = "",  // 计划说明
    val isActive: Boolean = false, // 是否为当前启用计划
    val createTime: Long = System.currentTimeMillis()
)

/**
 * 训练计划条目实体类
 * 一条计划包含多个动作条目
 * 对应数据库表：training_plan_items
 */
@Entity(
    tableName = "training_plan_items",
    foreignKeys = [
        androidx.room.ForeignKey(
            entity = TrainingPlan::class,
            parentColumns = ["id"],
            childColumns = ["planId"],
            onDelete = androidx.room.ForeignKey.CASCADE
        ),
        androidx.room.ForeignKey(
            entity = Exercise::class,
            parentColumns = ["id"],
            childColumns = ["exerciseId"],
            onDelete = androidx.room.ForeignKey.CASCADE
        )
    ],
    indices = [
        androidx.room.Index(value = ["planId"]),
        androidx.room.Index(value = ["exerciseId"])
    ]
)
data class TrainingPlanItem(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val planId: Long,        // 所属计划
    val exerciseId: Long,    // 动作
    val sets: Int = 3,       // 目标组数
    val reps: Int = 10,      // 目标次数
    val sortOrder: Int = 0,  // 排序
    val dayId: Long? = null  // 所属训练日分组（plan_days.id），null 表示未分组
)
