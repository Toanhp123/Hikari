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
  :core:designsystem:assembleDebug \
  :catalog:domain:test \
  :catalog:storage:assembleDebug \
  :catalog:runtime:testDebugUnitTest \
  :feature:catalog:testDebugUnitTest \
  :catalog:model:test \
  :catalog:engine:test \
  :reader:engine:test \
  :plugins:api:test \
  :app:testDebugUnitTest \
  :app:lintDebug \
  detekt \
  :app:assembleDebug \
  --stacktrace
