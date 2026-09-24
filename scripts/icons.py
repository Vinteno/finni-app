"""Иконка приложения из снимков IconExportTest — гайд §13.1.

Запуск после ./gradlew :app:recordRoborazziDebug --tests '*IconExportTest*':
    python3 scripts/icons.py
Нужен Pillow. Кладёт передний слой адаптивной иконки в res/mipmap-*/ и
512 × 512 без прозрачности для витрины RuStore в build/store/icon-512.png.
"""
from pathlib import Path
from PIL import Image

root = Path(__file__).resolve().parent.parent
shots = root / "app/build/icons"
res = root / "app/src/main/res"
fg = Image.open(shots / "foreground.png").convert("RGBA")
# Фон снимка — окно Robolectric; передний слой должен быть прозрачным вокруг рисунка.
px = fg.load()
bg = px[0, 0]
for y in range(fg.height):
    for x in range(fg.width):
        r, g, b, a = px[x, y]
        if abs(r - bg[0]) < 3 and abs(g - bg[1]) < 3 and abs(b - bg[2]) < 3:
            px[x, y] = (0, 0, 0, 0)
for folder, size in {"mdpi": 108, "hdpi": 162, "xhdpi": 216, "xxhdpi": 324, "xxxhdpi": 432}.items():
    out = res / f"mipmap-{folder}"
    out.mkdir(parents=True, exist_ok=True)
    fg.resize((size, size), Image.LANCZOS).save(out / "ic_launcher_foreground.png")
store = Image.open(shots / "store.png").convert("RGB").resize((512, 512), Image.LANCZOS)
(root / "build/store").mkdir(parents=True, exist_ok=True)
store.save(root / "build/store/icon-512.png")
print("ok")
