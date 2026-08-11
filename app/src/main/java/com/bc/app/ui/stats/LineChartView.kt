package com.bc.app.ui.stats

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.util.AttributeSet
import android.util.TypedValue
import android.view.View
import kotlin.math.max

/**
 * 简单折线图 View
 * 手绘实现，不依赖第三方图表库
 * 用于展示动作重量/次数/1RM 随训练日期的变化趋势
 * 颜色从主题读取（colorPrimary 线条 + 主题感知的网格/文字色），暗黑模式自动适配。
 */
class LineChartView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    data class Point(val label: String, val value: Float)

    private var points: List<Point> = emptyList()

    // 画笔（颜色从主题初始化）
    private var lineColor: Int = Color.rgb(27, 79, 191)
    private var textColor: Int = Color.argb(180, 0, 0, 0)
    private var gridColor: Int = Color.argb(40, 0, 0, 0)

    private val linePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 4f * resources.displayMetrics.density
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
    }
    private val pointPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
    }
    private val gridPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 1f * resources.displayMetrics.density
    }
    private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textSize = 11f * resources.displayMetrics.density
        textAlign = Paint.Align.CENTER
    }

    init {
        // 从主题读取颜色，适配亮/暗模式
        readThemeColors()
    }

    /**
     * 从当前主题解析图表颜色：
     * 线条用 colorPrimary，文字/网格根据背景亮度自动选深色或浅色。
     */
    private fun readThemeColors() {
        val tv = TypedValue()
        val theme = context.theme
        if (theme.resolveAttribute(androidx.appcompat.R.attr.colorPrimary, tv, true)) {
            lineColor = tv.data
        }
        // 背景底色（windowBackground）判断亮暗，决定文字/网格颜色
        var isDark = false
        if (theme.resolveAttribute(android.R.attr.colorBackground, tv, true)) {
            val bc = tv.data
            isDark = Color.luminance(bc) < 0.5f
        }
        textColor = if (isDark) Color.argb(200, 230, 230, 230) else Color.argb(180, 0, 0, 0)
        gridColor = if (isDark) Color.argb(45, 255, 255, 255) else Color.argb(40, 0, 0, 0)
        applyColors()
    }

    private fun applyColors() {
        linePaint.color = lineColor
        pointPaint.color = lineColor
        gridPaint.color = gridColor
        textPaint.color = textColor
    }

    /**
     * 设置数据点
     */
    fun setData(data: List<Point>) {
        this.points = data
        invalidate()
    }

    /**
     * 手动指定颜色（可选，默认读主题）
     */
    fun setColors(line: Int, grid: Int, text: Int) {
        lineColor = line
        gridColor = grid
        textColor = text
        applyColors()
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        if (points.isEmpty()) return

        // 图表绘制区域（预留边距）
        val padding = 16f * resources.displayMetrics.density
        val labelSpace = 28f * resources.displayMetrics.density
        val left = padding
        val right = width - padding
        val top = padding
        val bottom = height - labelSpace

        val chartWidth = right - left
        val chartHeight = bottom - top

        // 计算 Y 轴范围
        val maxValue = points.maxByOrNull { it.value }?.value ?: 1f
        val minValue = points.minByOrNull { it.value }?.value ?: 0f
        val range = max(1f, maxValue - minValue)
        val yPadding = range * 0.15f
        val yTop = maxValue + yPadding
        val yBottom = (minValue - yPadding).coerceAtLeast(0f)
        val yRange = max(1f, yTop - yBottom)

        // 绘制水平网格线（3条）
        for (i in 0..3) {
            val y = top + chartHeight * i / 3f
            canvas.drawLine(left, y, right, y, gridPaint)
            // 标注数值
            val value = yTop - yRange * i / 3f
            textPaint.textAlign = Paint.Align.LEFT
            canvas.drawText(String.format("%.0f", value), left, y - 4f * resources.displayMetrics.density, textPaint)
        }

        // 计算每个点的位置
        val n = points.size
        val positions = points.mapIndexed { index, p ->
            val x = if (n == 1) left + chartWidth / 2f else left + chartWidth * index / (n - 1f)
            val y = top + chartHeight * (yTop - p.value) / yRange
            x to y
        }

        // 绘制折线
        if (n > 1) {
            val path = Path()
            path.moveTo(positions[0].first, positions[0].second)
            for (i in 1 until n) {
                path.lineTo(positions[i].first, positions[i].second)
            }
            canvas.drawPath(path, linePaint)
        }

        // 绘制数据点
        val pointRadius = 6f * resources.displayMetrics.density
        positions.forEach { (x, y) ->
            canvas.drawCircle(x, y, pointRadius, pointPaint)
        }

        // 绘制 X 轴标签（首、中、尾）
        textPaint.textAlign = Paint.Align.CENTER
        if (n <= 4) {
            positions.forEachIndexed { i, (x, y) ->
                canvas.drawText(points[i].label, x, bottom + 14f * resources.displayMetrics.density, textPaint)
            }
        } else {
            canvas.drawText(points.first().label, positions.first().first, bottom + 14f * resources.displayMetrics.density, textPaint)
            canvas.drawText(points.last().label, positions.last().first, bottom + 14f * resources.displayMetrics.density, textPaint)
        }
    }
}
