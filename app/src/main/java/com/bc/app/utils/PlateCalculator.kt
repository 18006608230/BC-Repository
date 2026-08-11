package com.bc.app.utils

/**
 * 杠铃片计算器
 * 根据目标重量、杠铃杆重量，计算每侧需要搭配的标准杠铃片。
 */
object PlateCalculator {

    // 标准杠铃片规格（kg），从大到小
    val STANDARD_PLATES = listOf(25f, 20f, 15f, 10f, 5f, 2.5f, 1.25f, 0.5f, 0.25f)

    data class PlateResult(
        val platesPerSide: List<Float>, // 每侧所需杠铃片（从大到小）
        val totalPlates: List<Float>,   // 双侧合计（展示用）
        val achievedWeight: Float,      // 实际可达到的重量（杆+双侧片）
        val targetWeight: Float,        // 目标重量
        val isExact: Boolean           // 是否精确匹配
    )

    /**
     * 计算杠铃片配比（贪心算法，优先用大片）
     * @param targetWeight 目标总重量(kg)
     * @param barWeight 杠铃杆重量(kg)，默认 20kg 标准杆
     */
    fun calculate(targetWeight: Float, barWeight: Float = 20f): PlateResult? {
        if (targetWeight <= 0) return null
        if (targetWeight < barWeight) {
            // 低于杆重，无法配片
            return PlateResult(emptyList(), emptyList(), barWeight, targetWeight, false)
        }

        // 每侧需要加载的重量
        var remainingPerSide = (targetWeight - barWeight) / 2f
        // 四舍五入到 0.25kg 精度
        remainingPerSide = kotlin.math.round(remainingPerSide * 4f) / 4f

        val perSide = mutableListOf<Float>()
        var placed = 0f
        for (plate in STANDARD_PLATES) {
            while (remainingPerSide - placed >= plate - 0.001f) {
                perSide.add(plate)
                placed += plate
            }
        }

        val achieved = barWeight + placed * 2f
        return PlateResult(
            platesPerSide = perSide,
            totalPlates = perSide.flatMap { listOf(it, it) },
            achievedWeight = achieved,
            targetWeight = targetWeight,
            isExact = Math.abs(achieved - targetWeight) < 0.001f
        )
    }
}
