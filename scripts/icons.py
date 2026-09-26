"""Иконка приложения из art/app/app_icon.png — гайд §13.1, прогон 5 графики.

Раньше иконка снималась с рига тестом IconExportTest; с прогона 5 она нарисована
целиком (art/app/app_icon.png, реестр — internal/design/assets-register.md), тест
удалён. Скрипт нужен только при замене картинки:
    python3 scripts/icons.py
Нужен Pillow. Кладёт передний слой адаптивной иконки в res/mipmap-*/ (картинка во всё
поле 108 dp, фон слоя — bg_sand) и 512 × 512 для витрины RuStore в build/store/icon-512.png.
"""
from pathlib import Path
from PIL import Image

root = Path(__file__).resolve().parent.parent
src = Image.open(root / "art/app/app_icon.png").convert("RGB")
res = root / "app/src/main/res"
for folder, size in {"mdpi": 108, "hdpi": 162, "xhdpi": 216, "xxhdpi": 324, "xxxhdpi": 432}.items():
    out = res / f"mipmap-{folder}"
    out.mkdir(parents=True, exist_ok=True)
    src.resize((size, size), Image.LANCZOS).save(out / "ic_launcher_foreground.png")
(root / "build/store").mkdir(parents=True, exist_ok=True)
src.resize((512, 512), Image.LANCZOS).save(root / "build/store/icon-512.png")
print("ok")
