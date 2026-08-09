#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="${OPENSTORY_ROOT_DIR:-$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)}"

grep -q 'Architecture Baseline 2' "$ROOT_DIR/docs/project/current-state.md"
grep -q 'Wave 06.*frozen\|do not begin Wave 06' "$ROOT_DIR/docs/project/current-state.md"
grep -q 'Architecture Baseline 2' "$ROOT_DIR/docs/implementation/current-roadmap.md"
! grep -q 'Begin Wave 06 Task 01' "$ROOT_DIR/docs/implementation/current-roadmap.md"
