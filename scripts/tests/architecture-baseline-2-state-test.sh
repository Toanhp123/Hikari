#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="${OPENSTORY_ROOT_DIR:-$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)}"

grep -q 'Architecture Baseline 2' "$ROOT_DIR/docs/project/current-state.md"
grep -q 'Architecture Baseline 2: \*\*ACCEPTED\*\*' "$ROOT_DIR/docs/project/current-state.md"
grep -q 'Wave 06 Tasks 01-04: \*\*VERIFIED\*\*' "$ROOT_DIR/docs/project/current-state.md"
grep -q 'Wave 06 Task 05' "$ROOT_DIR/docs/project/current-state.md"
grep -q 'Architecture Baseline 2' "$ROOT_DIR/docs/implementation/current-roadmap.md"
grep -q 'Tasks 01-04 verified, Task 05 active' "$ROOT_DIR/docs/implementation/current-roadmap.md"
grep -q 'Continue Wave 06 with Task 05' "$ROOT_DIR/docs/implementation/current-roadmap.md"
grep -q 'Status: ACCEPTED' "$ROOT_DIR/docs/internal/checkpoints/architecture-baseline-2.md"
grep -q 'Status: \*\*VERIFIED\*\*' "$ROOT_DIR/docs/internal/checkpoints/wave-06-task-01-metadata-only-library.md"
grep -q 'Status: \*\*VERIFIED\*\*' "$ROOT_DIR/docs/internal/checkpoints/wave-06-task-02-library-presentation.md"
grep -q 'Status: \*\*VERIFIED\*\*' "$ROOT_DIR/docs/internal/checkpoints/wave-06-task-03-content-story-matching.md"
grep -q 'Status: \*\*VERIFIED\*\*' "$ROOT_DIR/docs/internal/checkpoints/wave-06-task-04-content-source-search.md"
grep -q '^storage/room/schemas/\*\*/\*.json text eol=lf$' "$ROOT_DIR/.gitattributes"

WAVE_06="$ROOT_DIR/docs/implementation/waves/wave-06-library-and-story-matching.md"
grep -q 'Architecture Baseline 2' "$WAVE_06"
grep -q ':catalog' "$WAVE_06"
grep -q ':feature:catalog' "$WAVE_06"
grep -q ':storage:room' "$WAVE_06"
grep -q ':plugins:runtime' "$WAVE_06"
grep -q 'DOCUMENT LIFECYCLE: ACTIVE / TASKS 01-04 VERIFIED / TASK 05 NEXT' "$WAVE_06"
! grep -Eq 'feature/library|core/matching|feature/story|core/database|(^|[[:space:]])sync/|HostedPlugin<ContentPlugin>' "$WAVE_06"
