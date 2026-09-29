#!/bin/bash
# Один раз создаёт командный релизный ключ вне репозитория. Пароли вводятся интерактивно.
set -euo pipefail

cd "$(dirname "$0")/.."
root="$(pwd -P)"
target="${1:-}"

if [ -z "$target" ] || [ "${target#/}" = "$target" ]; then
  echo "Укажите абсолютный путь вне репозитория: $0 /путь/finni-release.jks" >&2
  exit 1
fi

case "$target" in
  "$root"/*) echo "Ключ нельзя хранить внутри репозитория." >&2; exit 1 ;;
esac

[ ! -e "$target" ] || { echo "Файл уже существует: $target" >&2; exit 1; }
mkdir -p "$(dirname "$target")"
umask 077

if [ -z "${JAVA_HOME:-}" ] && [ -d "/Applications/Android Studio.app/Contents/jbr/Contents/Home" ]; then
  export JAVA_HOME="/Applications/Android Studio.app/Contents/jbr/Contents/Home"
fi
: "${JAVA_HOME:?Укажите JAVA_HOME или установите Android Studio}"
export PATH="$JAVA_HOME/bin:$PATH"

keytool -genkeypair -v \
  -keystore "$target" \
  -alias finni-release \
  -keyalg RSA -keysize 4096 -validity 10000

chmod 600 "$target"
echo "Ключ создан: $target"
echo "Сделайте две защищённые резервные копии. Потерянный ключ восстановить нельзя."
