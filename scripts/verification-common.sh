#!/usr/bin/env bash
set -euo pipefail

run_repository_static_gates() {
  for test_script in ./scripts/tests/*.sh; do
    bash "$test_script"
  done

  ./scripts/verify-structural-suppressions.sh
  ./scripts/verify-package-boundaries.sh
  ./scripts/verify-source-layout.sh
  ./scripts/verify-ui-tokens.sh
  ./scripts/structural-review-report.sh
  ./scripts/verify-current-architecture.sh
}
