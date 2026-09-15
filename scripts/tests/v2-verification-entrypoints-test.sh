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

if grep -Fq 'scripts/tests/*.sh' "$COMMON"; then
  fail 'Shared verification must not wildcard-discover every historical test script.'
fi

current_static_tests=(
  'v2-build-surface-test.sh'
  'v2-build-surface-step3-admission-test.sh'
  'v2-capability-admission-contract-test.sh'
  'v2-cutover-context-test.sh'
  'v2-salvage-ledger-test.sh'
  'v2-source-layout-policy-test.sh'
  'v2-core-artwork-layout-test.sh'
  'v2-feature-catalog-layout-test.sh'
  'v2-verification-entrypoints-test.sh'
)
for test_name in "${current_static_tests[@]}"; do
  grep -Fq "$test_name" "$COMMON" ||
    fail "Shared verification is missing current static contract: $test_name"
done

non_static_or_historical_tests=(
  'v2-retired-runtime-absence-test.sh'
  'v2-step2-designsystem-slice-test.sh'
  'v2-step3-build-surface-test.sh'
)
for test_name in "${non_static_or_historical_tests[@]}"; do
  if grep -Fq "$test_name" "$COMMON"; then
    fail "Shared static verification must not route historical/Gradle-backed test: $test_name"
  fi
done

classified_tests=("${current_static_tests[@]}" "${non_static_or_historical_tests[@]}")
while IFS= read -r test_path; do
  test_name="$(basename "$test_path")"
  classified=false
  for classified_name in "${classified_tests[@]}"; do
    if [[ "$test_name" == "$classified_name" ]]; then
      classified=true
      break
    fi
  done
  [[ "$classified" == true ]] ||
    fail "Verification script is not classified as current-static or historical/Gradle-backed: $test_name"
done < <(find "$ROOT_DIR/scripts/tests" -maxdepth 1 -type f -name '*.sh' | sort)
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
