#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
TEMP_DIR="$(mktemp -d)"
trap 'rm -rf "$TEMP_DIR"' EXIT

FAKE_BIN="$TEMP_DIR/bin"
mkdir -p "$FAKE_BIN"
GRADLE_LOG="$TEMP_DIR/gradle.log"

cat > "$FAKE_BIN/adb" <<'ADB'
#!/usr/bin/env bash
set -euo pipefail

if [[ "${1:-}" == "devices" ]]; then
  printf 'List of devices attached\nfake-api-26\tdevice\n'
  exit 0
fi

if [[ "${1:-}" == "-s" ]]; then
  shift 2
fi

case "${1:-} ${2:-} ${3:-}" in
  "shell getprop ro.build.version.sdk")
    printf '26\r\n'
    ;;
  "shell am force-stop")
    ;;
  "shell am start")
    printf 'Status: ok\nActivity: app.openstory/app.openstory.MainActivity\n'
    ;;
  "shell pidof app.openstory")
    printf '4242\n'
    ;;
  "logcat -d -t")
    ;;
  *)
    printf 'Unexpected fake adb invocation: %s\n' "$*" >&2
    exit 90
    ;;
esac
ADB
chmod +x "$FAKE_BIN/adb"

cat > "$TEMP_DIR/fake-gradlew" <<EOF_GRADLE
#!/usr/bin/env bash
printf '%s\n' "\$*" >> "$GRADLE_LOG"
EOF_GRADLE
chmod +x "$TEMP_DIR/fake-gradlew"

PATH="$FAKE_BIN:$PATH" \
GRADLEW="$TEMP_DIR/fake-gradlew" \
  "$ROOT_DIR/scripts/instrumentation/android.sh" 26 >/dev/null

grep -q ':app:connectedDebugAndroidTest' "$GRADLE_LOG"
grep -q ':app:installDebug' "$GRADLE_LOG"

set +e
PATH="$FAKE_BIN:$PATH" \
ANDROID_SERIAL="fake-api-26" \
GRADLEW="$TEMP_DIR/fake-gradlew" \
  "$ROOT_DIR/scripts/instrumentation/android.sh" 37 >/dev/null 2>&1
STATUS=$?
set -e

if [[ "$STATUS" -eq 0 ]]; then
  echo "Expected an API mismatch failure." >&2
  exit 1
fi

echo "instrumentation/android.sh contract verified."
