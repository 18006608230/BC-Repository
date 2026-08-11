package com.bc.app.ui.motion

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.drawable.BitmapDrawable
import android.view.View

/**
 * 毛玻璃（backdrop-filter）工具。
 *
 * 用"降采样 + 多重近似高斯模糊"实现 frosted-glass 效果，兼容 minSdk 26 全版本：
 *  - 先把底层内容画到很小的小位图（等效强降采样，天然柔和）。
 *  - 再做多次 3x3 均值模糊叠加，得到通透的磨砂质感。
 *  - 最后放大回原尺寸作为面板背景。
 */
object BlurUtil {

    /**
     * 捕获 view 的可视区域并返回模糊后的位图。
     * @param strength 0..1：大面板用高值（更糊更通透），小控件用低值。
     */
    fun captureBlurred(view: View, strength: Float = 0.6f): Bitmap {
        val w = maxOf(view.width, 1)
        val h = maxOf(view.height, 1)

        // 采样比例随强度提高：越糊，降采样越狠（性能越好）
        val sample = when {
            strength >= 0.8f -> 8
            strength >= 0.45f -> 5
            else -> 4
        }
        val sw = (w / sample).coerceAtLeast(8)
        val sh = (h / sample).coerceAtLeast(8)

        // 1) 降采样绘制底层内容
        val small = Bitmap.createBitmap(sw, sh, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(small)
        val scale = sw.toFloat() / w
        canvas.scale(scale, scale)
        view.draw(canvas)

        // 2) 多次均值模糊叠加强度
        val passes = (strength * 3f).toInt().coerceIn(1, 5)
        var bmp = small
        repeat(passes) {
            val next = boxBlur(bmp)
            if (bmp !== small) bmp.recycle()
            bmp = next
        }

        // 3) 放大回原尺寸（让滤镜覆盖整张面板）
        val out = Bitmap.createScaledBitmap(bmp, w, h, true)
        if (bmp !== small) bmp.recycle()
        small.recycle()
        return out
    }

    /** 3x3 均值模糊一遍 */
    private fun boxBlur(src: Bitmap): Bitmap {
        val w = src.width
        val h = src.height
        val out = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        val px = IntArray(w * h)
        src.getPixels(px, 0, w, 0, 0, w, h)
        val res = IntArray(w * h)
        for (y in 0 until h) {
            for (x in 0 until w) {
                var r = 0; var g = 0; var b = 0; var a = 0; var n = 0
                for (dy in -1..1) {
                    for (dx in -1..1) {
                        val ny = y + dy
                        val nx = x + dx
                        if (ny in 0 until h && nx in 0 until w) {
                            val c = px[ny * w + nx]
                            r += Color.red(c); g += Color.green(c)
                            b += Color.blue(c); a += Color.alpha(c)
                            n++
                        }
                    }
                }
                val ia = (a / n).coerceIn(0, 255)
                res[y * w + x] = Color.argb(ia, r / n, g / n, b / n)
            }
        }
        out.setPixels(res, 0, w, 0, 0, w, h)
        return out
    }

    /** 包成可绘制层 */
    fun asDrawable(bmp: Bitmap, res: android.content.res.Resources): BitmapDrawable =
        BitmapDrawable(res, bmp)
}
