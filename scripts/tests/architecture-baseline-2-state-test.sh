#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="${OPENSTORY_ROOT_DIR:-$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)}"

grep -q 'Architecture Baseline 2' "$ROOT_DIR/docs/project/current-state.md"
grep -q 'Architecture Baseline 2: \*\*ACCEPTED\*\*' "$ROOT_DIR/docs/project/current-state.md"
grep -q 'Wave 06 Task 01' "$ROOT_DIR/docs/project/current-state.md"
grep -q 'Architecture Baseline 2' "$ROOT_DIR/docs/implementation/current-roadmap.md"
grep -q 'Ready to start at Task 01' "$ROOT_DIR/docs/implementation/current-roadmap.md"
grep -q 'Begin Wave 06 with Task 01' "$ROOT_DIR/docs/implementation/current-roadmap.md"
grep -q 'Status: ACCEPTED' "$ROOT_DIR/docs/internal/checkpoints/architecture-baseline-2.md"

WAVE_06="$ROOT_DIR/docs/implementation/waves/wave-06-library-and-story-matching.md"
grep -q 'Architecture Baseline 2' "$WAVE_06"
grep -q ':catalog' "$WAVE_06"
grep -q ':feature:catalog' "$WAVE_06"
grep -q ':storage:room' "$WAVE_06"
grep -q ':plugins:runtime' "$WAVE_06"
! grep -Eq 'feature/library|core/matching|feature/story|core/database|(^|[[:space:]])sync/|HostedPlugin<ContentPlugin>' "$WAVE_06"
