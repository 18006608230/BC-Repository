package com.bc.app.ui.stats

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.util.AttributeSet
import android.util.TypedValue
import android.view.MotionEvent
import android.view.View
import com.bc.app.R
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

/**
 * 训练日历热力图（GitHub 贡献图风格）
 *
 * 设计要点：
 *  - 53 周 × 7 天，周**一**为每周第一天（与 App 内其它地方保持一致）；
 *  - 圆角格子，格子颜色由「当天训练量」分 4 档；
 *  - **颜色全部从主题实时计算**：把 `?attr/bcPrimary` 按 25% / 50% / 75% / 100%
 *    混入卡片背景色，因此换到 7 套主题中任意一套都会自动变成该主题的深浅色阶，
 *    不需要为每套主题单独配一组颜色；
 *  - 今天之后的格子不绘制（避免出现无意义的「未来空格」）。
 */
class TrainingHeatmapView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    companion object {
        private const val WEEKS = 53

        /**
         * 格子边长（dp）。**固定值，不随屏幕宽度压缩** ——
         * 53 周若硬塞进手机宽度，格子只剩 2dp，根本看不清。
         * 这里保持舒适尺寸，由外层 HorizontalScrollView 横向滚动，
         * 既保住完整一年，又不用眯着眼看。
         */
        private const val CELL_DP = 13f

