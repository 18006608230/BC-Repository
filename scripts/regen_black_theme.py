import os

RES = r"E:\BC\app\src\main\res\values"

KEYS = [
    "primary","primary_dark","primary_light","primary_pale","on_primary",
    "accent","accent_dark","on_accent",
    "window_bg","card_bg","header_bg","home_page_bg",
    "text_primary","text_secondary","text_disabled",
    "border","border_focus","input_bg","input_stroke",
    "gradient_start","gradient_end","glow","ripple",
]

# 「黑」主题：浅色背景 + 黑色前景，不是黑夜模式
#   大片浅色 = 极其浅的灰（card / header / window）
#   深色部分 = 纯黑（primary / text_primary / 按钮渐变）
black = {
    "primary":        "#1A1A1A",   # 深近黑（标题 / 按钮）
    "primary_dark":   "#000000",   # 纯黑（强强调用）
    "primary_light":  "#5C5C5C",   # 中灰（次级）
    "primary_pale":   "#E8E8EA",   # 极浅灰（链接 / 软背景点缀）
    "on_primary":     "#FFFFFF",
    "accent":         "#3C3C3C",   # 深灰辅色
    "accent_dark":    "#222222",
    "on_accent":      "#FFFFFF",
    "window_bg":      "#FAFAFA",   # 极浅灰（页面底）
    "card_bg":        "#F2F2F4",   # 极浅灰（卡片）
    "header_bg":      "#E8E8EA",   # 略深浅灰（顶部色片）
    "home_page_bg":   "#FAFAFA",
    "text_primary":   "#0A0A0A",   # 近纯黑（文字）
    "text_secondary": "#5C5C5C",   # 中灰（副文字）
    "text_disabled":  "#A8A8AA",
    "border":         "#D8D8DA",
    "border_focus":   "#1A1A1A",
    "input_bg":       "#FFFFFF",
    "input_stroke":   "#B0B0B0",
    "gradient_start": "#1A1A1A",
    "gradient_end":   "#3C3C3C",
    "glow":           "#401A1A1A",
    "ripple":         "#301A1A1A",
}

lines = ['<?xml version="1.0" encoding="utf-8"?>', '<resources>',
         '    <!-- 主题配色：黑（浅色模式：浅灰大底 + 黑色前景，区别于黑夜模式） -->']
for k in KEYS:
    lines.append(f'    <color name="th_black_{k}">{black[k]}</color>')
lines.append('</resources>')
with open(os.path.join(RES, "colors_theme_black.xml"), "w", encoding="utf-8") as f:
    f.write("\n".join(lines) + "\n")

print("regenerated colors_theme_black.xml")
print()
print(f"{'KEY':18s} {'VALUE'}")
for k in KEYS:
    print(f"  black_{k:14s} {black[k]}")
