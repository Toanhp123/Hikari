#!/usr/bin/env bash
set -euo pipefail
ROOT="$(cd "$(dirname "$0")/../.." && pwd)"
CI="$ROOT/.github/workflows/ci.yml"

fail() {
  echo "FAIL: $*" >&2
  exit 1
}

COUNT_SETUP_ANDROID="$(grep -c 'uses: android-actions/setup-android@v3' "$CI" || true)"
[[ "$COUNT_SETUP_ANDROID" -eq 2 ]] || fail "expected setup-android in both CI jobs, found $COUNT_SETUP_ANDROID"

COUNT_PLATFORM37="$(grep -c 'platforms;android-37.0' "$CI" || true)"
[[ "$COUNT_PLATFORM37" -eq 2 ]] || fail "expected explicit compileSdk 37 install in both CI jobs, found $COUNT_PLATFORM37"

COUNT_BUILD_TOOLS="$(grep -c 'build-tools;37.0.0' "$CI" || true)"
[[ "$COUNT_BUILD_TOOLS" -eq 2 ]] || fail "expected build-tools 37.0.0 install in both CI jobs, found $COUNT_BUILD_TOOLS"

grep -q 'bash scripts/verify-bootstrap.sh --host-only' "$CI" || fail "verify job does not exercise canonical host bootstrap gate"
for contract_test in bootstrap-execution-harness-test.sh ci-bootstrap-contract-test.sh gradle-bootstrap-contract-test.sh; do
  grep -Fq "bash scripts/tests/$contract_test" "$CI" || fail "verify job does not run $contract_test"
done

echo "ci-bootstrap-contract-test: PASS"
