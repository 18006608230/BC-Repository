package com.bc.app.ui.motion

import android.animation.ValueAnimator
import android.content.Context
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.VelocityTracker
import android.view.ViewConfiguration
import android.view.animation.DecelerateInterpolator
import android.widget.FrameLayout
import kotlin.math.abs

/**
 * 可中断 + 惯性滑动的玻璃面板（iOS 风格）。
 *
 * 核心承诺：
 *  1. 【可中断】手指按下（ACTION_DOWN）的瞬间立即取消正在播放的动画，手势随时接管，
 *     绝不让用户等动画播完，也不会"卡死"在半路。
 *  2. 【速度交接】手指松开（ACTION_UP）时继承手势最后速度，带阻尼继续滑行（惯性）。
 *  3. 【空间一致性】面板从指定侧滑入、关闭原路滑回；开启时背景同步缩放 + 变暗。
 *
 * @param orientation 滑入方向：ORIENT_BOTTOM（底部上滑）或 ORIENT_RIGHT（右侧滑入）
 */
class MotionSheet @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : FrameLayout(context, attrs, defStyleAttr) {

    companion object {
        const val ORIENT_BOTTOM = 0
        const val ORIENT_RIGHT = 1

        private const val OPENED = 1f
        private const val CLOSED = 0f
        private const val OPEN_DURATION = 380L
        private const val CLOSE_DURATION = 320L
        private const val OPEN_THRESHOLD = 0.5f
    }

    var orientation: Int = ORIENT_BOTTOM

    /** 拖拽位移换算进度的系数：越大越跟手 */
    var dragSensitivity = 0.005f

    /** 面板开启时背景缩放比例 */
    var backdropScale = 0.96f
    /** 面板开启时背景最大变暗程度 (0..1) */
    var backdropDim = 0.45f

    /** 当前进度 0(收起) ~ 1(展开) */
    var progress: Float = CLOSED
        private set

    /** 被缩放/变暗的底层内容视图 */
    var backdropView: android.view.View? = null
        set(value) {
            field = value
            applyBackdrop()
        }

    var onStateChange: ((Boolean) -> Unit)? = null
    var onProgressChange: ((Float) -> Unit)? = null

    private var animator: ValueAnimator? = null

    private var tracking = false
    private var isDragging = false
    private var startProgress = 0f
    private var lastTouchX = 0f
    private var lastTouchY = 0f
    private var velocityTracker: VelocityTracker? = null

    private val flingVelocity = ViewConfiguration.get(context).scaledMinimumFlingVelocity
    private val touchSlop = ViewConfiguration.get(context).scaledTouchSlop
    private val decelerate = DecelerateInterpolator(1.6f)

    private var downX = 0f
    private var downY = 0f
    private var interceptOwn = false
    private var lastReportedOpen = false

    // ---------- 内部工具 ----------

    private fun cancelAnimation() {
        animator?.cancel()
        animator = null
    }

    private fun translateFromProgress(p: Float) {
        val dp = (p.coerceIn(CLOSED, OPENED))
        translationX = if (orientation == ORIENT_RIGHT) (1f - dp) * width else 0f
        translationY = if (orientation == ORIENT_BOTTOM) (1f - dp) * height else 0f
        alpha = dp.coerceIn(0f, 1f)
        visibility = if (dp <= 0f) INVISIBLE else VISIBLE
        if (dp > 0f) bringToFront()
    }

    private fun applyBackdrop() {
        val bg = backdropView ?: return
        val p = progress
        val scale = 1f - (1f - backdropScale) * p
        bg.scaleX = scale
        bg.scaleY = scale
        bg.alpha = 1f - backdropDim * p
    }

    private fun setProgress(p: Float) {
        val np = p.coerceIn(CLOSED, OPENED)
        progress = np
        translateFromProgress(np)
        applyBackdrop()
        onProgressChange?.invoke(np)
        val open = np >= OPEN_THRESHOLD
        if (open != lastReportedOpen) {
            lastReportedOpen = open
            onStateChange?.invoke(open)
        }
    }

    private fun animateTo(target: Float, duration: Long, withVelocity: Boolean) {
        cancelAnimation()
        val from = progress
        val delta = target - from
        if (abs(delta) < 0.001f) {
            setProgress(target)
            return
        }
        val anim = ValueAnimator.ofFloat(0f, 1f).apply {
            this.duration = duration
            interpolator = decelerate
            addUpdateListener {
                val t = it.animatedFraction
                var eased = t
                if (withVelocity) {
                    // 带初速：前期走得更快，尾段阻尼收住（iOS 感）
                    eased = (t * 1.25f).coerceAtMost(1f)
                }
                setProgress(from + delta * eased)
            }
            addListener(object : android.animation.AnimatorListenerAdapter() {
                override fun onAnimationEnd(animation: android.animation.Animator) {
                    animator = null
                    setProgress(target)
                }
            })
        }
        animator = anim
        anim.start()
    }

