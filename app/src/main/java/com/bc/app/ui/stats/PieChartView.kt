package com.bc.app.ui.stats

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.util.AttributeSet
import android.util.TypedValue
import android.view.View
import androidx.core.content.ContextCompat
import kotlin.math.min

/**
 * 手绘圆环图 / 扇形图 View
 * 不依赖第三方图表库，用于展示各部位训练次数占比
 * 文字与描边读主题（bcPrimary / bcTextSecondary），扇区配色取自 6 个非黑主题的
 * bcPrimary / bcAccent（在 colors.xml 的 bc_chart_* 资源中），多 slice 按索引旋转。
 */
class PieChartView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    data class Slice(val label: String, val value: Float)

    private var slices: List<Slice> = emptyList()
    private var centerText: String = ""

    // 主题感知颜色（统一使用主题深色，bold）
    private var textColor: Int = Color.rgb(26, 26, 26)
    private var bgRingColor: Int = Color.argb(28, 0, 0, 0)

    // 扇区调色盘：6 个非黑主题主色 + 2 个主题辅色，全部取自 colors.xml 的 bc_chart_*
    private val paletteResIds = intArrayOf(
        com.bc.app.R.color.bc_chart_blue,            // 深蓝  primary
        com.bc.app.R.color.bc_chart_red,             // 酒红  primary
        com.bc.app.R.color.bc_chart_green,           // 橄榄绿 primary
        com.bc.app.R.color.bc_chart_orange,          // 爱马仕橙 primary
        com.bc.app.R.color.bc_chart_purple,          // 深紫  primary
        com.bc.app.R.color.bc_chart_yellow,          // 金黄  primary
        com.bc.app.R.color.bc_chart_red_accent,      // 酒红  accent
        com.bc.app.R.color.bc_chart_purple_accent    // 深紫  accent
    )

    /** 调色盘颜色（懒加载，首次绘制时解析 resId → ColorInt） */
    private var palette: IntArray = IntArray(paletteResIds.size)

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
        // 文字统一使用当前主题的 bcPrimary（深色前景）：中心训练次数、底部"训练次数"、右侧图例（部位名 + 次数）
        val tv = TypedValue()
        var primary = textColor
        if (context.theme.resolveAttribute(com.bc.app.R.attr.bcPrimary, tv, true)) {
            primary = tv.data
        }
        textColor = primary
        // 圆环底色取次主题色（hsl 0.35, 0.40 的透出版）
        if (context.theme.resolveAttribute(com.bc.app.R.attr.bcTextSecondary, tv, true)) {
            val base = tv.data
            bgRingColor = (base and 0x00FFFFFF) or 0x14000000
        }
        // 扇区调色盘：加载资源（取自 colors.xml 的 bc_chart_*）
        for (i in paletteResIds.indices) {
            palette[i] = ContextCompat.getColor(context, paletteResIds[i])
        }
        // 自适应主题变化
        invalidate()
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

        // 圆环区域：使用较短边，右侧预留图例空间，左侧留出偏移避免圆环贴左边缘被遮挡；
        // 整体缩小 0.9 避免遮挡
        val density = resources.displayMetrics.density
        val legendWidth = 110f * density
        val chartOffsetX = 16f * density   // 左侧留白：整体右移，避免贴边/被裁切
        val availWidth = width - legendWidth - chartOffsetX
        val chartSize = ((min(availWidth, height.toFloat()) - 8f * density) * 0.9f)
            .coerceAtLeast(10f)
        val centerX = chartOffsetX + chartSize / 2f
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

        // 中心文字（总体训练次数，加粗 + 主题主色）
        textPaint.color = textColor
        textPaint.textSize = 30f * resources.displayMetrics.density
        textPaint.textAlign = Paint.Align.CENTER
        textPaint.isFakeBoldText = true
        canvas.drawText(centerText, centerX, centerY - 2f * resources.displayMetrics.density, textPaint)

        textPaint.textSize = 11f * resources.displayMetrics.density
        canvas.drawText("训练次数", centerX, centerY + 18f * resources.displayMetrics.density, textPaint)

        // 右侧图例：色块 + 名称 + 次数（随圆环右移同步右移）
        val legendX = chartOffsetX + chartSize + 12f * resources.displayMetrics.density
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
            // 名称 + 次数（加粗 + 主题主色）
            textPaint.color = textColor
            textPaint.isFakeBoldText = true
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
        textPaint.isFakeBoldText = true
        canvas.drawText("暂无数据", width / 2f, height / 2f, textPaint)
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        // 进入屏幕时刷新主题颜色（防止主题切换 recreate 时属性延迟读到）
        readThemeColors()
    }
}
