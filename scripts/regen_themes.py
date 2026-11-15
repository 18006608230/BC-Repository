import colorsys, os

RES = r"E:\BC\app\src\main\res\values"

THEMES = [
    ("black",  "黑",          None, None, None, None),
    ("blue",   "深蓝",        218, 0.62, 0.50, 30),
    ("green",  "深绿",        145, 0.55, 0.42, 30),
    ("red",    "酒红",        348, 0.62, 0.42, 320),
    ("yellow", "日落黄",       42, 0.62, 0.56, 25),
    ("orange", "爱马仕橙",     24, 0.68, 0.55, 20),
    ("purple", "深紫",        270, 0.58, 0.46, 28),
]

KEYS = [
    "primary","primary_dark","primary_light","primary_pale","on_primary",
    "accent","accent_dark","on_accent",
    "window_bg","card_bg","header_bg","home_page_bg",
    "text_primary","text_secondary","text_disabled",
    "border","border_focus","input_bg","input_stroke",
    "gradient_start","gradient_end","glow","ripple",
]

def hsl(h, s, l):
    r,g,b = colorsys.hls_to_rgb(h/360.0, l, s)
    return "#%02X%02X%02X" % (round(r*255), round(g*255), round(b*255))

def hexa(h, s, l, a):
    c = hsl(h,s,l).lstrip('#')
    return "#%02X%s" % (round(a*255), c)

def palette_black():
    return {
        "primary":       "#3A4150",
        "primary_dark":  "#23272F",
        "primary_light": "#62697A",
        "primary_pale":  "#2A2E35",
        "on_primary":    "#FFFFFF",
        "accent":        "#7B8AA0",
        "accent_dark":   "#5C687A",
        "on_accent":     "#FFFFFF",
        "window_bg":     "#16181D",
        "card_bg":       "#1F2329",
        "header_bg":     "#23262C",
        "home_page_bg":  "#16181D",
        "text_primary":  "#EEF0F4",
        "text_secondary":"#A4ABB5",
        "text_disabled": "#5C6066",
        "border":        "#33373E",
        "border_focus":  "#62697A",
        "input_bg":      "#1F2329",
        "input_stroke":  "#4A4F57",
        "gradient_start":"#3A4150",
        "gradient_end":  "#62697A",
        "glow":          "#663A4150",
        "ripple":        "#443A4150",
    }

def palette_light(hue, sat, lit, accent_off):
    p = {}
    p["primary"]       = hsl(hue, sat, lit)
    p["primary_dark"]  = hsl(hue, sat+0.05, lit-0.10)
    p["primary_light"] = hsl(hue, sat, lit+0.18)
    p["primary_pale"]  = hsl(hue, 0.45, 0.92)
    p["on_primary"]    = "#FFFFFF"
    ac_h = (hue + accent_off) % 360
    p["accent"]        = hsl(ac_h, 0.62, 0.55)
    p["accent_dark"]   = hsl(ac_h, 0.62, 0.45)
    p["on_accent"]     = "#FFFFFF"
    p["window_bg"]     = hsl(hue, 0.20, 0.97)
    p["card_bg"]       = hsl(hue, 0.36, 0.93)
    p["header_bg"]     = hsl(hue, 0.45, 0.86)
    p["home_page_bg"]  = hsl(hue, 0.18, 0.965)
    p["text_primary"]  = "#0F1115"
    p["text_secondary"]= hsl(hue, 0.35, 0.40)
    p["text_disabled"] = "#9AA0A6"
    p["border"]        = hsl(hue, 0.30, 0.88)
    p["border_focus"]  = p["primary"]
    p["input_bg"]      = "#FFFFFF"
    p["input_stroke"]  = hsl(hue, 0.22, 0.80)
    p["gradient_start"]= p["primary"]
    p["gradient_end"]  = p["primary_light"]
    p["glow"]          = hexa(hue, sat, lit, 0.25)
    p["ripple"]        = hexa(hue, sat, lit, 0.20)
    return p

for theme in THEMES:
    key, cn, hue, sat, lit, aco = theme
    p = palette_black() if key == "black" else palette_light(hue, sat, lit, aco)
    lines = ['<?xml version="1.0" encoding="utf-8"?>', '<resources>',
             f'    <!-- 主题配色：{cn}（亮、不荧光） -->']
    for k in KEYS:
        lines.append(f'    <color name="th_{key}_{k}">{p[k]}</color>')
    lines.append('</resources>')
    with open(os.path.join(RES, f"colors_theme_{key}.xml"), "w", encoding="utf-8") as f:
        f.write("\n".join(lines) + "\n")
    print(f"  {key:6s} {cn:6s}  primary={p['primary']}  card={p['card_bg']}  header={p['header_bg']}  text={p['text_primary']}")

pf = os.path.join(RES, "colors_theme_pink.xml")
if os.path.exists(pf):
    os.remove(pf)
    print(f"\nremoved: {pf}")
