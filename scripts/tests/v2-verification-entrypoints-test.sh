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

grep -Fq 'verifyStep3FastModules' "$ROOT_DIR/scripts/verify-fast.sh" ||
  fail 'scripts/verify-fast.sh is missing the live Step 3 fast module aggregate.'
grep -Fq 'verifyStep3FullModules' "$ROOT_DIR/scripts/verify.sh" ||
  fail 'scripts/verify.sh is missing the live Step 3 full module aggregate.'

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
