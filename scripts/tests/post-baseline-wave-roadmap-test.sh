#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="${OPENSTORY_ROOT_DIR:-$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)}"
WAVE_DIR="$ROOT_DIR/docs/implementation/waves"
DESIGN="$ROOT_DIR/docs/superpowers/specs/2026-08-10-post-baseline-wave-06-11-architecture-design.md"

[[ -f "$DESIGN" ]]
grep -q 'Status: APPROVED' "$DESIGN"

WAVES=()
for number in 06 07 08 09 10 11; do
  matches=("$WAVE_DIR"/wave-"$number"-*.md)
  [[ "${#matches[@]}" -eq 1 && -f "${matches[0]}" ]]
  WAVES+=("${matches[0]}")
  grep -q '2026-08-10-post-baseline-wave-06-11-architecture-design.md' "${matches[0]}"
  grep -q 'Entry module graph' "${matches[0]}"
  grep -q 'Exit module graph' "${matches[0]}"
  grep -q 'Deep ownership review' "${matches[0]}"
done

if grep -EIn \
  'core/model|core/database|core/matching|core/plugin-host|core/plugin-api|feature/home|feature/story|feature/library|(^|[[:space:]])sync/|:sync([^a-z]|$)|HostedPlugin<|CatalogPlugin|ContentPlugin' \
  "${WAVES[@]}"; then
  echo 'Active post-baseline wave plans still reference removed architecture.' >&2
  exit 1
fi

grep -q 'Introduces `:library`' "${WAVES[0]}"
grep -q 'Introduces `:chapters`' "${WAVES[1]}"
grep -q 'Introduces `:reader` and `:feature:reader`' "${WAVES[2]}"
grep -q 'Introduces `:downloads` and `:storage:files`' "${WAVES[3]}"
grep -q 'Introduces `:settings` and `:feature:settings`' "${WAVES[4]}"
grep -q 'Introduces `:feature:plugins`' "${WAVES[5]}"

grep -q 'Consumes from Wave 06' "${WAVES[1]}"
grep -q 'Consumes from Wave 07' "${WAVES[2]}"
grep -q 'Consumes from Wave 08' "${WAVES[3]}"
grep -q 'Consumes from Wave 09' "${WAVES[4]}"
grep -q 'Consumes from Wave 10' "${WAVES[5]}"

# Enforce the concrete cross-wave contracts that make the sequence executable.
grep -q 'verify-current-architecture.sh' "${WAVES[0]}"
grep -q 'switch `verify.sh` to `verify-current-architecture.sh`' "${WAVES[0]}"
grep -q 'ContentChapterListModeDto' "${WAVES[1]}"
grep -q 'RECENT.*FULL.*INCREMENTAL' "${WAVES[1]}"
grep -q 'ReaderDocumentStore.*write' "${WAVES[2]}"
grep -q 'sanitized network write-through' "${WAVES[2]}"
grep -q 'successful sanitized network reads write into automatic cache' "${WAVES[3]}"
grep -q 'LibraryWorkSchedulePort' "${WAVES[4]}"
grep -q 'ChapterWorkSchedulePort' "${WAVES[4]}"
grep -q 'DownloadWorkSchedulePort' "${WAVES[4]}"
grep -q 'SettingsWorkSchedulePort' "${WAVES[4]}"
! grep -q 'feature-owned UI over .*runtime management' "${WAVES[4]}"

grep -q 'schema `1 -> 2`' "${WAVES[0]}"
grep -q 'schema `2 -> 3`' "${WAVES[0]}"
grep -q 'schema `3 -> 4`' "${WAVES[1]}"
grep -q 'schema `4 -> 5`' "${WAVES[2]}"
grep -q 'schema `5 -> 6`' "${WAVES[3]}"
grep -q 'schema `6 -> 7`' "${WAVES[4]}"
grep -q 'Room schema 7 remains stable' "${WAVES[5]}"

for wave in "${WAVES[@]}"; do
  [[ "$(grep -c '^### Task ' "$wave")" -eq 6 ]]
  [[ "$(grep -c '\./gradlew\|\./scripts/' "$wave")" -ge 6 ]]
done

for canonical in \
  "$ROOT_DIR/docs/README.md" \
  "$ROOT_DIR/docs/PROJECT-HANDBOOK.md" \
  "$ROOT_DIR/docs/project/current-state.md" \
  "$ROOT_DIR/docs/project/document-governance.md" \
  "$ROOT_DIR/docs/implementation/current-roadmap.md"; do
  grep -q '2026-08-10-post-baseline-wave-06-11-architecture-design.md' "$canonical"
done

grep -q '| 06 | `:library`' "$ROOT_DIR/docs/implementation/current-roadmap.md"
grep -q '| 11 | `:feature:plugins`' "$ROOT_DIR/docs/implementation/current-roadmap.md"

echo 'Post-Baseline Wave 06-11 roadmap contract verified.'
