#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="${REPO_ROOT:-$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)}"
CONTRACT="$ROOT_DIR/docs/internal/v2/capability-admission-contract.md"

[[ -f "$CONTRACT" ]] || {
  echo "Missing capability admission contract." >&2
  exit 1
}

for id in PERF-01 PERF-02 PERF-03 PERF-04 PERF-05 PERF-06 PERF-07 PERF-08; do
  grep -Fq "$id" "$CONTRACT" || {
    echo "Missing performance rule: $id" >&2
    exit 1
  }
done

required_fields=(
  'Activation trigger and owner'
  'Deactivation / quiescence rule'
  'Production dependency graph'
  'Foreground working-set / cardinality'
  'Observer keys, invalidation scope, and lifetime'
  'CPU / I/O / network execution owner'
  'Durable / background work owner'
  'Retention / aging / eviction'
  'Failure / retry / terminal-state ownership'
  'Benchmark / scaling delta'
)

for field in "${required_fields[@]}"; do
  grep -Fq "$field" "$CONTRACT" || {
    echo "Missing admission field: $field" >&2
    exit 1
  }
done

if grep -Eiq '\b(CapabilityManager|CapabilityRegistry|ServiceLocator)\b' "$CONTRACT"; then
  echo "Admission contract must not introduce a runtime capability framework." >&2
  exit 1
fi

echo "V2 capability admission contract verified."
