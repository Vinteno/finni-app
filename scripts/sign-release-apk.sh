#!/bin/bash
# Собирает и подписывает релизный APK командным ключом. Секреты берёт только из окружения.
set -euo pipefail

cd "$(dirname "$0")/.."

: "${FINNI_KEYSTORE:?Укажите абсолютный путь в FINNI_KEYSTORE}"
: "${FINNI_KEYSTORE_PASSWORD:?Укажите пароль хранилища в FINNI_KEYSTORE_PASSWORD}"
: "${FINNI_KEY_PASSWORD:?Укажите пароль ключа в FINNI_KEY_PASSWORD}"

[ -f "$FINNI_KEYSTORE" ] || { echo "Ключ не найден: $FINNI_KEYSTORE" >&2; exit 1; }

if [ -z "${JAVA_HOME:-}" ] && [ -d "/Applications/Android Studio.app/Contents/jbr/Contents/Home" ]; then
  export JAVA_HOME="/Applications/Android Studio.app/Contents/jbr/Contents/Home"
fi
: "${JAVA_HOME:?Укажите JAVA_HOME или установите Android Studio}"
export PATH="$JAVA_HOME/bin:$PATH"

sdk="$(sed -n 's/^sdk.dir=//p' local.properties)"
tools="$(find "$sdk/build-tools" -mindepth 1 -maxdepth 1 -type d | sort -V | tail -1)"
zipalign="$tools/zipalign"
apksigner="$tools/apksigner"
[ -x "$zipalign" ] && [ -x "$apksigner" ] || { echo "Не найдены Android build-tools." >&2; exit 1; }

./gradlew :app:assembleRelease

version="$(sed -n 's/.*versionName = "\([^"]*\)".*/\1/p' app/build.gradle.kts | head -1)"
unsigned="app/build/outputs/apk/release/app-release-unsigned.apk"
outdir="build/release"
aligned="$outdir/finni-${version}-aligned.apk"
signed="$outdir/finni-${version}.apk"
mkdir -p "$outdir"

"$zipalign" -f -P 16 4 "$unsigned" "$aligned"
"$zipalign" -c -P 16 4 "$aligned"
"$apksigner" sign \
  --ks "$FINNI_KEYSTORE" \
  --ks-key-alias finni-release \
  --ks-pass env:FINNI_KEYSTORE_PASSWORD \
  --key-pass env:FINNI_KEY_PASSWORD \
  --out "$signed" "$aligned"
"$apksigner" verify --verbose --print-certs "$signed"
shasum -a 256 "$signed"

echo "Готово: $signed"
echo "Не заменяйте apk/finni-${version}.apk до установки и проверки на телефоне."
