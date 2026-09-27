#!/bin/bash
# «Ребёнок в облаке»: игра целиком с первого запуска на четырёх окнах, снимок каждого экрана, проверка
# вёрстки и текста, листы снимков для просмотра глазами.
#
#   bash scripts/journey.sh            # все четыре окна (~6 минут)
#   bash scripts/journey.sh 411x659    # одно окно: 411x659, 360x600, 411x860, font130
#
# Итог: app/build/journey/<окно>/report.md — находки и путь; index.html — все снимки; sheets/NN.png —
# листы по 16 снимков (их открывает агент в облаке как изображения).
set -e
cd "$(dirname "$0")/.."

case "${1:-all}" in
  411x659) filter='*JourneyTest.phone411x659' ;;
  360x600) filter='*JourneyTest.phone360x600' ;;
  411x860) filter='*JourneyTest.phone411x860' ;;
  font130) filter='*JourneyTest.font130' ;;
  *) filter='*JourneyTest*' ;;
esac

./gradlew :app:recordRoborazziDebug --tests "$filter" -q --max-workers=2 || true

python3 -c "import PIL" 2>/dev/null || pip install -q pillow
for dir in app/build/journey/*/; do
  [ -f "$dir/report.md" ] || continue
  mkdir -p "$dir/sheets"
  ls "$dir"/*.png | split -l 16 -d - "$dir/sheets/list_"
  for list in "$dir"/sheets/list_*; do
    python3 scripts/sheet.py "$dir/sheets/$(basename "$list" | sed 's/list_//').png" $(cat "$list") --cols 8 --height 440 > /dev/null
    rm "$list"
  done
  echo "== $dir"
  sed -n '/^# /,/^## Путь/p' "$dir/report.md" | grep -v '^## Путь'
done
