package com.bc.app.ui.stats

import com.bc.app.data.entity.Exercise
import com.bc.app.data.entity.TrainingRecord
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

/**
 * 肌群周覆盖 的计算（纯逻辑，不依赖 Android 组件）
 *
 * 统计口径：某部位某天只要训练过（当天有该部位任一动作的记录）即计 1 次，
 * 按「部位 + 日期」去重。这样一次训练里做 5 个胸的动作也只算 1 次，
 * 符合「每周每个肌群练几次」的通用口径。
 *
 * 按**部位名称**归组而不是 bodyPartId：bodyPartId 是数据库自增 id，
 * 而项目里又存在把 sortOrder 当 id 用的历史写法（见 Exercise.CARDIO_BODY_PART_SORT），
 * 部位表一旦被清空重建，id 就会顺延。按名称匹配不受此影响。
 */
object MuscleBalance {

    /** 达标程度 */
    enum class Level { OK, LOW, NONE }

    /**
     * 各部位每周建议次数。
     *
     * **0 表示「不做要求」**（目前是手臂）：永远按达标处理，
     * 不会出现在欠练提示里，界面上也不显示「/ N 次」。
     */
    private val WEEKLY_TARGET = mapOf(
        "胸部" to 1,
        "背部" to 1,
        "肩部" to 1,
        "腿部" to 1,
        "核心" to 1,
        "有氧" to 1,
        "休息" to 1,
        "手臂" to 0
    )
    private const val DEFAULT_TARGET = 1

    /** 部位展示顺序（与 BodyPart 预置顺序一致；「颈部」已下线，「休息」在最后） */
    private val PART_ORDER = listOf("胸部", "背部", "肩部", "手臂", "腿部", "核心", "有氧", "休息")

    /** 单个部位的周统计 */
    data class PartStat(
        val name: String,
        /** 本周训练次数（按天去重） */
        val sessions: Int,
        /** 每周建议次数；0 = 不做要求 */
        val target: Int,
        val level: Level
    )

    data class Result(
        val parts: List<PartStat>,
        /** 本周未达标的部位（不做要求的部位不会进来） */
        val underTrained: List<String>
    )

    /** 本周起点：周一 00:00 */
    fun weekStartMillis(now: Long = System.currentTimeMillis()): Long {
        val cal = Calendar.getInstance()
        cal.timeInMillis = now
        cal.set(Calendar.HOUR_OF_DAY, 0)
        cal.set(Calendar.MINUTE, 0)
        cal.set(Calendar.SECOND, 0)
        cal.set(Calendar.MILLISECOND, 0)
        cal.set(Calendar.DAY_OF_WEEK, Calendar.MONDAY)
        return cal.timeInMillis
    }

    fun compute(
        records: List<TrainingRecord>,
        exerciseMap: Map<Long, Exercise>,
        bodyPartIdToName: Map<Long, String>,
        now: Long = System.currentTimeMillis()
    ): Result {
        val start = weekStartMillis(now)
        val dayFmt = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
        val partNames = bodyPartIdToName.values.toSet()

        // 部位名 -> 本周训练过的日期集合
        val partDays = HashMap<String, HashSet<String>>()

        for (r in records) {
            if (r.date < start) continue
            val ex = exerciseMap[r.exerciseId] ?: continue
            val partName = bodyPartIdToName[ex.bodyPartId] ?: continue
            val dayKey = dayFmt.format(Date(r.date))
            partDays.getOrPut(partName) { HashSet() }.add(dayKey)
        }

        // 先按预置顺序，再把数据库里多出来的自定义部位追加在后面
        val known = PART_ORDER.filter { partNames.contains(it) }
        val extra = partNames.filter { it !in PART_ORDER }.sorted()
        val parts = (known + extra).map { name ->
            val sessions = partDays[name]?.size ?: 0
            val target = WEEKLY_TARGET[name] ?: DEFAULT_TARGET
            val level = when {
                target <= 0 -> Level.OK // 不做要求，不参与欠练提示
                sessions >= target -> Level.OK
                sessions > 0 -> Level.LOW
                else -> Level.NONE
            }
            PartStat(name, sessions, target, level)
        }

        val underTrained = parts
            .filter { it.level != Level.OK }
            .map { it.name }

        return Result(parts, underTrained)
    }
}
