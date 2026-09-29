#!/bin/bash
# Собирает локальную копию материалов промежуточной сдачи. Ничего не загружает и не пушит.
set -euo pipefail

cd "$(dirname "$0")/.."

version="$(sed -n 's/.*versionName = "\([^"]*\)".*/\1/p' app/build.gradle.kts | head -1)"
: "${version:?Не удалось определить versionName из app/build.gradle.kts}"
apk="apk/finni-${version}.apk"
out="dist/finni-selection-${version}"

required=(
  README.md
  "$apk"
  docs/submission.md
  docs/architecture.md
  docs/data-and-formulas.md
  docs/requirements.md
  docs/questions.md
  docs/limitations.md
)

for path in "${required[@]}"; do
  [ -f "$path" ] || { echo "Не найден обязательный файл: $path" >&2; exit 1; }
done

rm -rf "$out"
mkdir -p "$out/apk" "$out/docs"
cp README.md "$out/"
cp "$apk" "$out/apk/"
cp -R docs/. "$out/docs/"
# Рабочий аудит финальной сдачи не относится к промежуточному комплекту и содержит внутренние ссылки.
rm -f "$out/docs/final-submission-checklist.md"
cp -R licenses "$out/"

(cd "$out" && shasum -a 256 "apk/finni-${version}.apk" > SHA256SUMS.txt)
rm -f "${out}.zip"
(cd dist && zip -qr "finni-selection-${version}.zip" "finni-selection-${version}")

echo "Готово: ${out}.zip"
echo "APK: $(shasum -a 256 "$apk")"
