#!/usr/bin/env bash
set -euo pipefail

ROOT="$(cd "$(dirname "$0")/.." && pwd)"
TARGET_PACKAGE="app.universalmedia.debug"
TEST_CLASS="app.universalmedia.ProcessDeathSafEndToEndTest"
FIXTURE_SOURCE="$ROOT/playback/media3/src/androidTest/assets/fixture.mp4"
DEVICE_FOLDER="/sdcard/Download/HikariTask11"
DEVICE_FIXTURE="$DEVICE_FOLDER/task11-fixture.mp4"
TARGET_APK="$ROOT/app/build/outputs/apk/debug/app-debug.apk"
TEST_APK="$ROOT/app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk"

ADB=(adb)
if [[ -n "${ANDROID_SERIAL:-}" ]]; then
  ADB+=( -s "$ANDROID_SERIAL" )
fi

run_adb() {
  "${ADB[@]}" "$@"
}

require_device() {
  local state
  state="$(run_adb get-state 2>/dev/null || true)"
  [[ "$state" == "device" ]] || {
    echo "Task 11 requires one online adb device (or ANDROID_SERIAL)." >&2
    exit 1
  }
}

run_phase() {
  local method="$1"
  local output
  set +e
  output="$(run_adb shell am instrument -w -r \
    -e class "$TEST_CLASS#$method" "$INSTRUMENTATION" 2>&1)"
  local status=$?
  set -e
  printf '%s\n' "$output"
  [[ $status -eq 0 ]] || {
    echo "Instrumentation transport failed for $method." >&2
    exit $status
  }
  grep -Eq 'OK \([0-9]+ tests?\)' <<<"$output" || {
    echo "Instrumentation did not report JUnit success for $method." >&2
    exit 1
  }
  if grep -Eq 'FAILURES!!!|INSTRUMENTATION_FAILED|INSTRUMENTATION_ABORTED' <<<"$output"; then
    echo "Instrumentation reported failure for $method." >&2
    exit 1
  fi
}

cd "$ROOT"
require_device
[[ -f "$FIXTURE_SOURCE" ]] || {
  echo "Missing self-owned playback fixture: $FIXTURE_SOURCE" >&2
  exit 1
}

echo '== Task 11: build target and instrumentation APKs =='
./gradlew :app:assembleDebug :app:assembleDebugAndroidTest --no-daemon
[[ -f "$TARGET_APK" && -f "$TEST_APK" ]] || {
  echo 'Expected debug APK outputs were not produced.' >&2
  exit 1
}

echo '== Task 11: install once and prepare clean device state =='
run_adb install -r -t "$TARGET_APK"
run_adb install -r -t "$TEST_APK"

INSTRUMENTATION="$(run_adb shell pm list instrumentation | sed -n \
  "s/^instrumentation:\([^ ]*\) (target=$TARGET_PACKAGE)$/\1/p" | head -n 1 | tr -d '\r')"
[[ -n "$INSTRUMENTATION" ]] || {
  echo "Could not resolve instrumentation component for $TARGET_PACKAGE." >&2
  exit 1
}
TEST_PACKAGE="${INSTRUMENTATION%%/*}"

run_adb shell pm clear "$TARGET_PACKAGE" >/dev/null
run_adb shell pm clear "$TEST_PACKAGE" >/dev/null
run_adb shell rm -rf "$DEVICE_FOLDER"
run_adb shell mkdir -p "$DEVICE_FOLDER"
run_adb push "$FIXTURE_SOURCE" "$DEVICE_FIXTURE" >/dev/null

echo
echo '== Phase A: establish real SAF + Room + Library + Media3 state =='
echo 'When DocumentsUI opens, choose Internal/shared storage > Download > HikariTask11,'
echo 'then confirm Use this folder / Allow. Do not choose the parent Download folder.'
run_phase phaseA_establishDurableState

echo
echo '== External real process stop =='
run_adb shell am force-stop "$TARGET_PACKAGE"
# Force-stop must happen outside both instrumentation invocations.
sleep 1

echo
echo '== Phase B: fresh process reconstructs durable state =='
run_phase phaseB_reconstructAfterForceStop

echo
echo 'Task 11 process-death / persisted-SAF device evidence: PASS'
