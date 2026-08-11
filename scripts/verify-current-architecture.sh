#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="${REPO_ROOT:-${OPENSTORY_ROOT_DIR:-$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)}}"
SETTINGS="$ROOT_DIR/settings.gradle.kts"
POLICY="$ROOT_DIR/config/architecture/module-boundaries.json"
SUPPRESSIONS="$ROOT_DIR/config/quality/structural-suppressions.txt"
SCHEMA_DIR="$ROOT_DIR/storage/room/schemas/app.openstory.storage.room.OpenStoryDatabase"
DATABASE_SOURCE="$ROOT_DIR/storage/room/src/main/kotlin/app/openstory/storage/room/OpenStoryDatabase.kt"
BUNDLED_DESCRIPTOR_SOURCE="$ROOT_DIR/app/src/main/kotlin/app/openstory/di/BundledPlugins.kt"
BASELINE_SCHEMA_ONE_SHA256="adbd52a78feebd2eee197ccb58f0c209852ca059abd9fe1327bbfa962ba2011a"

fail() {
  echo "$1" >&2
  exit 1
}

sorted_unique() {
  sort -u
}

module_block() {
  local module="$1"
  awk -v target="\"$module\"" '
    index($0, target) && $0 ~ /:[[:space:]]*\{/ { inside = 1 }
    inside { print }
    inside && $0 ~ /^[[:space:]]*}[,]?[[:space:]]*$/ { exit }
  ' "$POLICY"
}

policy_dependencies() {
  local module="$1" field="$2" block tail values
  block="$(module_block "$module" | tr '\n' ' ')"
  tail="${block#*\"$field\"}"
  [[ "$tail" != "$block" ]] || fail "Missing $field for $module in architecture policy."
  tail="${tail#*\[}"
  values="${tail%%]*}"
  printf '%s\n' "$values" |
    grep -oE '":[a-z0-9:-]+"' 2>/dev/null |
    tr -d '"' |
    sorted_unique || true
}

policy_path() {
  local module="$1" block
  block="$(module_block "$module")"
  printf '%s\n' "$block" |
    sed -nE 's/^[[:space:]]*"path"[[:space:]]*:[[:space:]]*"([^"]+)".*/\1/p' |
    head -1
}

actual_dependencies() {
  local build_file="$1" kind="$2" configuration dependency lower
  [[ -f "$build_file" ]] || return 0

  while IFS= read -r line; do
    [[ "$line" == *'project(":'* ]] || continue
    configuration="$(printf '%s\n' "$line" | sed -nE 's/^[[:space:]]*([A-Za-z0-9_]+)[[:space:]]*\(.*/\1/p')"
    dependency="$(printf '%s\n' "$line" | grep -oE 'project\(":[a-z0-9:-]+"\)' | head -1 | sed -E 's/project\("([^"]+)"\)/\1/')"
    [[ -n "$configuration" && -n "$dependency" ]] || continue

    lower="$(printf '%s' "$configuration" | tr '[:upper:]' '[:lower:]')"
    if [[ "$lower" == *test* ]]; then
      if [[ "$kind" == "test" ]]; then
        printf '%s\n' "$dependency"
      fi
    elif [[ "$lower" == *api || "$lower" == *implementation || "$lower" == *compileonly || "$lower" == *runtimeonly ]]; then
      if [[ "$kind" == "production" ]]; then
        printf '%s\n' "$dependency"
      fi
    else
      fail "Unknown project dependency configuration in $build_file: $configuration"
    fi
  done < "$build_file" | sorted_unique
}

[[ -f "$SETTINGS" ]] || fail "Missing settings.gradle.kts."
[[ -f "$POLICY" ]] || fail "Missing module boundary policy."
[[ -f "$SUPPRESSIONS" ]] || fail "Missing structural suppression policy."
[[ -d "$SCHEMA_DIR" ]] || fail "Missing Room schema history."
[[ -f "$DATABASE_SOURCE" ]] || fail "Missing OpenStoryDatabase source."

declared_modules="$({
  grep -oE 'include\(":[a-z0-9:-]+"\)' "$SETTINGS" |
    sed -E 's/include\("([^"]+)"\)/\1/' |
    sorted_unique
} || true)"
policy_modules="$({
  grep -oE '"\:[a-z0-9:-]+"[[:space:]]*:[[:space:]]*\{' "$POLICY" |
    sed -E 's/^"([^"]+)".*/\1/' |
    sorted_unique
} || true)"

