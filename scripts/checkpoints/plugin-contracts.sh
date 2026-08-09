#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
cd "$ROOT_DIR"

GRADLEW="${GRADLEW:-./gradlew}"
export GRADLEW

./scripts/verify.sh

"$GRADLEW" --no-daemon \
  --dependency-verification strict \
  :plugins:api:test \
  :plugins:runtime:testDebugUnitTest \
  :catalog:testDebugUnitTest \
  :test:fixtures:test \
  --stacktrace

echo "Plugin contract checkpoint passed."
