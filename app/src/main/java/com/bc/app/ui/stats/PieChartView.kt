package com.bc.app.ui.stats

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.util.AttributeSet
import android.util.TypedValue
import android.view.View
import kotlin.math.min

/**
 * 手绘圆环图 / 扇形图 View
 * 不依赖第三方图表库，用于展示各部位训练次数占比
 * 颜色从主题读取（描边/文字主题感知），扇区配色使用内置高对比度调色板，暗黑模式自动适配。
 */
class PieChartView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    data class Slice(val label: String, val value: Float)

    private var slices: List<Slice> = emptyList()
    private var centerText: String = ""

    // 主题感知颜色
    private var textColor: Int = Color.argb(180, 0, 0, 0)
    private var bgRingColor: Int = Color.argb(30, 0, 0, 0)

    // 内置高对比度扇区调色板
    private val palette = intArrayOf(
        Color.rgb(22, 93, 255),   // 主蓝
        Color.rgb(255, 112, 67),  // 橙
        Color.rgb(52, 199, 123),  // 绿
        Color.rgb(255, 179, 64),  // 金黄
        Color.rgb(175, 82, 222),  // 紫
        Color.rgb(0, 181, 204),   // 青
        Color.rgb(255, 71, 87),   // 红
        Color.rgb(88, 86, 214)    // 靛
    )

    private val slicePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
    }
    private val ringPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
    }
    private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textAlign = Paint.Align.CENTER
    }

    init {
        readThemeColors()
    }

    private fun readThemeColors() {
        val tv = TypedValue()
        val theme = context.theme
        var isDark = false
        if (theme.resolveAttribute(android.R.attr.colorBackground, tv, true)) {
            isDark = Color.luminance(tv.data) < 0.5f
        }
        textColor = if (isDark) Color.argb(220, 235, 235, 235) else Color.argb(200, 0, 0, 0)
        bgRingColor = if (isDark) Color.argb(45, 255, 255, 255) else Color.argb(28, 0, 0, 0)
    }

    /**
     * 设置扇区数据与中心文字（如总次数）
     */
    fun setData(data: List<Slice>, centerText: String) {
        this.slices = data.filter { it.value > 0f }
        this.centerText = centerText
        invalidate()
    }

    /**
     * 清空数据
     */
    fun clear() {
        slices = emptyList()
        centerText = ""
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val total = slices.sumOf { it.value.toDouble() }.toFloat()
        if (total <= 0f || slices.isEmpty()) {
            drawEmpty(canvas)
            return
        }

        // 圆环区域：使用较短边，右侧预留图例空间；整体缩小 0.9 避免遮挡
        val legendWidth = 110f * resources.displayMetrics.density
        val chartSize = ((min(width - legendWidth, height.toFloat()) - 8f * resources.displayMetrics.density) * 0.9f)
            .coerceAtLeast(10f)
        val centerX = chartSize / 2f
        val centerY = height / 2f
        val radius = chartSize * 0.42f
        val donutThickness = radius * 0.45f

        // 背景圆环（空心占位）
        ringPaint.style = Paint.Style.STROKE
        ringPaint.strokeWidth = donutThickness
        ringPaint.color = bgRingColor
        val bgRect = RectF(centerX - radius, centerY - radius, centerX + radius, centerY + radius)
        canvas.drawArc(bgRect, 0f, 360f, false, ringPaint)

        // 绘制各扇区（从 -90° 开始）
        var startAngle = -90f
        val gap = 2f
        for ((index, slice) in slices.withIndex()) {
            val sweep = 360f * slice.value / total
            slicePaint.color = palette[index % palette.size]
            val drawSweep = if (sweep < 359f) sweep - gap else sweep
            canvas.drawArc(bgRect, startAngle + gap / 2f, drawSweep.coerceAtLeast(0.5f), false, ringPaint.apply {
                style = Paint.Style.STROKE
                strokeWidth = donutThickness
                color = slicePaint.color
            })
            startAngle += sweep
        }

        // 中心文字（总体训练次数）
        textPaint.color = textColor
        textPaint.textSize = 30f * resources.displayMetrics.density
        textPaint.textAlign = Paint.Align.CENTER
        canvas.drawText(centerText, centerX, centerY - 2f * resources.displayMetrics.density, textPaint)

        textPaint.textSize = 11f * resources.displayMetrics.density
        canvas.drawText("训练次数", centerX, centerY + 18f * resources.displayMetrics.density, textPaint)

        // 右侧图例：色块 + 名称 + 次数
        val legendX = chartSize + 12f * resources.displayMetrics.density
        drawLegend(canvas, legendX, centerY)
    }

    private fun drawLegend(canvas: Canvas, startX: Float, centerY: Float) {
        val swatch = 12f * resources.displayMetrics.density
        val lineHeight = 22f * resources.displayMetrics.density
        val totalLines = slices.size
        // 图例垂直居中排布
        var y = centerY - (totalLines - 1) * lineHeight / 2f

        textPaint.textSize = 11f * resources.displayMetrics.density
        textPaint.textAlign = Paint.Align.LEFT
        for ((index, slice) in slices.withIndex()) {
            slicePaint.color = palette[index % palette.size]
            // 色块
            canvas.drawRoundRect(startX, y - swatch / 2f, startX + swatch, y + swatch / 2f, 3f, 3f, slicePaint)
            // 名称 + 次数
            textPaint.color = textColor
            val label = slice.label
            val value = slice.value.toInt().toString()
            canvas.drawText(label, startX + swatch + 6f * resources.displayMetrics.density, y + 4f * resources.displayMetrics.density, textPaint)
            canvas.drawText(value, width - 20f * resources.displayMetrics.density, y + 4f * resources.displayMetrics.density, textPaint)
            y += lineHeight
        }
    }

    private fun drawEmpty(canvas: Canvas) {
        textPaint.color = textColor
        textPaint.textSize = 13f * resources.displayMetrics.density
        textPaint.textAlign = Paint.Align.CENTER
        canvas.drawText("暂无数据", width / 2f, height / 2f, textPaint)
    }
}
