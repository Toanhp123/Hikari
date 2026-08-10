#!/usr/bin/env bash
set -euo pipefail

FILE=scripts/checkpoints/architecture-baseline-2.sh
[[ -f "$FILE" ]]
grep -q './scripts/verify.sh' "$FILE"
grep -q 'ANDROID_SERIAL_API_26' "$FILE"
grep -q 'ANDROID_SERIAL_API_37' "$FILE"
grep -q 'scripts/instrumentation/architecture-baseline-2.sh' "$FILE"
RUNNER=scripts/instrumentation/architecture-baseline-2.sh
for task in \
  ':plugins:runtime:connectedDebugAndroidTest' \
  ':storage:room:connectedDebugAndroidTest' \
  ':feature:catalog:connectedDebugAndroidTest' \
  ':app:connectedDebugAndroidTest'; do
  grep -q -- "$task" "$RUNNER"
done
grep -q 'app.openstory' "$RUNNER"
grep -q 'Status: ok' "$RUNNER"

echo 'checkpoints/architecture-baseline-2.sh contract verified.'
