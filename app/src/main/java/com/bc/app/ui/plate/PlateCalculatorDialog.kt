package com.bc.app.ui.plate

import android.app.Dialog
import android.content.Context
import android.text.TextWatcher
import android.view.View
import android.view.ViewGroup
import android.view.Window
import com.bc.app.R
import com.bc.app.utils.PlateCalculator
import kotlin.math.abs

/**
 * 杠铃片计算器 —— 普通卡片弹窗（非毛玻璃）。
 * 输入目标重量 + 杆重，计算每侧杠铃片配比。
 * 使用标准 Dialog + 圆角白卡背景（bg_dialog_card），右上角带关闭按钮。
 */
class PlateCalculatorDialog(private val context: Context) {

    private var dialog: Dialog? = null

    fun show() {
        val dlg = Dialog(context, R.style.Theme_BrawnCatalog_CardDialog)
        dialog = dlg

        val view = View.inflate(context, R.layout.dialog_plate_calculator, null)
        dlg.setContentView(view)
        dlg.window?.apply {
            setLayout(
                (context.resources.displayMetrics.widthPixels * 0.9f).toInt(),
                ViewGroup.LayoutParams.WRAP_CONTENT
            )
            setBackgroundDrawableResource(com.bc.app.R.drawable.bg_dialog_card)
        }

        // 默认预填标准杆重
        view.findViewById<android.widget.EditText>(R.id.editBarWeight)?.setText("20")

        view.findViewById<View>(R.id.buttonClose)?.setOnClickListener { dlg.dismiss() }

        view.findViewById<View>(R.id.buttonCalculate)?.setOnClickListener {
            calculate(view)
        }

        // 回车/换行自动计算
        val calcOnEnter = object : TextWatcher {
            override fun afterTextChanged(s: android.text.Editable?) { calculate(view) }
            override fun beforeTextChanged(s: CharSequence?, a: Int, b: Int, c: Int) {}
            override fun onTextChanged(s: CharSequence?, a: Int, b: Int, c: Int) {}
        }
        view.findViewById<android.widget.EditText>(R.id.editTargetWeight)?.addTextChangedListener(calcOnEnter)

        dlg.show()
    }

    fun calculate(view: View) {
        val binding = view.findViewById<android.widget.EditText>(R.id.editTargetWeight)
        val barEdit = view.findViewById<android.widget.EditText>(R.id.editBarWeight)
        val resultLayout = view.findViewById<View>(R.id.layoutResult)
        val resultHeader = view.findViewById<android.widget.TextView>(R.id.textResultHeader)
        val platesPerSide = view.findViewById<android.widget.TextView>(R.id.textPlatesPerSide)
        val achievedWeight = view.findViewById<android.widget.TextView>(R.id.textAchievedWeight)
        if (binding == null || resultLayout == null) return

        val target = binding.text.toString().toFloatOrNull()
        val bar = barEdit?.text?.toString()?.toFloatOrNull() ?: 20f
        if (target == null || target <= 0) {
            resultLayout.visibility = View.GONE
            return
        }

        val result = PlateCalculator.calculate(target, bar)
        if (result == null) {
            resultLayout.visibility = View.GONE
            return
        }

        resultLayout.visibility = View.VISIBLE

        val warn = if (!result.isExact) "\n⚠️ 无法精确匹配，最接近方案" else ""
        resultHeader?.text = "配片方案$warn"

        if (result.platesPerSide.isEmpty()) {
            platesPerSide?.text = "目标重量低于杆重，无需配片"
            achievedWeight?.text = "杆重 $bar kg"
            return
        }

        // 每侧配片描述（合并相同规格）
        val perSide = result.platesPerSide.groupingBy { it }.eachCount().entries
            .sortedByDescending { it.key }
            .joinToString("  ") { e ->
                val label = if (e.value > 1) "${e.key}kg×${e.value}" else "${e.key}kg"
                label
            }

        val achievedFormatted = if (abs(result.achievedWeight - result.achievedWeight.toInt()) < 0.001f)
            result.achievedWeight.toInt().toString() else result.achievedWeight.toString()

        platesPerSide?.text = "每侧：$perSide\n两侧：$perSide（×2）"
        achievedWeight?.text = "实际总重：$achievedFormatted kg${if (result.isExact) " ✓" else ""}"
    }
}
