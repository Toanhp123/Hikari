#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
TEMP_DIR="$(mktemp -d)"
trap 'rm -rf "$TEMP_DIR"' EXIT

FAKE_BIN="$TEMP_DIR/bin"
mkdir -p "$FAKE_BIN"
ADB_LOG="$TEMP_DIR/adb.log"
GRADLE_LOG="$TEMP_DIR/gradle.log"

cat > "$FAKE_BIN/adb" <<EOF_ADB
#!/usr/bin/env bash
set -euo pipefail
printf '%s\n' "\$*" >> "$ADB_LOG"
if [[ "\${1:-}" == 'devices' ]]; then
  printf 'List of devices attached\nauto-selected-device\tdevice\n'
  exit 0
fi
if [[ "\${1:-}" == '-s' ]]; then
  serial="\${2:-}"
  shift 2
  [[ "\$serial" == 'explicit-api-26' ]] || exit 91
fi
case "\${1:-} \${2:-} \${3:-}" in
  'shell getprop ro.build.version.sdk') printf '26\r\n' ;;
  'shell am force-stop') ;;
  'shell am start') printf 'Status: ok\nActivity: app.openstory/app.openstory.MainActivity\n' ;;
  'shell pidof app.openstory') printf '4242\n' ;;
  'logcat -d -t') ;;
  *) printf 'Unexpected fake adb invocation: %s\n' "\$*" >&2; exit 90 ;;
esac
EOF_ADB
chmod +x "$FAKE_BIN/adb"

cat > "$TEMP_DIR/fake-gradlew" <<EOF_GRADLE
#!/usr/bin/env bash
set -euo pipefail
printf '%s\n' "\$*" >> "$GRADLE_LOG"
EOF_GRADLE
chmod +x "$TEMP_DIR/fake-gradlew"

if PATH="$FAKE_BIN:$PATH" GRADLEW="$TEMP_DIR/fake-gradlew" \
  "$ROOT_DIR/scripts/instrumentation/architecture-baseline-2.sh" 26 >/dev/null 2>&1; then
  echo 'Expected a missing ANDROID_SERIAL to fail without auto-selection.' >&2
  exit 1
fi

: > "$ADB_LOG"
PATH="$FAKE_BIN:$PATH" \
ANDROID_SERIAL='explicit-api-26' \
GRADLEW="$TEMP_DIR/fake-gradlew" \
  "$ROOT_DIR/scripts/instrumentation/architecture-baseline-2.sh" 26 >/dev/null

if grep -q '^devices$' "$ADB_LOG"; then
  echo 'Instrumentation runner must not auto-select an attached device.' >&2
  exit 1
fi
if grep -v '^-s explicit-api-26 ' "$ADB_LOG" | grep -q .; then
  echo 'Every adb call must forward the supplied ANDROID_SERIAL.' >&2
  exit 1
fi
for task in \
  ':plugins:runtime:connectedDebugAndroidTest' \
  ':storage:room:connectedDebugAndroidTest' \
  ':feature:catalog:connectedDebugAndroidTest' \
  ':app:connectedDebugAndroidTest' \
  ':app:installDebug'; do
  grep -q -- "$task" "$GRADLE_LOG"
done

echo 'instrumentation/architecture-baseline-2.sh contract verified.'
