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
        // 预置部位数据（胸部~核心 + 有氧 + 休息）
        // 「颈部」已下线：没有预置动作，也不参与推拉腿与周覆盖统计，
        // 留着只会多一项永远欠练的提示。老库里的残留由 BrawnCatalogApplication 一次性清理。
        // 「休息」接替 sortOrder 8：点击直接记一次休息，不进子分类页。
        fun getPredefinedParts(): List<BodyPart> = listOf(
            BodyPart(name = "胸部", sortOrder = 1),
            BodyPart(name = "背部", sortOrder = 2),
            BodyPart(name = "肩部", sortOrder = 3),
            BodyPart(name = "手臂", sortOrder = 4),
            BodyPart(name = "腿部", sortOrder = 5),
            BodyPart(name = "核心", sortOrder = 6),
            BodyPart(name = "有氧", sortOrder = 7),
            BodyPart(name = "休息", sortOrder = 8)
        )

        /** 部位卡片副标题（覆盖肌群说明） */
        fun partDesc(name: String): String = when (name) {
            "胸部" -> "胸大肌 / 胸小肌 / 前锯肌"
            "背部" -> "背阔肌 / 斜方肌 / 菱形肌 / 竖脊肌"
            "肩部" -> "三角肌前中后束 / 肩袖"
            "手臂" -> "肱二头 / 肱三头 / 前臂"
            "腿部" -> "股四头 / 股二头 / 臀 / 小腿"
            "核心" -> "腹肌 / 腹斜肌 / 下背"
            "有氧" -> "心肺耐力与热量消耗"
            "休息" -> "主动恢复日，点一下记一次"
            else -> ""
        }
    }
}
