package com.bc.app.data.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * 部位实体类
 * 对应数据库表：body_parts
 */
@Entity(tableName = "body_parts")
data class BodyPart(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val name: String,
    val sortOrder: Int,
    val createTime: Long = System.currentTimeMillis()
) {
    companion object {
        // 预置部位数据（胸部~核心 + 有氧）
        fun getPredefinedParts(): List<BodyPart> = listOf(
            BodyPart(name = "胸部", sortOrder = 1),
            BodyPart(name = "背部", sortOrder = 2),
            BodyPart(name = "肩部", sortOrder = 3),
            BodyPart(name = "手臂", sortOrder = 4),
            BodyPart(name = "腿部", sortOrder = 5),
            BodyPart(name = "核心", sortOrder = 6),
            BodyPart(name = "有氧", sortOrder = 7)
        )
    }
}
