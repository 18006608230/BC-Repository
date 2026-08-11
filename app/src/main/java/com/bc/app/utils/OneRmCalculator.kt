package com.bc.app.utils

import com.bc.app.data.entity.SetDetail
import com.bc.app.data.entity.TrainingRecord
import kotlin.math.roundToInt

/**
 * 1RM（一次最大重量）估算工具
 * 采用 Epley 公式（更常用）与 Brzycki 公式（次数<=10时更准）
 *
 * Epley:    1RM = weight * (1 + reps / 30)
 * Brzycki:  1RM = weight * (36 / (37 - reps))
 */
object OneRmCalculator {

    /**
     * 根据单组重量和次数估算 1RM（Epley 公式）
     */
    fun epley(weight: Float, reps: Int): Float {
        if (weight <= 0 || reps <= 0) return 0f
        // 1次就是自身重量
        if (reps == 1) return weight
        return weight * (1f + reps / 30f)
    }

    /**
     * 根据单组重量和次数估算 1RM（Brzycki 公式，适合 reps <= 10）
     */
    fun brzycki(weight: Float, reps: Int): Float {
        if (weight <= 0 || reps <= 0) return 0f
        if (reps == 1) return weight
        if (reps >= 36) return weight // 公式退化保护
        return weight * (36f / (37f - reps))
    }

    /**
     * 使用 Epley 公式（默认推荐）
     */
    fun estimate(weight: Float, reps: Int): Float = epley(weight, reps)

    /**
     * 计算一组逐组明细里的最大 1RM 估算
     */
    fun maxFromSetDetails(details: List<SetDetail>): Float {
        return details.mapNotNull { d ->
            if (d.weight > 0 && d.reps > 0) epley(d.weight, d.reps) else null
        }.maxOrNull() ?: 0f
    }

    /**
     * 计算一条记录的 1RM 估算：
     * 优先使用专业模式的 setDetails 逐组计算最大 1RM；
     * 否则用主重量 weight 和 avgReps 估算。
     */
    fun fromRecord(record: TrainingRecord): Float {
        val details = record.setDetails?.let { JsonUtils.jsonToSetDetails(it) }
        if (!details.isNullOrEmpty()) {
            val max = maxFromSetDetails(details)
            if (max > 0) return max
        }
        return estimate(record.weight, record.avgReps)
    }

    /**
     * 计算单次容量的逐组明细总和（总容量 kg = Σ weight * reps）
     */
    fun totalVolumeFromSetDetails(details: List<SetDetail>): Float {
        return details.fold(0f) { acc, d -> acc + d.weight * d.reps }
    }

    /**
     * 判断是否打破历史记录（重量或 1RM），返回 PR 类型集合
     * 当尚无历史记录（old 为 null）时视为首次即 PR。
     */
    fun checkPr(
        newRecordWeight: Float,
        newOneRm: Float,
        oldMaxWeight: Float?,
        oldMaxOneRm: Float?
    ): Set<PrType> {
        val result = mutableSetOf<PrType>()
        // 首次记录（无历史最大重量）或超过历史最大重量 → 重量 PR
        if (oldMaxWeight == null || newRecordWeight > oldMaxWeight) {
            result.add(PrType.WEIGHT)
        }
        // 首次记录（无历史最大 1RM）或超过历史最大 1RM → 1RM PR
        if (oldMaxOneRm == null || newOneRm > oldMaxOneRm) {
            result.add(PrType.ONE_RM)
        }
        return result
    }

    /**
     * 格式化 1RM 数值（保留1位小数）
     */
    fun format(value: Float): String {
        if (value <= 0) return "-"
        return "${(value * 10).roundToInt() / 10f} kg"
    }

    /**
     * PR 类型枚举
     */
    enum class PrType {
        WEIGHT,  // 打破最大重量
        ONE_RM;  // 打破最大 1RM

        fun label(): String = when (this) {
            WEIGHT -> "新最大重量"
            ONE_RM -> "新最大 1RM"
        }
    }
}
