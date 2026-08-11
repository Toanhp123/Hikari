#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="${OPENSTORY_ROOT_DIR:-$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)}"

grep -q 'Architecture Baseline 2' "$ROOT_DIR/docs/project/current-state.md"
grep -q 'Architecture Baseline 2: \*\*ACCEPTED\*\*' "$ROOT_DIR/docs/project/current-state.md"
grep -q 'Wave 06 Tasks 01-06: \*\*VERIFIED\*\*' "$ROOT_DIR/docs/project/current-state.md"
grep -q 'Wave 07 Tasks 01-06: \*\*VERIFIED\*\*' "$ROOT_DIR/docs/project/current-state.md"
grep -q 'Wave 08 Task 01' "$ROOT_DIR/docs/project/current-state.md"
grep -q 'schema 4 current' "$ROOT_DIR/docs/project/current-state.md"
grep -q 'Architecture Baseline 2' "$ROOT_DIR/docs/implementation/current-roadmap.md"
grep -q 'Completed; Tasks 01-06 verified' "$ROOT_DIR/docs/implementation/current-roadmap.md"
grep -q 'Ready to start; Task 01 next' "$ROOT_DIR/docs/implementation/current-roadmap.md"
grep -q 'Continue Wave 08 with Task 01' "$ROOT_DIR/docs/implementation/current-roadmap.md"
grep -q 'Status: ACCEPTED' "$ROOT_DIR/docs/internal/checkpoints/architecture-baseline-2.md"
grep -q 'Status: \*\*VERIFIED\*\*' "$ROOT_DIR/docs/internal/checkpoints/wave-06-task-01-metadata-only-library.md"
grep -q 'Status: \*\*VERIFIED\*\*' "$ROOT_DIR/docs/internal/checkpoints/wave-06-task-02-library-presentation.md"
grep -q 'Status: \*\*VERIFIED\*\*' "$ROOT_DIR/docs/internal/checkpoints/wave-06-task-03-content-story-matching.md"
grep -q 'Status: \*\*VERIFIED\*\*' "$ROOT_DIR/docs/internal/checkpoints/wave-06-task-04-content-source-search.md"
grep -q 'Status: \*\*VERIFIED\*\*' "$ROOT_DIR/docs/internal/checkpoints/wave-06-task-05-protected-content-mappings.md"
grep -q 'Status: \*\*VERIFIED\*\*' "$ROOT_DIR/docs/internal/checkpoints/wave-06-task-06-mapping-review-url-import.md"
grep -q '^storage/room/schemas/\*\*/\*.json text eol=lf$' "$ROOT_DIR/.gitattributes"

WAVE_06="$ROOT_DIR/docs/implementation/waves/wave-06-library-and-story-matching.md"
grep -q 'Architecture Baseline 2' "$WAVE_06"
grep -q ':catalog' "$WAVE_06"
grep -q ':feature:catalog' "$WAVE_06"
grep -q ':storage:room' "$WAVE_06"
grep -q ':plugins:runtime' "$WAVE_06"
grep -q 'DOCUMENT LIFECYCLE: HISTORICAL / TASKS 01-06 VERIFIED / WAVE COMPLETE' "$WAVE_06"

WAVE_07="$ROOT_DIR/docs/implementation/waves/wave-07-chapter-sync-and-aggregation.md"
grep -q 'DOCUMENT LIFECYCLE: VERIFIED / COMPLETE' "$WAVE_07"
grep -q 'Introduces `:chapters`' "$WAVE_07"
grep -q 'Room schema 3 is the entry' "$WAVE_07"

! grep -Eq 'feature/library|core/matching|feature/story|core/database|(^|[[:space:]])sync/|HostedPlugin<ContentPlugin>' "$WAVE_06"
