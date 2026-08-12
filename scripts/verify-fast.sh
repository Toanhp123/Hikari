#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$ROOT_DIR"

GRADLEW="${GRADLEW:-./gradlew}"
export GRADLEW

# shellcheck source=scripts/verification-common.sh
source ./scripts/verification-common.sh

run_repository_static_gates

ROOM_SCHEMA_FINGERPRINT="$(
  ./scripts/verify-room-schema-stability.sh
)"

"$GRADLEW" \
  --dependency-verification strict \
  verifyArchitecture \
  :build-logic:test \
  test \
  testDebugUnitTest \
  detekt \
  --stacktrace

./scripts/verify-room-schema-stability.sh \
  "$ROOM_SCHEMA_FINGERPRINT"
