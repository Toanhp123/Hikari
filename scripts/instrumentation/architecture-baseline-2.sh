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
  echo 'adb is required for Architecture Baseline 2 instrumentation.' >&2
  exit 2
}

: "${ANDROID_SERIAL:?Set ANDROID_SERIAL to the target emulator/device serial.}"

ACTUAL_API="$(
  adb -s "$ANDROID_SERIAL" shell getprop ro.build.version.sdk |
    tr -d '\r[:space:]'
)"
if [[ "$ACTUAL_API" != "$EXPECTED_API" ]]; then
  echo "Expected API $EXPECTED_API on $ANDROID_SERIAL, found API $ACTUAL_API." >&2
  exit 1
fi

"$GRADLEW" --no-daemon \
  --dependency-verification strict \
  :plugins:runtime:connectedDebugAndroidTest \
  :storage:room:connectedDebugAndroidTest \
  :feature:catalog:connectedDebugAndroidTest \
  :app:connectedDebugAndroidTest \
  --stacktrace

"$GRADLEW" --no-daemon \
  --dependency-verification strict \
  :app:installDebug \
  --stacktrace

adb -s "$ANDROID_SERIAL" shell am force-stop app.openstory
LAUNCH_OUTPUT="$(
  adb -s "$ANDROID_SERIAL" shell am start -W \
    -n app.openstory/app.openstory.MainActivity
)"
printf '%s\n' "$LAUNCH_OUTPUT"

grep -q 'Status: ok' <<<"$LAUNCH_OUTPUT" || {
  echo 'Launcher activity did not report Status: ok.' >&2
  exit 1
}

APP_PID="$(
  adb -s "$ANDROID_SERIAL" shell pidof app.openstory |
    tr -d '\r[:space:]'
)"
if [[ -z "$APP_PID" ]]; then
  echo 'app.openstory process is not running after launcher start.' >&2
  adb -s "$ANDROID_SERIAL" logcat -d -t 300 >&2 || true
  exit 1
fi

echo "Architecture Baseline 2 instrumentation passed on API $EXPECTED_API ($ANDROID_SERIAL, pid=$APP_PID)."
