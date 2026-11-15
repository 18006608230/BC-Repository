package com.bc.app.ui.stats

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.util.AttributeSet
import android.util.TypedValue
import android.view.View
import com.bc.app.R

/**
 * 每月训练天数柱状图（热力图页面的下半部分）
 *
 * 与热力图互补：热力图看「每天」，这里看「每月汇总」。
 * 颜色同样走主题：柱子用 `?attr/bcPrimary`，空月份用 `?attr/bcBorder`，
 * 当前月份加粗高亮，换主题自动跟随。
 */
class MonthBarsView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    companion object {
        private const val MONTHS = 12
        private const val BAR_AREA_DP = 78f
        private const val VALUE_SPACE_DP = 18f
        private const val LABEL_SPACE_DP = 18f
    }

    /** 每月训练天数，下标 0 = 1 月 */
    private var values = IntArray(MONTHS)
    /** 需要高亮的月份下标（0..11），-1 表示不高亮 */
    private var highlight = -1

    private val density get() = resources.displayMetrics.density

    private var primary = Color.rgb(0x16, 0x5D, 0xFF)
    private var emptyColor = Color.rgb(0xE5, 0xE8, 0xEF)
    private var textColor = Color.GRAY
    private var mutedColor = Color.LTGRAY

    private val barPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textAlign = Paint.Align.CENTER
        isFakeBoldText = true
    }
    private val valuePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { textAlign = Paint.Align.CENTER }

    init {
        readThemeColors()
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        readThemeColors()
    }

    private fun readThemeColors() {
        val tv = TypedValue()
        val theme = context.theme
        if (theme.resolveAttribute(R.attr.bcPrimary, tv, true)) primary = tv.data
        if (theme.resolveAttribute(R.attr.bcBorder, tv, true)) emptyColor = tv.data
        if (theme.resolveAttribute(R.attr.bcTextSecondary, tv, true)) textColor = tv.data
        mutedColor = textColor
        textPaint.color = textColor
        valuePaint.color = textColor
    }

    fun setData(values: IntArray, highlightMonth: Int = -1) {
        this.values = if (values.size == MONTHS) values else IntArray(MONTHS).also {
            for (i in 0 until minOf(values.size, MONTHS)) it[i] = values[i]
        }
        this.highlight = highlightMonth
        invalidate()
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val w = MeasureSpec.getSize(widthMeasureSpec)
        val h = when (MeasureSpec.getMode(heightMeasureSpec)) {
            MeasureSpec.EXACTLY -> MeasureSpec.getSize(heightMeasureSpec)
            else -> ((VALUE_SPACE_DP + BAR_AREA_DP + LABEL_SPACE_DP) * density).toInt()
        }
        setMeasuredDimension(w, h)
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)

        val valueSpace = VALUE_SPACE_DP * density
        val labelSpace = LABEL_SPACE_DP * density
        val barArea = height - valueSpace - labelSpace
        val gap = 4f * density
        val barW = (width - (MONTHS - 1) * gap) / MONTHS
        val radius = barW * 0.34f

        val max = values.maxOrNull() ?: 0
        val scale = if (max > 0) barArea / max.toFloat() else 0f

        textPaint.textSize = 10f * density
        valuePaint.textSize = 9.5f * density

        for (i in 0 until MONTHS) {
            val v = values[i]
            val x = i * (barW + gap)
            val barH = if (v > 0) (v * scale).coerceAtLeast(3f * density) else 3f * density
            val top = valueSpace + barArea - barH
            val rect = RectF(x, top, x + barW, valueSpace + barArea)

            barPaint.color = if (v > 0) primary else emptyColor
            barPaint.alpha = if (i == highlight) 255 else if (v > 0) 210 else 255
            canvas.drawRoundRect(rect, radius, radius, barPaint)
            barPaint.alpha = 255

            // 柱顶数值
            if (v > 0) {
                canvas.drawText(v.toString(), x + barW / 2f, top - 4f * density, valuePaint)
            }
            // 月份标签
            canvas.drawText("${i + 1}", x + barW / 2f, height - 5f * density, textPaint)
        }
    }
}
