#!/usr/bin/env python3
"""
根据上传的源图生成 Android 应用图标。

处理流程：
1. 读取输入照片（INPUT_PATH）。
2. 按 SCALE=0.75 缩放至原尺寸的 75%（保持比例，高质量 Lanczos 重采样）。
3. 从 75% 基准图生成完整 Android launcher 图标集：
   - 标准图标（ic_launcher.png）
   - 圆形图标（ic_launcher_round.png）
   - 自适应图标前景（ic_launcher_foreground.png / ic_launcher_round_foreground.png）
   - Play Store 图标（ic_launcher-playstore.png）
4. 输出为 PNG，保存到项目 res/mipmap-* 目录。

图标尺寸规范（Android 官方）：
- Launcher: mdpi 48, hdpi 72, xhdpi 96, xxhdpi 144, xxxhdpi 192
- Adaptive foreground: 108dp = 108/162/216/324/432 px（2.25x launcher）
- Play Store: 512 x 512 px
"""

from __future__ import annotations

import os
from pathlib import Path

from PIL import Image, ImageDraw

# --------------------------------------------------------------------------- #
# 配置区
# --------------------------------------------------------------------------- #
INPUT_PATH = r"C:\Users\24715\.workbuddy\clipboard-images\clipboard-2026-08-31T07-44-14-140Z-581d2adb.png"
OUTPUT_ROOT = r"E:\BC\app\src\main\res"
OUTPUT_FORMAT = "PNG"
SCALE = 0.75

# 输出尺寸定义: (目录后缀, launcher_px, foreground_px)
DENSITIES = [
    ("mdpi", 48, 108),
    ("hdpi", 72, 162),
    ("xhdpi", 96, 216),
    ("xxhdpi", 144, 324),
    ("xxxhdpi", 192, 432),
]

# 自适应图标前景：Logo 占画布的 70%，其余为白底留白
SAFE_ZONE_RATIO = 0.70

# 圆形图标内容占画布比例（80% 留白边距更稳妥，避免圆形遮罩裁到 Logo 主体）
ROUND_CONTENT_RATIO = 0.70

# Play Store 尺寸
PLAYSTORE_SIZE = 512

# --------------------------------------------------------------------------- #
# 图像处理函数
# --------------------------------------------------------------------------- #

def make_square(src: Image.Image, size: int, *, is_round: bool = False, content_ratio: float = 0.90) -> Image.Image:
    """
    将源图缩放并居中放置到 size x size 的画布上。

    :param src: 源图（已缩放至 75% 的基准图）
    :param size: 输出边长
    :param is_round: 是否裁成圆形
    :param content_ratio: 内容占画布宽高的比例
    """
    canvas = Image.new("RGBA", (size, size), (255, 255, 255, 255))

    # 按 content_ratio 计算内容尺寸
    content_size = int(size * content_ratio)
    thumb = src.resize((content_size, content_size), Image.Resampling.LANCZOS)

    # 居中粘贴
    x = (size - content_size) // 2
    y = (size - content_size) // 2
    canvas.paste(thumb, (x, y), thumb if thumb.mode == "RGBA" else None)

    if is_round:
        mask = Image.new("L", (size, size), 0)
        draw = ImageDraw.Draw(mask)
        draw.ellipse((0, 0, size, size), fill=255)
        canvas.putalpha(mask)

    return canvas


def make_foreground(src: Image.Image, size: int) -> Image.Image:
    """生成自适应图标前景：透明底，内容位于安全区内。"""
    return make_square(
        src,
        size,
        is_round=False,
        content_ratio=SAFE_ZONE_RATIO,
    )


def make_round_foreground(src: Image.Image, size: int) -> Image.Image:
    """生成自适应圆形图标前景：透明底，圆形遮罩，内容位于安全区内。"""
    return make_square(
        src,
        size,
        is_round=True,
        content_ratio=SAFE_ZONE_RATIO,
    )


def save_png(img: Image.Image, path: Path) -> None:
    """保存为 PNG，确保无透明问题。"""
    path.parent.mkdir(parents=True, exist_ok=True)
    if img.mode != "RGBA":
        img = img.convert("RGBA")
    img.save(path, format="PNG", optimize=True)


# --------------------------------------------------------------------------- #
# 主流程
# --------------------------------------------------------------------------- #
def main() -> None:
    print(f"Input: {INPUT_PATH}")
    print(f"Scale: {SCALE * 100:.0f}%")
    print(f"Output root: {OUTPUT_ROOT}")
    print(f"Format: {OUTPUT_FORMAT}")
    print("-" * 60)

    src = Image.open(INPUT_PATH)
    print(f"Original size: {src.size}")

    # 1) 缩放至 75%
    new_width = int(src.width * SCALE)
    new_height = int(src.height * SCALE)
    scaled = src.resize((new_width, new_height), Image.Resampling.LANCZOS)
    print(f"Scaled size: {scaled.size}")

    # 确保缩放后的图为 RGBA，方便后续合成
    if scaled.mode != "RGBA":
        scaled = scaled.convert("RGBA")

    generated: list[Path] = []

    # 2) 生成各密度图标
    for suffix, launcher_px, fg_px in DENSITIES:
        out_dir = Path(OUTPUT_ROOT) / f"mipmap-{suffix}"

        # 标准 launcher 图标（Logo 70%，白底留白）
        launcher = make_square(scaled, launcher_px, is_round=False, content_ratio=0.70)
        p = out_dir / "ic_launcher.png"
        save_png(launcher, p)
        generated.append(p)

        # 圆形 launcher 图标
        launcher_round = make_square(scaled, launcher_px, is_round=True, content_ratio=ROUND_CONTENT_RATIO)
        p = out_dir / "ic_launcher_round.png"
        save_png(launcher_round, p)
        generated.append(p)

        # 自适应前景
        fg = make_foreground(scaled, fg_px)
        p = out_dir / "ic_launcher_foreground.png"
        save_png(fg, p)
        generated.append(p)

        # 自适应圆形前景
        fg_round = make_round_foreground(scaled, fg_px)
        p = out_dir / "ic_launcher_round_foreground.png"
        save_png(fg_round, p)
        generated.append(p)

        print(f"mipmap-{suffix}: launcher={launcher_px}px, foreground={fg_px}px")

    # 3) Play Store 图标
    playstore = make_square(scaled, PLAYSTORE_SIZE, is_round=False, content_ratio=0.70)
    playstore_path = Path(OUTPUT_ROOT).parent / "ic_launcher-playstore.png"
    save_png(playstore, playstore_path)
    generated.append(playstore_path)
    print(f"Play Store: {PLAYSTORE_SIZE}px -> {playstore_path}")

    print("-" * 60)
    print("Generated files:")
    for p in generated:
        print(f"  {p}")
    print(f"\nTotal: {len(generated)} files")


if __name__ == "__main__":
    main()
