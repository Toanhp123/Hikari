#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="${REPO_ROOT:-$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)}"
COMMON="$ROOT_DIR/scripts/verification-common.sh"

fail() {
  echo "$1" >&2
  exit 1
}

required_tasks=(
  'verifyArchitecture'
  ':build-logic:test'
  ':core:common:test'
  ':core:designsystem:assembleDebug'
  ':catalog:domain:test'
  ':catalog:storage:assembleDebug'
  ':catalog:runtime:testDebugUnitTest'
  ':feature:catalog:testDebugUnitTest'
  ':catalog:model:test'
  ':catalog:engine:test'
  ':reader:engine:test'
  ':plugins:api:test'
  ':app:testDebugUnitTest'
  'detekt'
)

for entry in scripts/verify.sh scripts/verify-fast.sh; do
  entry_path="$ROOT_DIR/$entry"
  [[ -f "$entry_path" ]] || fail "Missing V2 verification entrypoint: $entry"
  grep -Fq 'run_repository_static_gates' "$entry_path" ||
    fail "$entry does not run repository static gates."
  grep -Fq 'verifyArchitecture' "$entry_path" ||
    fail "$entry does not run the authoritative V2 architecture gate."
  for task in "${required_tasks[@]}"; do
    grep -Fq "$task" "$entry_path" || fail "$entry is missing V2 Gradle task: $task"
  done
  if grep -Eqi \
    'room-schema|wave-10|verify-current-architecture|verify-package-boundaries' \
    "$entry_path"; then
    fail "V1 verification call remains in $entry."
  fi
done

grep -Fq ':app:lintDebug' "$ROOT_DIR/scripts/verify.sh" ||
  fail 'scripts/verify.sh is missing app lint.'
grep -Fq ':app:assembleDebug' "$ROOT_DIR/scripts/verify.sh" ||
  fail 'scripts/verify.sh is missing debug assembly.'
if grep -Eq ':app:(lintDebug|assembleDebug)' "$ROOT_DIR/scripts/verify-fast.sh"; then
  fail 'scripts/verify-fast.sh must leave lint and assembly to the full gate.'
fi

[[ -f "$COMMON" ]] || fail 'Missing scripts/verification-common.sh.'
for gate in \
  './scripts/verify-structural-suppressions.sh' \
  './scripts/verify-source-layout.sh' \
  './scripts/structural-review-report.sh'; do
  grep -Fq "$gate" "$COMMON" || fail "Shared verification is missing gate: $gate"
done
if grep -Eqi \
  'room-schema|wave-10|verify-current-architecture|verify-package-boundaries|verify-ui-tokens' \
  "$COMMON"; then
  fail 'Shared verification still names a V1-only static gate.'
fi

echo "V2 verification entrypoints verified."
