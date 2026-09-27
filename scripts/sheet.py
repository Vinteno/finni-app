#!/usr/bin/env python3
"""Лист снимков: несколько PNG рядом, с подписями, в одну картинку.

Облачный агент смотрит экраны глазами без эмулятора: снимки Robolectric (build/shots/...) склеиваются
по 4–6 в лист, лист открывается как изображение. Так видно весь путь недели разом, а не 40 файлов
по одному.

    python3 scripts/sheet.py OUT.png a.png b.png ...     # явный список
    python3 scripts/sheet.py OUT.png DIR --cols 5        # все PNG папки по имени
    python3 scripts/sheet.py OUT.png DIR --match home_   # только с подстрокой в имени

Нужен Pillow (pip install pillow).
"""
import argparse
import os
import sys

from PIL import Image, ImageDraw, ImageFont


def font(size):
    for path in ("/usr/share/fonts/truetype/dejavu/DejaVuSans.ttf", "/usr/share/fonts/dejavu/DejaVuSans.ttf"):
        if os.path.exists(path):
            return ImageFont.truetype(path, size)
    return ImageFont.load_default()


def main():
    p = argparse.ArgumentParser()
    p.add_argument("out")
    p.add_argument("inputs", nargs="+")
    p.add_argument("--cols", type=int, default=4)
    p.add_argument("--height", type=int, default=640, help="высота каждого снимка на листе, px")
    p.add_argument("--match", default="")
    a = p.parse_args()

    files = []
    for i in a.inputs:
        if os.path.isdir(i):
            files += sorted(os.path.join(i, f) for f in os.listdir(i) if f.endswith(".png"))
        else:
            files.append(i)
    files = [f for f in files if a.match in os.path.basename(f)]
    if not files:
        sys.exit("нет снимков")

    label_h = 28
    f = font(16)
    thumbs = []
    for path in files:
        im = Image.open(path).convert("RGB")
        k = a.height / im.height
        thumbs.append((os.path.basename(path)[:-4], im.resize((max(1, int(im.width * k)), a.height))))
    cell_w = max(t.width for _, t in thumbs) + 12
    cols = min(a.cols, len(thumbs))
    rows = (len(thumbs) + cols - 1) // cols
    sheet = Image.new("RGB", (cell_w * cols, (a.height + label_h + 12) * rows), "white")
    d = ImageDraw.Draw(sheet)
    for n, (name, t) in enumerate(thumbs):
        x = (n % cols) * cell_w + 6
        y = (n // cols) * (a.height + label_h + 12) + 6
        d.text((x, y), name, fill="black", font=f)
        sheet.paste(t, (x, y + label_h))
        d.rectangle([x - 1, y + label_h - 1, x + t.width, y + label_h + t.height], outline="#888")
    sheet.save(a.out)
    print(a.out, len(thumbs), "снимков")


if __name__ == "__main__":
    main()
