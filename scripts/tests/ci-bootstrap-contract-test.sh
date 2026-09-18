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

COUNT_SETUP_ANDROID="$(grep -c 'uses: android-actions/setup-android@v4' "$CI" || true)"
[[ "$COUNT_SETUP_ANDROID" -eq 2 ]] || fail "expected setup-android@v4 in both CI jobs, found $COUNT_SETUP_ANDROID"
! grep -Fq 'uses: android-actions/setup-android@v3' "$CI" || fail "setup-android@v3 requests the removed legacy tools package with current command-line tools"

COUNT_SETUP_ANDROID_PACKAGES="$(grep -c "packages: 'platform-tools'" "$CI" || true)"
[[ "$COUNT_SETUP_ANDROID_PACKAGES" -eq 2 ]] || fail "expected setup-android to request platform-tools only in both CI jobs, found $COUNT_SETUP_ANDROID_PACKAGES"
! grep -Eq "packages:[[:space:]]*['\"]?tools([[:space:]]|['\"]|$)" "$CI" || fail "legacy Android SDK package tools is forbidden; Google no longer serves it"

COUNT_CMDLINE_TOOLS="$(grep -c "cmdline-tools-version: '15859902'" "$CI" || true)"
[[ "$COUNT_CMDLINE_TOOLS" -eq 2 ]] || fail "expected command-line tools 15859902 in both CI jobs, found $COUNT_CMDLINE_TOOLS"

COUNT_ALIGN_CMDLINE_TOOLS="$(grep -c 'name: Align emulator runner command-line tools' "$CI" || true)"
[[ "$COUNT_ALIGN_CMDLINE_TOOLS" -eq 1 ]] || fail "expected exactly one emulator-runner cmdline-tools alignment step, found $COUNT_ALIGN_CMDLINE_TOOLS"
ALIGN_LINE="$(grep -n 'name: Align emulator runner command-line tools' "$CI" | cut -d: -f1)"
EMULATOR_RUNNER_LINE="$(grep -n 'uses: reactivecircus/android-emulator-runner@v2' "$CI" | cut -d: -f1)"
[[ "$ALIGN_LINE" -lt "$EMULATOR_RUNNER_LINE" ]] || fail "cmdline-tools alignment must run before android-emulator-runner"
grep -Fq 'PINNED_SDKMANAGER="$(command -v sdkmanager)"' "$CI" || fail "emulator toolchain alignment must derive the sdkmanager installed by setup-android"
grep -Fq 'test "$PINNED_VERSION" = "22.0"' "$CI" || fail "emulator toolchain alignment must verify command-line tools 22.0"
grep -Fq 'sudo rm -rf "$SDK/cmdline-tools/latest"' "$CI" || fail "stale cmdline-tools/latest must be removed before emulator-runner"
grep -Fq 'sudo ln -s "$PINNED_ROOT" "$SDK/cmdline-tools/latest"' "$CI" || fail "cmdline-tools/latest must point at the pinned setup-android toolchain"

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