[[ -n "$policy_modules" ]] || fail "Architecture policy contains no modules."
[[ "$declared_modules" == "$policy_modules" ]] ||
  fail "settings.gradle.kts modules must match module-boundaries.json exactly."

module_count=0
while IFS= read -r module; do
  [[ -n "$module" ]] || continue
  module_count=$((module_count + 1))
  path="$(policy_path "$module")"
  [[ -n "$path" ]] || fail "Missing path for $module in architecture policy."
  [[ -d "$ROOT_DIR/$path" ]] || fail "Missing module directory for $module: $path"
  build_file="$ROOT_DIR/$path/build.gradle.kts"
  [[ -f "$build_file" ]] || fail "Missing build script for $module: $build_file"

  expected_production="$(policy_dependencies "$module" productionDependencies)"
  actual_production="$(actual_dependencies "$build_file" production)"
  [[ "$actual_production" == "$expected_production" ]] ||
    fail "Production dependencies for $module do not match module-boundaries.json."

  expected_test="$(policy_dependencies "$module" testDependencies)"
  actual_test="$(actual_dependencies "$build_file" test)"
  [[ "$actual_test" == "$expected_test" ]] ||
    fail "Test dependencies for $module do not match module-boundaries.json."
done <<< "$policy_modules"

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

[[ -f "$SCHEMA_DIR/1.json" ]] || fail "Room schema history must retain 1.json."
actual_schema_one_sha256="$(sha256sum "$SCHEMA_DIR/1.json" | awk '{ print $1 }')"
[[ "$actual_schema_one_sha256" == "$BASELINE_SCHEMA_ONE_SHA256" ]] ||
  fail "Frozen Architecture Baseline 2 Room schema 1 changed."

schema_version=1
while IFS= read -r schema_file; do
  version="$(basename "$schema_file" .json)"
  [[ "$version" =~ ^[0-9]+$ ]] || continue
  [[ "$version" == "$schema_version" ]] ||
    fail "Room schema history must be contiguous; expected $schema_version.json, found $version.json."
  schema_version=$((schema_version + 1))
done < <(find "$SCHEMA_DIR" -type f -name '*.json' -print | sort -V)
latest_schema=$((schema_version - 1))

database_version="$(
  sed -nE 's/^[[:space:]]*version[[:space:]]*=[[:space:]]*([0-9]+),[[:space:]]*$/\1/p' "$DATABASE_SOURCE" |
    head -1
)"
[[ -n "$database_version" ]] || fail "Could not read the Room database version."
[[ "$latest_schema" == "$database_version" ]] ||
  fail "Latest Room schema ($latest_schema) must match OpenStoryDatabase version ($database_version)."

BUNDLED_ASSET_DIR="$ROOT_DIR/app/src/main/assets/plugins"
[[ -f "$BUNDLED_DESCRIPTOR_SOURCE" ]] || fail "Missing production bundled plugin registry."

declared_bundled_assets="$(
  sed -nE 's/^[[:space:]]*assetPath[[:space:]]*=[[:space:]]*"plugins\/([^"/]+\.osp)".*/\1/p' \
    "$BUNDLED_DESCRIPTOR_SOURCE" | sorted_unique
)"
actual_bundled_assets="$({
  for bundled_asset in "$BUNDLED_ASSET_DIR"/*.osp; do
    [[ -f "$bundled_asset" ]] && basename "$bundled_asset"
  done | sorted_unique
} || true)"
[[ -n "$declared_bundled_assets" ]] || fail "Production bundled plugin registry contains no packages."
[[ "$actual_bundled_assets" == "$declared_bundled_assets" ]] ||
  fail "Production plugin assets must match the bundled plugin registry exactly."

[[ ! -s "$SUPPRESSIONS" ]] || fail "Structural suppression debt must be empty."

if find "$ROOT_DIR" \
  -path '*/build' -prune -o \
  -path '*/.git' -prune -o \
  -type f -name 'selector.json' -print -quit | grep -q .; then
  fail "Legacy selector package content remains."
fi

production_roots=()
while IFS= read -r module; do
  [[ -n "$module" ]] || continue
  path="$(policy_path "$module")"
  source_root="$ROOT_DIR/$path/src/main"
  [[ -d "$source_root" ]] && production_roots+=("$source_root")
done <<< "$policy_modules"

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

echo "Current architecture verified: $module_count modules, Room schema 1..$latest_schema."
