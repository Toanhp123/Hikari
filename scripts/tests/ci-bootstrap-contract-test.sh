#!/usr/bin/env bash
set -euo pipefail
ROOT="$(cd "$(dirname "$0")/../.." && pwd)"
CI="$ROOT/.github/workflows/ci.yml"

fail() {
  echo "FAIL: $*" >&2
  exit 1
}

grep -Fq 'branches: [ master ]' "$CI" || fail "push trigger must target canonical default branch master"
! grep -Fq 'branches: [ main ]' "$CI" || fail "stale main-only push trigger would skip the canonical master branch"

COUNT_SETUP_ANDROID="$(grep -c 'uses: android-actions/setup-android@v3' "$CI" || true)"
[[ "$COUNT_SETUP_ANDROID" -eq 2 ]] || fail "expected setup-android in both CI jobs, found $COUNT_SETUP_ANDROID"

COUNT_CMDLINE_TOOLS="$(grep -c "cmdline-tools-version: '15859902'" "$CI" || true)"
[[ "$COUNT_CMDLINE_TOOLS" -eq 2 ]] || fail "expected command-line tools 15859902 in both CI jobs, found $COUNT_CMDLINE_TOOLS"

COUNT_PLATFORM37="$(grep -c 'platforms;android-37.0' "$CI" || true)"
[[ "$COUNT_PLATFORM37" -eq 2 ]] || fail "expected explicit compileSdk 37 install in both CI jobs, found $COUNT_PLATFORM37"

COUNT_BUILD_TOOLS="$(grep -c 'build-tools;37.0.0' "$CI" || true)"
[[ "$COUNT_BUILD_TOOLS" -eq 2 ]] || fail "expected build-tools 37.0.0 install in both CI jobs, found $COUNT_BUILD_TOOLS"

! grep -Fq 'api-level: [23, 37]' "$CI" || fail "legacy major-only API 37 matrix would request android-37 instead of android-37.0"
! grep -Eq "^[[:space:]]+- api-level: ['\"]?37['\"]?[[:space:]]*$" "$CI" || fail "major-only API 37 matrix entry is forbidden; use Android 17 package 37.0"
COUNT_MATRIX_APIS="$(grep -Ec "^[[:space:]]+- api-level:" "$CI" || true)"
[[ "$COUNT_MATRIX_APIS" -eq 2 ]] || fail "expected exactly two instrumentation matrix API entries, found $COUNT_MATRIX_APIS"
grep -A1 -F "api-level: '23'" "$CI" | grep -Fq 'target: default' || fail "instrumentation matrix must pair API 23 with the default target"
grep -A1 -F "api-level: '37.0'" "$CI" | grep -Fq 'target: google_apis' || fail "instrumentation matrix must pair Android 17 package 37.0 with google_apis"
grep -Fq 'api-level: ${{ matrix.api-level }}' "$CI" || fail "emulator runner must consume the matrix API level"
grep -Fq 'target: ${{ matrix.target }}' "$CI" || fail "emulator runner must consume the matrix target"

grep -Fq 'name: Enable KVM group perms' "$CI" || fail "instrumentation job does not enable KVM"
grep -Fq '99-kvm4all.rules' "$CI" || fail "KVM udev permission rule is missing"
grep -Fq 'sudo udevadm trigger --name-match=kvm' "$CI" || fail "KVM udev rule is not activated"

grep -q 'bash scripts/verify-bootstrap.sh --host-only' "$CI" || fail "verify job does not exercise canonical host bootstrap gate"
for contract_test in bootstrap-execution-harness-test.sh ci-bootstrap-contract-test.sh gradle-bootstrap-contract-test.sh; do
  grep -Fq "bash scripts/tests/$contract_test" "$CI" || fail "verify job does not run $contract_test"
done

echo "ci-bootstrap-contract-test: PASS"
