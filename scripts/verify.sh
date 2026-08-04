#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$ROOT_DIR"

./scripts/check-module-dependencies.sh

./gradlew --no-daemon \
  --dependency-verification strict \
  :build-logic:test \
  test \
  testDebugUnitTest \
  lintDebug \
  detekt \
  :app:assembleDebug \
  --stacktrace
