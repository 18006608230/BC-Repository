package com.bc.app.data.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * 身体数据记录实体类
 * 对应数据库表：body_metrics
 * 记录体重、体脂率等身体指标
 */
@Entity(tableName = "body_metrics")
data class BodyMetric(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val date: Long,             // 测量日期时间戳
    val weight: Float? = null,  // 体重 (kg)
    val bodyFat: Float? = null, // 体脂率 (%)
    val muscle: Float? = null,  // 肌肉量 (kg)
    val waist: Float? = null,   // 腰围 (cm)
    val remark: String = ""    // 备注
)
