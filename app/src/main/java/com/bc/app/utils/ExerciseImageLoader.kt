package com.bc.app.utils

import android.view.View
import android.widget.ImageView
import coil.load

/**
 * 动作配图统一加载器。
 *
 * imagePath 有两种来源：
 *  - 数据集/预置配图：assets/exercise_images/ 下的文件名（如 "0001-2gPfomN.gif"），
 *    通过 `file:///android_asset/exercise_images/<name>` 加载（GIF 由全局 GifDecoder 播放）。
 *  - 用户自行导入的自定义图片：应用私有目录绝对路径
 *    （如 "/data/user/0/com.bc.app/files/exercise_images/custom_xxx.jpg"），
 *    自动补 `file://` 前缀后以本地文件方式加载（静态图）。
 *
 * 空字符串表示无配图，ImageView 隐藏。
 */
fun ImageView.loadExerciseImage(imagePath: String) {
    if (imagePath.isBlank()) {
        visibility = View.GONE
        return
    }
    visibility = View.VISIBLE
    val url = when {
        imagePath.startsWith("file://") -> imagePath
        imagePath.startsWith("/") -> "file://$imagePath"
        else -> "file:///android_asset/exercise_images/$imagePath"
    }
    load(url)
}