    private fun settleTo(open: Boolean) {
        animateTo(if (open) OPENED else CLOSED, if (open) OPEN_DURATION else CLOSE_DURATION, false)
    }

    private fun onRelease() {
        val tracker = velocityTracker
        tracker?.computeCurrentVelocity(1000)
        val vx = tracker?.xVelocity ?: 0f
        val vy = tracker?.yVelocity ?: 0f
        tracker?.recycle()
        velocityTracker = null
        tracking = false

        val velocity = if (orientation == ORIENT_BOTTOM) vy else vx
        val flipping = abs(velocity) > flingVelocity

        val shouldOpen = if (flipping) {
            velocity < 0  // 向"开"的方向滑 → 打开
        } else {
            progress >= OPEN_THRESHOLD
        }

        // 速度交接：越快的甩动，动画越冲劲
        animateTo(
            if (shouldOpen) OPENED else CLOSED,
            (OPEN_DURATION * (1f - (abs(velocity) / 6000f).coerceIn(0f, 0.4f))).toLong().coerceAtLeast(140),
            flipping
        )
    }

    // ---------- 触摸分发：随时打断 ----------

    override fun onInterceptTouchEvent(ev: MotionEvent): Boolean {
        when (ev.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                downX = ev.x
                downY = ev.y
                interceptOwn = false
                // 手指落下 → 立即打断动画，手势接管
                if (animator != null) {
                    cancelAnimation()
                    startProgress = progress
                    interceptOwn = true
                }
            }
            MotionEvent.ACTION_MOVE -> {
                if (!interceptOwn && animator == null) {
                    val delta = if (orientation == ORIENT_BOTTOM) ev.y - downY else ev.x - downX
                    if (abs(delta) > touchSlop) interceptOwn = true
                }
                if (interceptOwn) {
                    parent?.requestDisallowInterceptTouchEvent(true)
                    return true
                }
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> interceptOwn = false
        }
        return interceptOwn
    }

    override fun onTouchEvent(ev: MotionEvent): Boolean {
        when (ev.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                cancelAnimation()
                startProgress = progress
                tracking = true
                isDragging = false
                lastTouchX = ev.x
                lastTouchY = ev.y
                velocityTracker = VelocityTracker.obtain().also { it.addMovement(ev) }
                return true
            }
            MotionEvent.ACTION_MOVE -> {
                if (!tracking) return true
                val dx = ev.x - lastTouchX
                val dy = ev.y - lastTouchY
                lastTouchX = ev.x
                lastTouchY = ev.y
                velocityTracker?.addMovement(ev)

                val delta = if (orientation == ORIENT_BOTTOM) dy else dx
                if (!isDragging && abs(delta) > touchSlop) isDragging = true
                if (isDragging) {
                    // 反向拖动 = 反向跟随（随时掉头）
                    setProgress(startProgress + delta * dragSensitivity)
                }
                return true
            }
            MotionEvent.ACTION_UP -> {
                if (tracking) onRelease()
                return true
            }
            MotionEvent.ACTION_CANCEL -> {
                if (tracking) {
                    settleTo(progress >= OPEN_THRESHOLD)
                    tracking = false
                }
                velocityTracker?.recycle()
                velocityTracker = null
                return true
            }
        }
        return super.onTouchEvent(ev)
    }

    // ---------- 对外 API ----------

    fun open() {
        post {
            cancelAnimation()
            setProgress(CLOSED + 0.001f)
            animateTo(OPENED, OPEN_DURATION, false)
        }
    }

    fun close() {
        post {
            cancelAnimation()
            animateTo(CLOSED, CLOSE_DURATION, false)
        }
    }

    /** 是否已展开（供按钮状态、back 键逻辑使用） */
    fun isOpen(): Boolean = progress >= OPEN_THRESHOLD

    /** 立即把进度摆到指定位置（不带动画，用于初始落位/瞬移） */
    fun setProgressAnimated(p: Float) {
        cancelAnimation()
        setProgress(p)
    }

    override fun onDetachedFromWindow() {
        cancelAnimation()
        super.onDetachedFromWindow()
    }
}
