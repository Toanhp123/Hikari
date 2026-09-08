#!/usr/bin/env bash
set -euo pipefail

run_repository_static_gates() {
  for test_script in ./scripts/tests/*.sh; do
    bash "$test_script"
  done

  ./scripts/verify-structural-suppressions.sh
  ./scripts/verify-source-layout.sh
  ./scripts/structural-review-report.sh
}
