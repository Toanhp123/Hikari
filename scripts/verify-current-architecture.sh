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
HES_V1_PRODUCTION_MODULES=17
HES_V1_ANDROID_TEST_MODULES=1
RICC_V1_ROOM_SCHEMA=12

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

policy_dependency_mode() {
  local module="$1" block
  block="$(module_block "$module")"
  printf '%s\n' "$block" |
    sed -nE 's/^[[:space:]]*"dependencyMode"[[:space:]]*:[[:space:]]*"([^"]+)".*/\1/p' |
    head -1
}

policy_platform() {
  local module="$1" block
  block="$(module_block "$module")"
  printf '%s\n' "$block" |
    sed -nE 's/^[[:space:]]*"platform"[[:space:]]*:[[:space:]]*"([^"]+)".*/\1/p' |
    head -1
}

dependency_set_allowed() {
  local actual="$1" expected="$2" mode="$3" dependency
  if [[ "$mode" == "exact" ]]; then
    [[ "$actual" == "$expected" ]]
    return
  fi
  [[ "$mode" == "allowlist" ]] || fail "Unknown dependencyMode: $mode"
  while IFS= read -r dependency; do
    [[ -n "$dependency" ]] || continue
    grep -Fxq "$dependency" <<< "$expected" || return 1
  done <<< "$actual"
}

actual_dependencies() {
  local build_file="$1" kind="$2" configuration dependency lower
  [[ -f "$build_file" ]] || return 0

  while IFS= read -r line; do
    [[ "$line" == *'project(":'* ]] || continue
    configuration="$(printf '%s\n' "$line" | sed -nE 's/^[[:space:]]*"?([A-Za-z0-9_]+)"?[[:space:]]*\(.*/\1/p')"
    dependency="$(printf '%s\n' "$line" | grep -oE 'project\(":[a-z0-9:-]+"\)' | head -1 | sed -E 's/project\("([^"]+)"\)/\1/')"
    [[ -n "$configuration" && -n "$dependency" ]] || continue

    lower="$(printf '%s' "$configuration" | tr '[:upper:]' '[:lower:]')"
    if [[ "$lower" == *test* || "$lower" == "baselineprofile" ]]; then
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

production_module_count=0
android_test_module_count=0
while IFS= read -r module; do
  [[ -n "$module" ]] || continue
  platform="$(policy_platform "$module")"
  if [[ "$platform" == "android-test" ]]; then
    android_test_module_count=$((android_test_module_count + 1))
  else
    production_module_count=$((production_module_count + 1))
  fi
  path="$(policy_path "$module")"
  [[ -n "$path" ]] || fail "Missing path for $module in architecture policy."
  [[ -d "$ROOT_DIR/$path" ]] || fail "Missing module directory for $module: $path"
  build_file="$ROOT_DIR/$path/build.gradle.kts"
  [[ -f "$build_file" ]] || fail "Missing build script for $module: $build_file"

  dependency_mode="$(policy_dependency_mode "$module")"
  expected_production="$(policy_dependencies "$module" productionDependencies)"
  actual_production="$(actual_dependencies "$build_file" production)"
  dependency_set_allowed "$actual_production" "$expected_production" "$dependency_mode" ||
    fail "Production dependencies for $module do not match module-boundaries.json."

  expected_test="$(policy_dependencies "$module" testDependencies)"
  actual_test="$(actual_dependencies "$build_file" test)"
  dependency_set_allowed "$actual_test" "$expected_test" "$dependency_mode" ||
    fail "Test dependencies for $module do not match module-boundaries.json."
done <<< "$policy_modules"

[[ "$production_module_count" -eq "$HES_V1_PRODUCTION_MODULES" ]] ||
  fail "HES-v1 requires exactly $HES_V1_PRODUCTION_MODULES production modules."
[[ "$android_test_module_count" -eq "$HES_V1_ANDROID_TEST_MODULES" ]] ||
  fail "HES-v1 requires exactly $HES_V1_ANDROID_TEST_MODULES android-test module."

reader_engine_platform="$(policy_platform ':reader:engine')"
[[ "$reader_engine_platform" == "jvm" ]] || fail ":reader:engine must remain JVM-only."
[[ "$(policy_dependency_mode ':reader:engine')" == "exact" ]] ||
  fail ":reader:engine must keep exact dependency policy."
[[ "$(policy_dependencies ':reader:engine' productionDependencies)" == ":core:common" ]] ||
  fail ":reader:engine production dependencies must remain exactly :core:common."
reader_dependencies="$(policy_dependencies ':reader' productionDependencies)"
grep -Fxq ':reader:engine' <<< "$reader_dependencies" || fail ":reader must consume :reader:engine."
if grep -Fxq ':settings' <<< "$reader_dependencies"; then
  fail ":reader must not depend on :settings."
fi
feature_reader_dependencies="$(policy_dependencies ':feature:reader' productionDependencies)"
if grep -Fxq ':downloads' <<< "$feature_reader_dependencies"; then
  fail ":feature:reader must not depend on :downloads."
fi
reader_build="$ROOT_DIR/reader/build.gradle.kts"
grep -Fq 'implementation(project(":reader:engine"))' "$reader_build" ||
  fail ":reader must consume :reader:engine with implementation()."
if grep -Fq 'api(project(":reader:engine"))' "$reader_build"; then
  fail ":reader must not expose :reader:engine with api()."
fi
feature_reader_build="$ROOT_DIR/feature/reader/build.gradle.kts"
if grep -Fq 'project(":downloads")' "$feature_reader_build"; then
  fail ":feature:reader must not declare a :downloads dependency."
fi
if grep -RInE --include='*.kt' '(ReaderAsset|RICC|ImageContinuity)' \
  "$ROOT_DIR/reader/engine/src" >/dev/null; then
  fail "RICC code must not exist under :reader:engine."
fi

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
[[ "$latest_schema" -eq "$RICC_V1_ROOM_SCHEMA" ]] ||
  fail "RICC-v1 requires Room schema $RICC_V1_ROOM_SCHEMA; found $latest_schema."
grep -Fq 'RoomMigrations.MIGRATION_10_11' "$DATABASE_SOURCE" ||
  fail "OpenStoryDatabase must keep MIGRATION_10_11 registered."
grep -Fq 'RoomMigrations.MIGRATION_11_12' "$DATABASE_SOURCE" ||
  fail "OpenStoryDatabase must register MIGRATION_11_12."

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

production_label="modules"
[[ "$production_module_count" -eq 1 ]] && production_label="module"
android_test_label="modules"
[[ "$android_test_module_count" -eq 1 ]] && android_test_label="module"
echo "Current architecture verified: $production_module_count production $production_label, $android_test_module_count android-test $android_test_label, Room schema 1..$latest_schema."
