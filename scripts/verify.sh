#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$ROOT_DIR"

GRADLEW="${GRADLEW:-./gradlew}"
export GRADLEW

for test_script in ./scripts/tests/*.sh; do
  bash "$test_script"
done

ROOM_SCHEMA_FINGERPRINT="$(
  ./scripts/verify-room-schema-stability.sh
)"

./scripts/check-module-dependencies.sh

"$GRADLEW" --no-daemon \
  --dependency-verification strict \
  :build-logic:test \
  test \
  testDebugUnitTest \
  lintDebug \
  detekt \
  :app:assembleDebug \
  --stacktrace

./scripts/verify-room-schema-stability.sh \
  "$ROOM_SCHEMA_FINGERPRINT"
