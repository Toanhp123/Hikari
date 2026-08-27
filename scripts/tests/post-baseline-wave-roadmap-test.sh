#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="${OPENSTORY_ROOT_DIR:-$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)}"
WAVE_DIR="$ROOT_DIR/docs/implementation/waves"
DESIGN="$ROOT_DIR/docs/superpowers/specs/2026-08-10-post-baseline-wave-06-11-architecture-design.md"
WAVE_10_SPEC="$ROOT_DIR/docs/superpowers/specs/2026-08-24-wave-10-clean-background-auth-notifications-design.md"

[[ -f "$DESIGN" ]]
grep -q 'Status: APPROVED' "$DESIGN"
[[ -f "$WAVE_10_SPEC" ]]
grep -q 'Status: \*\*APPROVED FOR PLAN REBASELINE\*\*' "$WAVE_10_SPEC"

WAVES=()
for number in 06 07 08 09 10 11; do
  matches=("$WAVE_DIR"/wave-"$number"-*.md)
  [[ "${#matches[@]}" -eq 1 && -f "${matches[0]}" ]]
  WAVES+=("${matches[0]}")
  if [[ "$number" == 10 ]]; then
    grep -q '2026-08-24-wave-10-clean-background-auth-notifications-design.md' "${matches[0]}"
    grep -q 'DOCUMENT LIFECYCLE: VERIFIED / CLOSED' "${matches[0]}"
    grep -q '## Entry Baseline' "${matches[0]}"
    grep -q '## Exit Baseline' "${matches[0]}"
    grep -q '## Global Invariants' "${matches[0]}"
  else
    grep -q '2026-08-10-post-baseline-wave-06-11-architecture-design.md' "${matches[0]}"
    grep -q 'Entry module graph' "${matches[0]}"
    grep -q 'Exit module graph' "${matches[0]}"
    grep -q 'Deep ownership review' "${matches[0]}"
  fi
done

if grep -EIn \
  'core/model|core/database|core/matching|core/plugin-host|core/plugin-api|feature/home|feature/story|feature/library|(^|[[:space:]])sync/|:sync([^a-z]|$)|HostedPlugin<|CatalogPlugin|ContentPlugin' \
  "${WAVES[@]}" | grep -v 'Do not introduce a generic `:sync` module'; then
  echo 'Active post-baseline wave plans still reference removed architecture.' >&2
  exit 1
fi

grep -q 'Introduces `:library`' "${WAVES[0]}"
grep -q 'Introduces `:chapters`' "${WAVES[1]}"
grep -q 'Introduces `:reader` and `:feature:reader`' "${WAVES[2]}"
grep -q 'Introduces `:downloads` and `:storage:files`' "${WAVES[3]}"
grep -q 'Production graph: exactly 16 modules by adding only `:settings` and `:feature:settings`.' "${WAVES[4]}"
grep -q 'Introduces `:feature:plugins`' "${WAVES[5]}"

grep -q 'Consumes from Wave 06' "${WAVES[1]}"
grep -q 'Consumes from Wave 07' "${WAVES[2]}"
grep -q 'Consumes from Wave 08' "${WAVES[3]}"
grep -q 'Production graph: 14 modules.' "${WAVES[4]}"
grep -q 'Consumes from Wave 10' "${WAVES[5]}"

# Enforce the concrete cross-wave contracts that make the sequence executable.
grep -q 'verify-current-architecture.sh' "${WAVES[0]}"
grep -q 'switch `verify.sh` to `verify-current-architecture.sh`' "${WAVES[0]}"
grep -q 'ContentChapterListModeDto' "${WAVES[1]}"
grep -q 'RECENT.*FULL.*INCREMENTAL' "${WAVES[1]}"
grep -q 'ReaderDocumentStore.*write' "${WAVES[2]}"
grep -q 'sanitized network write-through' "${WAVES[2]}"
grep -q 'successful sanitized network reads write into automatic cache' "${WAVES[3]}"
grep -q 'LibraryMappingScheduler' "${WAVES[4]}"
grep -q 'DownloadScheduler' "${WAVES[4]}"
grep -q 'BackgroundWorkSchedulePort' "${WAVES[4]}"
! grep -q 'feature-owned UI over .*runtime management' "${WAVES[4]}"

grep -q 'schema `1 -> 2`' "${WAVES[0]}"
grep -q 'schema `2 -> 3`' "${WAVES[0]}"
grep -q 'schema `3 -> 4`' "${WAVES[1]}"
grep -q 'schema `4 -> 5`' "${WAVES[2]}"
grep -q 'schema `5 -> 6`' "${WAVES[3]}"
grep -q 'MIGRATION_10_11' "${WAVES[4]}"
grep -q 'Room schema 11 remains stable' "${WAVES[5]}"

for index in "${!WAVES[@]}"; do
  wave="${WAVES[$index]}"
  if [[ "$index" -eq 4 ]]; then
    [[ "$(grep -c '^### Task ' "$wave")" -eq 48 ]]
    [[ "$(grep -c '\./gradlew\|\./scripts/' "$wave")" -ge 3 ]]
  else
    [[ "$(grep -c '^### Task ' "$wave")" -eq 6 ]]
    [[ "$(grep -c '\./gradlew\|\./scripts/' "$wave")" -ge 6 ]]
  fi
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
grep -q ':core:designsystem' "$ROOT_DIR/docs/implementation/current-roadmap.md"
grep -q '| UI foundation | `:core:designsystem` |' "$DESIGN"
grep -q 'Production graph: exactly 16 modules by adding only `:settings` and `:feature:settings`.' "${WAVES[4]}"
grep -q 'Exit module graph: entry graph plus `:feature:plugins`.' "${WAVES[5]}"

# Preserve the approved product-navigation handoff into the future capability waves.
grep -q 'Settings is a utility route, never a top-level destination.' "${WAVES[4]}"
grep -q 'Wave 11 Plugin Management enters through the avatar utility sheet and never top-level navigation.' "${WAVES[5]}"
grep -q 'Discover / Home / Library remains the final top-level model.' "$ROOT_DIR/docs/implementation/current-roadmap.md"
grep -q 'Discover / Home / Library remains the final top-level model.' "$DESIGN"

# Future screens must compose through their owning feature and the existing app shell.
grep -q 'AppRoute.Settings' "${WAVES[4]}"
grep -q 'HikariUtilitySheet' "${WAVES[4]}"
grep -q 'TopLevelDestination' "${WAVES[4]}"
grep -q 'HikariUtilitySheet' "${WAVES[5]}"
grep -q 'installed/detail/install/update/rollback' "${WAVES[5]}"
grep -q 'approved Plugin target screen' "${WAVES[5]}"

echo 'Post-Baseline Wave 06-11 roadmap contract verified.'
