package com.bc.app.data.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * 训练记录实体类
 * 对应数据库表：training_records
 */
@Entity(
    tableName = "training_records",
    foreignKeys = [
        ForeignKey(
            entity = Exercise::class,
            parentColumns = ["id"],
            childColumns = ["exerciseId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index(value = ["exerciseId"])]
)
data class TrainingRecord(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val exerciseId: Long,
    val date: Long, // 训练日期时间戳
    val weight: Float, // 主训练重量(kg)
    val sets: Int, // 总组数
    val avgReps: Int, // 平均每组次数
    val remark: String = "", // 简短训练备注
    // 专业颗粒度字段
    val setDetails: String? = null, // 逐组明细，JSON格式字符串
    val rpe: Int? = null, // 整体训练自觉费力程度（1-10）
    val restSeconds: Int? = null, // 组间休息秒数
    val setType: Int = 0, // 组类型枚举：0普通组、1递减组、2超级组
    val variant: String? = null, // 动作变式，如宽握/窄握/正手/反手
    val tags: String? = null, // 训练标签，JSON数组字符串，如 ["推日","状态差"]
    val createTime: Long = System.currentTimeMillis()
) {
    /**
     * 组类型枚举
     */
    object SetType {
        const val NORMAL = 0
        const val DROP_SET = 1
        const val SUPER_SET = 2
    }

    /**
     * 判断是否为专业颗粒度记录
     */
    fun isProfessionalRecord(): Boolean {
        return !setDetails.isNullOrBlank() || rpe != null || restSeconds != null || setType != SetType.NORMAL
    }
}

/**
 * 逐组明细数据类
 * 用于JSON序列化/反序列化
 */
data class SetDetail(
    val setNumber: Int, // 组序号
    val reps: Int, // 该组完成次数
    val weight: Float, // 该组使用重量
    val rpe: Int? = null, // 该组RPE
    val restSeconds: Int? = null, // 该组休息秒数
    val completed: Boolean = true, // 是否完成
    val note: String? = null // 该组备注
)
