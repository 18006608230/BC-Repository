package com.bc.app.data.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * 计划↔日历排课实体
 * 对应数据库表：workout_schedules
 * 把某个训练计划（可选具体训练日）排到具体的日期上，日历据此展示当天该练什么
 */
@Entity(
    tableName = "workout_schedules",
    foreignKeys = [
        ForeignKey(
            entity = TrainingPlan::class,
            parentColumns = ["id"],
            childColumns = ["planId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index(value = ["date"]), Index(value = ["planId"])]
)
data class WorkoutSchedule(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val date: String,          // yyyy-MM-dd
    val planId: Long,          // 所属计划
    val dayId: Long? = null,   // 可空：null=整计划（全部训练日），非空=仅该训练日
    val dayName: String? = null, // 训练日名称缓存（列表展示用）
    val isCompleted: Boolean = false // 当天是否已完成
)
