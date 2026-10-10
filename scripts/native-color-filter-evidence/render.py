#!/usr/bin/env python3
"""Rasterize actual native-core snapshot colors; these are not UIKit screenshots."""
import json
import sys
from pathlib import Path
from PIL import Image, ImageDraw, ImageFont

output = Path(sys.argv[1])
try:
    font = ImageFont.truetype("DejaVuSans.ttf", 16)
except OSError:
    font = ImageFont.load_default()
for lane in ("before", "after"):
    samples = json.loads((output / f"{lane}.json").read_text())
    canvas = Image.new("RGBA", (720, 210), "white")
    draw = ImageDraw.Draw(canvas)
    draw.text((12, 8), f"{lane.title()}: native-core snapshot colors (not UIKit capture)", fill="black", font=font)
    for index, sample in enumerate(samples):
        left = 12 + index * 236
        for y in range(44, 164, 12):
            for x in range(left, left + 216, 12):
                shade = 220 if ((x - left) // 12 + (y - 44) // 12) % 2 else 255
                draw.rectangle((x, y, x + 11, y + 11), fill=(shade, shade, shade, 255))
        argb = sample["resolved"]
        color = ((argb >> 16) & 255, (argb >> 8) & 255, argb & 255, argb >> 24)
        canvas.alpha_composite(Image.new("RGBA", (216, 120), color), (left, 44))
        draw.text((left, 172), f"ARGB #{argb:08X}", fill="black", font=font)
    canvas.convert("RGB").save(output / f"{lane}.png")
