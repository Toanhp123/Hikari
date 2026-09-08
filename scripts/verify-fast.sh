#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$ROOT_DIR"

GRADLEW="${GRADLEW:-./gradlew}"
export GRADLEW

# shellcheck source=scripts/verification-common.sh
source ./scripts/verification-common.sh

run_repository_static_gates

"$GRADLEW" \
  --dependency-verification strict \
  verifyArchitecture \
  :build-logic:test \
  :core:common:test \
  :catalog:model:test \
  :catalog:engine:test \
  :reader:engine:test \
  :plugins:api:test \
  :app:testDebugUnitTest \
  detekt \
  --stacktrace
