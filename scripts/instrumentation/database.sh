#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
cd "$ROOT_DIR"

GRADLEW="${GRADLEW:-./gradlew}"

EXPECTED_API="${1:-}"
case "$EXPECTED_API" in
  26|37) ;;
  *)
    echo "Usage: $0 <26|37>" >&2
    exit 2
    ;;
esac

command -v adb >/dev/null 2>&1 || {
  echo "adb is required for database instrumentation verification." >&2
  exit 2
}

if [[ -z "${ANDROID_SERIAL:-}" ]]; then
  DEVICE_SERIALS=()
  while IFS= read -r serial; do
    [[ -n "$serial" ]] && DEVICE_SERIALS+=("$serial")
  done < <(
    adb devices |
      awk 'NR > 1 && $2 == "device" { print $1 }'
  )

  if [[ "${#DEVICE_SERIALS[@]}" -ne 1 ]]; then
    echo \
      "Set ANDROID_SERIAL when zero or multiple Android devices are connected." \
      >&2
    exit 2
  fi

  export ANDROID_SERIAL="${DEVICE_SERIALS[0]}"
fi

ACTUAL_API="$(
  adb -s "$ANDROID_SERIAL" shell getprop ro.build.version.sdk |
    tr -d '\r[:space:]'
)"

if [[ "$ACTUAL_API" != "$EXPECTED_API" ]]; then
  echo \
    "Expected API $EXPECTED_API on $ANDROID_SERIAL, found API $ACTUAL_API." \
    >&2
  exit 1
fi

"$GRADLEW" --no-daemon \
  --dependency-verification strict \
  :storage:room:connectedDebugAndroidTest \
  --stacktrace

echo \
  "Database instrumentation passed on API $EXPECTED_API "\
  "($ANDROID_SERIAL)."
