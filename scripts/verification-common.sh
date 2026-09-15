#!/usr/bin/env bash
set -euo pipefail

run_repository_static_contract_tests() {
  local test_scripts=(
    ./scripts/tests/v2-build-surface-test.sh
    ./scripts/tests/v2-build-surface-step3-admission-test.sh
    ./scripts/tests/v2-capability-admission-contract-test.sh
    ./scripts/tests/v2-cutover-context-test.sh
    ./scripts/tests/v2-salvage-ledger-test.sh
    ./scripts/tests/v2-source-layout-policy-test.sh
    ./scripts/tests/v2-core-artwork-layout-test.sh
    ./scripts/tests/v2-feature-catalog-layout-test.sh
    ./scripts/tests/v2-verification-entrypoints-test.sh
  )

  local test_script
  for test_script in "${test_scripts[@]}"; do
    bash "$test_script"
  done
}

run_repository_static_gates() {
  run_repository_static_contract_tests

  ./scripts/verify-structural-suppressions.sh
  ./scripts/verify-source-layout.sh
  ./scripts/structural-review-report.sh
}
