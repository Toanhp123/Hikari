#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="${REPO_ROOT:-${OPENSTORY_ROOT_DIR:-$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)}}"
SETTINGS="$ROOT_DIR/settings.gradle.kts"
POLICY="$ROOT_DIR/config/architecture/module-boundaries.json"
SUPPRESSIONS="$ROOT_DIR/config/quality/structural-suppressions.txt"
SCHEMA_DIR="$ROOT_DIR/storage/room/schemas/app.openstory.storage.room.OpenStoryDatabase"

fail() {
  echo "$1" >&2
  exit 1
}

FINAL_MODULES=(
  ':app'
  ':catalog'
  ':core:common'
  ':feature:catalog'
  ':plugins:api'
  ':plugins:runtime'
  ':storage:room'
)

module_path() {
  printf '%s' "${1#:}" | tr ':' '/'
}

[[ -f "$SETTINGS" ]] || fail "Missing settings.gradle.kts."
[[ -f "$POLICY" ]] || fail "Missing module boundary policy."
[[ -f "$SUPPRESSIONS" ]] || fail "Missing structural suppression policy."

for module in "${FINAL_MODULES[@]}"; do
  [[ -d "$ROOT_DIR/$(module_path "$module")" ]] || fail "Missing final module: $module"
  grep -Fq "\"$module\"" "$POLICY" || fail "Final module missing from architecture policy: $module"
done

declared_modules="$({
  grep -oE 'include\(":[a-z0-9:-]+"\)' "$SETTINGS" |
    sed -E 's/include\("([^"]+)"\)/\1/' |
    sort
} || true)"
expected_modules="$(printf '%s\n' "${FINAL_MODULES[@]}")"
[[ "$declared_modules" == "$expected_modules" ]] ||
  fail "settings.gradle.kts must include exactly the seven Baseline 2 modules."

for removed in \
  core/model core/database core/matching core/plugin-api core/plugin-host core/network \
  feature/home feature/story test/fixtures; do
  removed_path="$ROOT_DIR/$removed"
  [[ -e "$removed_path" ]] || continue
  if [[ ! -d "$removed_path/build" ]] || find "$removed_path" \
    -path "$removed_path/build" -prune -o \
    -type f -print -quit | grep -q .; then
    fail "Legacy module still exists: $removed"
  fi
done

schema_count=0
for schema_file in "$SCHEMA_DIR"/*.json; do
  [[ -f "$schema_file" ]] && schema_count=$((schema_count + 1))
done
[[ "$schema_count" == 1 && -f "$SCHEMA_DIR/1.json" ]] ||
  fail "Room schema directory must contain exactly 1.json."

BUNDLED_ASSET_DIR="$ROOT_DIR/app/src/main/assets/plugins"
bundled_asset_count=0
for bundled_asset in "$BUNDLED_ASSET_DIR"/*.osp; do
  [[ -f "$bundled_asset" ]] && bundled_asset_count=$((bundled_asset_count + 1))
done
[[ "$bundled_asset_count" == 1 && -f "$BUNDLED_ASSET_DIR/myanimelist-catalog.osp" ]] ||
  fail "Production assets must contain exactly the canonical MyAnimeList plugin package."

[[ ! -s "$SUPPRESSIONS" ]] || fail "Structural suppression debt must be empty."

if find "$ROOT_DIR" \
  -path '*/build' -prune -o \
  -path '*/.git' -prune -o \
  -type f -name 'selector.json' -print -quit | grep -q .; then
  fail "Legacy selector package content remains."
fi

production_roots=()
for module in "${FINAL_MODULES[@]}"; do
  source_root="$ROOT_DIR/$(module_path "$module")/src/main"
  [[ -d "$source_root" ]] && production_roots+=("$source_root")
done

if ((${#production_roots[@]} > 0)); then
  if grep -RInE --include='*.kt' \
    '@(file:)?Suppress\("(LargeClass|LongMethod|TooManyFunctions|CyclomaticComplexMethod|ComplexMethod|LongParameterList|NestedBlockDepth)"' \
    "${production_roots[@]}" >/dev/null; then
    fail "Production structural suppression remains."
  fi
  if grep -RInE --include='*.kt' \
    'app\.openstory\.(plugin\.(api|host)|model)(\.|$)|(^|[^A-Za-z0-9_])(AppResult|AppError)([^A-Za-z0-9_]|$)' \
    "${production_roots[@]}" >/dev/null; then
    fail "Legacy production package or result contract remains."
  fi
fi

echo "Architecture Baseline 2 verified."
