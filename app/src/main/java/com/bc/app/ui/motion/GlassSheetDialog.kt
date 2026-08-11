package com.bc.app.ui.motion

import android.app.Dialog
import android.content.Context
import android.graphics.Color
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.Window
import android.widget.FrameLayout

/**
 * 毛玻璃 + 可中断惯性滑动弹层（iOS 风格）。
 *
 * 特点：
 *  - 底部弹层从下方滑入、右侧弹层从右侧滑入，关闭原路滑回（空间一致性）。
 *  - 弹层外是半透明暗色可点区域（点击关闭），背后内容一键毛玻璃模糊 + 轻微缩放。
 *  - 面板本身可被手势随时打断 / 拖拽 / 反向，松手带阻尼惯性滑行。
 *
 * 用法：
 *   val sheet = GlassSheetDialog(context)
 *   sheet.orientation = MotionSheet.ORIENT_BOTTOM      // 或 ORIENT_RIGHT
 *   sheet.setBackdrop(activityRootView)                // 底层根视图（模糊+缩放）
 *   sheet.setContent(R.layout.my_panel)                // 面板内容
 *   sheet.show()
 *   sheet.dismiss()
 */
class GlassSheetDialog(context: Context) {

    private val dialog = Dialog(context, android.R.style.Theme_Black_NoTitleBar_Fullscreen)
    private val sheet = MotionSheet(context)
    private lateinit var panelHolder: FrameLayout
    private var scrimView: View? = null
    private var contentView: View? = null

    /** 底部弹层在屏幕中的高度比例（最大高度） */
    var bottomSheetHeightRatio = 0.9f
    /** 右侧弹层在屏幕中的宽度比例 */
    var rightSheetWidthRatio = 0.78f

    var orientation: Int = MotionSheet.ORIENT_BOTTOM
        set(value) {
            field = value
            sheet.orientation = value
        }

    /** 面板圆角 */
    var cornerRadius = 26f

    /** 此弹层覆盖的底层根视图（做毛玻璃模糊 + 缩放） */
    var backdrop: View? = null

    /** 模糊强度 0..1：大面板用高值，小控件用低值 */
    var blurStrength = 0.6f
    /** 背景缩放比例 */
    var backdropScale = 0.96f
    /** 背景最大变暗程度 (0..1) */
    var backdropDim = 0.45f

    var onDismiss: (() -> Unit)? = null
    private var showing = false
    private var dimmed = false

    init {
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE)
        dialog.setCancelable(false)

        val root = FrameLayout(context)
        root.setBackgroundColor(Color.TRANSPARENT)

        // 暗色遮罩：面板之外的区域，点击关闭
        val scrim = View(context).apply {
            setBackgroundColor(Color.argb(120, 0, 0, 0))
            setOnClickListener { dismiss() }
        }
        root.addView(scrim, FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.MATCH_PARENT,
            FrameLayout.LayoutParams.MATCH_PARENT
        ))
        scrimView = scrim

        // 面板承载容器（圆角 + 毛玻璃背景），置顶
        panelHolder = FrameLayout(context).apply {
            background = ColorDrawable(Color.argb(238, 250, 250, 247))
        }
        sheet.addView(panelHolder, FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.MATCH_PARENT,
            FrameLayout.LayoutParams.MATCH_PARENT,
            Gravity.CENTER
        ))

        // 面板内点击不应触发 scrim 关闭
        sheet.setOnTouchListener { _, _ -> false }
        sheet.setOnClickListener(null)

        // 面板状态：拖回完全关闭后收尾
        sheet.onStateChange = { open ->
            if (!open && showing && !sheet.isOpen()) finishDismiss()
        }

        root.addView(sheet, FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.MATCH_PARENT,
            FrameLayout.LayoutParams.MATCH_PARENT,
            Gravity.BOTTOM
        ))

        dialog.setContentView(root)
        dialog.window?.apply {
            setLayout(
                android.view.ViewGroup.LayoutParams.MATCH_PARENT,
                android.view.ViewGroup.LayoutParams.MATCH_PARENT
            )
            setBackgroundDrawableResource(android.R.color.transparent)
            decorView.setBackgroundColor(Color.TRANSPARENT)
        }
    }

    private fun applySheetSize() {
        val lp = sheet.layoutParams as FrameLayout.LayoutParams
        if (orientation == MotionSheet.ORIENT_RIGHT) {
            lp.width = (dialog.context.resources.displayMetrics.widthPixels * rightSheetWidthRatio).toInt()
            lp.height = FrameLayout.LayoutParams.MATCH_PARENT
            lp.gravity = Gravity.RIGHT or Gravity.CENTER_VERTICAL
        } else {
            lp.width = FrameLayout.LayoutParams.MATCH_PARENT
            lp.height = (dialog.context.resources.displayMetrics.heightPixels * bottomSheetHeightRatio).toInt()
            lp.gravity = Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL
        }
        sheet.layoutParams = lp
    }

    /** 面板内容 */
    fun setContent(layoutRes: Int) {
        setContentView(View.inflate(dialog.context, layoutRes, null))
    }

    fun setContentView(view: View) {
        contentView = view
        panelHolder.removeAllViews()
        panelHolder.addView(view, FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.MATCH_PARENT,
            FrameLayout.LayoutParams.MATCH_PARENT
        ))
    }

    fun <T : View> findViewById(id: Int): T? = contentView?.findViewById(id)

    fun show() {
        if (showing) return
        showing = true
        applySheetSize()

        val bg = backdrop
        if (bg != null) {
            try {
                val blurred = BlurUtil.captureBlurred(bg, blurStrength)
                panelHolder.background = BlurUtil.asDrawable(blurred, dialog.context.resources)
            } catch (_: Throwable) {
                panelHolder.background = ColorDrawable(Color.argb(238, 250, 250, 247))
            }
            sheet.backdropView = bg
            sheet.backdropScale = backdropScale
            sheet.backdropDim = backdropDim
        }

        dialog.show()
        // 首帧先落位，再滑入
        sheet.post {
            sheet.setProgressAnimated(0f)
            sheet.open()
        }
    }

    fun dismiss() {
        if (!showing) return
        sheet.close()
        panelHolder.postDelayed({ finishDismiss() }, 420)
    }

    fun dismissNow() {
        if (!showing) return
        showing = false
        restoreBackdrop()
        dialog.dismiss()
        onDismiss?.invoke()
    }

    private fun finishDismiss() {
        if (!showing) return
        showing = false
        restoreBackdrop()
        dialog.dismiss()
        onDismiss?.invoke()
    }

    private fun restoreBackdrop() {
        sheet.backdropView?.let { bg ->
            bg.scaleX = 1f
            bg.scaleY = 1f
            bg.alpha = 1f
        }
    }
}

private class ColorDrawable(color: Int) : android.graphics.drawable.ColorDrawable(color)