        /** 强度分档阈值：当天完成的动作数 */
        private const val L1_MAX = 2
        private const val L2_MAX = 4
        private const val L3_MAX = 6
    }

    /** key = yyyy-MM-dd，value = 当天完成的动作数（**不含休息**，休息单独用 restDays 表示） */
    private var countByDay: Map<String, Int> = emptyMap()

    /** 当天只休息、没练的日期集合 */
    private var restDays: Set<String> = emptySet()

    /** 网格最后一天（含），默认今天 */
    private var endCal: Calendar = Calendar.getInstance()

    private val dayKeyFmt = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())

    /** 单元格点击回调：(日期 key, 当天动作数（不含休息）, 是否休息) */
    var onCellClick: ((String, Int, Boolean) -> Unit)? = null

    private val density get() = resources.displayMetrics.density

    // ---- 主题色（在 init / attach 时读取，换肤后 recreate 会重新走一遍）----
    private var primary = Color.rgb(0x16, 0x5D, 0xFF)
    private var cardBg = Color.WHITE
    private var emptyColor = Color.rgb(0xE5, 0xE8, 0xEF)
    private var textColor = Color.GRAY
    private var todayStroke = Color.TRANSPARENT

    private val cellPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val strokePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 1.5f * resources.displayMetrics.density
    }
    private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textAlign = Paint.Align.LEFT
        isFakeBoldText = true
    }

    /** 格子矩形，供点击命中测试复用 */
    private val cellRects = ArrayList<CellRect>()
    private data class CellRect(
        val rect: RectF,
        val dayKey: String,
        val count: Int,
        val isRest: Boolean
    )

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
        if (theme.resolveAttribute(R.attr.bcCardBg, tv, true)) cardBg = tv.data
        if (theme.resolveAttribute(R.attr.bcBorder, tv, true)) emptyColor = tv.data
        if (theme.resolveAttribute(R.attr.bcTextSecondary, tv, true)) textColor = tv.data
        todayStroke = primary
        textPaint.color = textColor
        strokePaint.color = todayStroke
    }

    /**
     * @param countByDay 日期 → 当天训练量（动作数，**不含休息**）
     * @param restDays 当天记了休息的日期（休息没有组数重量，不按动作数计强度）
     * @param end 网格最后一天，默认今天
     */
    fun setData(
        countByDay: Map<String, Int>,
        restDays: Set<String> = emptySet(),
        end: Calendar = Calendar.getInstance()
    ) {
        this.countByDay = countByDay
        this.restDays = restDays
        this.endCal = end
        invalidate()
    }

    // ------------------------------------------------------------------
    // 尺寸：wrap_content 时按宽度反推高度
    // ------------------------------------------------------------------

    /**
     * 宽度按「完整 53 周 × 固定格子」算出一个比屏幕更宽的自然宽度，
     * 放在 HorizontalScrollView 里横向滚动；高度固定。
     */
    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val desiredW = (weekdayLabelWidth() + WEEKS * (cellSize() + gap()) + gap()).toInt()
        val w = when (MeasureSpec.getMode(widthMeasureSpec)) {
            MeasureSpec.EXACTLY -> MeasureSpec.getSize(widthMeasureSpec)
            else -> desiredW
        }
        val h = when (MeasureSpec.getMode(heightMeasureSpec)) {
            MeasureSpec.EXACTLY -> MeasureSpec.getSize(heightMeasureSpec)
            else -> (topLabelSpace() + gridHeight(cellSize()) + legendSpace()).toInt()
        }
        setMeasuredDimension(w, h)
    }

    private fun cellSize(): Float = CELL_DP * density

    private fun gap() = 3f * density
    private fun topLabelSpace() = 20f * density
    /** 底部留白（图例已去掉，这里只为了防止最后一个格子被裁掉） */
    private fun legendSpace() = 6f * density
    private fun weekdayLabelWidth() = 20f * density
    private fun gridHeight(cell: Float) = 7 * cell + 6 * gap()

    // ------------------------------------------------------------------
    // 绘制
    // ------------------------------------------------------------------

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)

        val cell = cellSize()
        val gap = gap()
        val left = weekdayLabelWidth()
        val top = topLabelSpace()

        textPaint.textSize = 9.5f * density
        textPaint.color = textColor

        // 网格起点：结束日期所在周的周一，再往前推 WEEKS-1 周
        val start = Calendar.getInstance().apply {
            timeInMillis = endCal.timeInMillis
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
            set(Calendar.DAY_OF_WEEK, Calendar.MONDAY)
            add(Calendar.WEEK_OF_YEAR, -(WEEKS - 1))
        }
        val todayKey = dayKeyFmt.format(Date(System.currentTimeMillis()))

        cellRects.clear()

        // --- 单元格 ---
        val cursor = Calendar.getInstance().apply { timeInMillis = start.timeInMillis }
        var lastMonthDrawn = -1
        for (col in 0 until WEEKS) {
            val monthOfCol = cursor.get(Calendar.MONTH)
            // 月份标签：与上一列不同月时，在这一列上方标一次
            if (monthOfCol != lastMonthDrawn) {
                val x = left + col * (cell + gap)
                canvas.drawText("${monthOfCol + 1}月", x, top - 6f * density, textPaint)
                lastMonthDrawn = monthOfCol
            }

            for (row in 0..6) {
                val dayKey = dayKeyFmt.format(cursor.time)
                // 今天之后不画
                if (dayKey <= todayKey) {
                    val count = countByDay[dayKey] ?: 0
                    val isRest = dayKey in restDays
                    val x = left + col * (cell + gap)
                    val y = top + row * (cell + gap)
                    val rect = RectF(x, y, x + cell, y + cell)

                    cellPaint.color = levelColor(levelOf(count, isRest))
                    canvas.drawRoundRect(rect, cell * 0.28f, cell * 0.28f, cellPaint)

                    // 今天描个边，方便定位
                    if (dayKey == todayKey) {
                        canvas.drawRoundRect(rect, cell * 0.28f, cell * 0.28f, strokePaint)
                    }

                    cellRects.add(CellRect(rect, dayKey, count, isRest))
                }
                cursor.add(Calendar.DAY_OF_YEAR, 1)
            }
        }

        // --- 星期标签（一 / 三 / 五）---
        val weekdayLabels = mapOf(0 to "一", 2 to "三", 4 to "五")
        weekdayLabels.forEach { (row, label) ->
            val y = top + row * (cell + gap) + cell
            canvas.drawText(label, 0f, y - cell * 0.15f, textPaint)
        }

        // 底部不再画图例（那块小字在手机上又挤又难读），只留一点下边距
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (event.action == MotionEvent.ACTION_UP) {
            val hit = cellRects.firstOrNull {
                event.x >= it.rect.left - gap() / 2 && event.x <= it.rect.right + gap() / 2 &&
                    event.y >= it.rect.top - gap() / 2 && event.y <= it.rect.bottom + gap() / 2
            }
            if (hit != null) {
                onCellClick?.invoke(hit.dayKey, hit.count, hit.isRest)
                return true
            }
        }
        return super.onTouchEvent(event)
    }

    // ------------------------------------------------------------------

    /**
     * 强度档位。休息日单独给 1 档：
     * 休息没有组数重量，不该按「动作数」算强度，但也不能显示为完全没练（否则日历上看不见）。
     */
    private fun levelOf(count: Int, isRest: Boolean): Int = when {
        count <= 0 -> if (isRest) 1 else 0
        count <= L1_MAX -> 1
        count <= L2_MAX -> 2
        count <= L3_MAX -> 3
        else -> 4
    }

    /** 把主题主色按档位混入卡片背景，自动适配任意主题 */
    private fun levelColor(level: Int): Int =
        if (level <= 0) emptyColor else blend(cardBg, primary, level / 4f)

    private fun blend(from: Int, to: Int, ratio: Float): Int {
        val r = Color.red(from) + (Color.red(to) - Color.red(from)) * ratio
        val g = Color.green(from) + (Color.green(to) - Color.green(from)) * ratio
        val b = Color.blue(from) + (Color.blue(to) - Color.blue(from)) * ratio
        return Color.argb(255, r.toInt(), g.toInt(), b.toInt())
    }
}
