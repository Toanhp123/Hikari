#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
FIXTURE="$(mktemp -d)"
trap 'rm -rf "$FIXTURE"' EXIT

make_fixture() {
  rm -rf "$FIXTURE"/*
  mkdir -p "$FIXTURE/config/architecture" "$FIXTURE/config/quality"
  cp "$ROOT_DIR/settings.gradle.kts" "$FIXTURE/settings.gradle.kts"
  cp "$ROOT_DIR/config/architecture/module-boundaries.json" "$FIXTURE/config/architecture/module-boundaries.json"
  : > "$FIXTURE/config/quality/structural-suppressions.txt"

  while IFS= read -r module_path; do
    [[ -n "$module_path" ]] || continue
    mkdir -p "$FIXTURE/$module_path/src/main/kotlin"
    cp "$ROOT_DIR/$module_path/build.gradle.kts" "$FIXTURE/$module_path/build.gradle.kts"
  done < <(
    sed -nE 's/^[[:space:]]*"path"[[:space:]]*:[[:space:]]*"([^"]+)".*/\1/p' \
      "$ROOT_DIR/config/architecture/module-boundaries.json"
  )

  mkdir -p \
    "$FIXTURE/storage/room/schemas/app.openstory.storage.room.OpenStoryDatabase" \
    "$FIXTURE/storage/room/src/main/kotlin/app/openstory/storage/room" \
    "$FIXTURE/app/src/main/assets/plugins" \
    "$FIXTURE/app/src/main/kotlin/app/openstory/di"
  cp "$ROOT_DIR/storage/room/schemas/app.openstory.storage.room.OpenStoryDatabase/"*.json \
    "$FIXTURE/storage/room/schemas/app.openstory.storage.room.OpenStoryDatabase/"
  cp "$ROOT_DIR/storage/room/src/main/kotlin/app/openstory/storage/room/OpenStoryDatabase.kt" \
    "$FIXTURE/storage/room/src/main/kotlin/app/openstory/storage/room/OpenStoryDatabase.kt"
  printf 'canonical plugin package\n' > "$FIXTURE/app/src/main/assets/plugins/myanimelist-catalog.osp"
  printf 'official content plugin package\n' > "$FIXTURE/app/src/main/assets/plugins/mangadex-content.osp"
  cat > "$FIXTURE/app/src/main/kotlin/app/openstory/di/BundledPlugins.kt" <<'KOTLIN'
package app.openstory.di

internal object BundledPlugins {
    val descriptors = listOf(
        BundledPluginDescriptor(
            assetPath = "plugins/myanimelist-catalog.osp",
        ),
        BundledPluginDescriptor(
            assetPath = "plugins/mangadex-content.osp",
        ),
    )
}
KOTLIN
}

verify() {
  REPO_ROOT="$FIXTURE" bash "$ROOT_DIR/scripts/verify-current-architecture.sh" >/dev/null 2>&1
}

expect_failure() {
  local description="$1"
  if verify; then
    echo "Expected current architecture verifier to reject $description." >&2
    exit 1
  fi
}

module_dependencies() {
  local module="$1"
  awk -v target="\"$module\"" '
    index($0, target) && $0 ~ /:[[:space:]]*\{/ { in_module = 1 }
    in_module && /"productionDependencies"[[:space:]]*:/ { in_dependencies = 1 }
    in_dependencies { print }
    in_dependencies && /]/ { exit }
  ' "$FIXTURE/config/architecture/module-boundaries.json" |
    grep -oE '":[a-z0-9:-]+"' |
    tr -d '"'
}

make_fixture
verify

printf 'unregistered plugin package\n' > "$FIXTURE/app/src/main/assets/plugins/unregistered.osp"
expect_failure 'a production plugin asset missing from the bundled plugin registry'
make_fixture

production_module_count="$(awk '
  /"\:[a-z0-9:-]+"[[:space:]]*:[[:space:]]*\{/ { module = $0; is_test = 0 }
  /"platform"[[:space:]]*:[[:space:]]*"android-test"/ { is_test = 1 }
  /^[[:space:]]*}[,]?[[:space:]]*$/ && module != "" { if (!is_test) count++; module = "" }
  END { print count + 0 }
' "$FIXTURE/config/architecture/module-boundaries.json")"
[[ "$production_module_count" == 15 ]] || {
  echo "Wave 10 foundation boundary must contain exactly fifteen production modules before feature settings." >&2
  exit 1
}

grep -q '"\:benchmark"[[:space:]]*:[[:space:]]*{' "$FIXTURE/config/architecture/module-boundaries.json" || {
  echo "Performance tooling policy must declare the benchmark test module." >&2
  exit 1
}
grep -A4 '"\:benchmark"[[:space:]]*:[[:space:]]*{' "$FIXTURE/config/architecture/module-boundaries.json" |
  grep -q '"platform"[[:space:]]*:[[:space:]]*"android-test"' || {
  echo "Benchmark must be classified as android-test, not production." >&2
  exit 1
}

expected_app_dependencies=$':core:common\n:core:designsystem\n:catalog\n:library\n:chapters\n:reader\n:downloads\n:settings\n:storage:room\n:storage:files\n:plugins:api\n:plugins:runtime\n:feature:catalog\n:feature:reader'
expected_download_dependencies=$':core:common\n:chapters\n:reader'
expected_file_dependencies=':downloads'
if [[ "$(module_dependencies ':app')" != "$expected_app_dependencies" ]] ||
  [[ "$(module_dependencies ':downloads')" != "$expected_download_dependencies" ]] ||
  [[ "$(module_dependencies ':storage:files')" != "$expected_file_dependencies" ]]; then
  echo "UI foundation policy must preserve the approved app, download, and file-storage edges." >&2
  exit 1
fi

grep -q 'api(project(":core:common"))' "$FIXTURE/downloads/build.gradle.kts" || {
  echo "Downloads must expose :core:common because ChapterBlobKey exposes ChapterReleaseId." >&2
  exit 1
}

printf '\n' >> "$FIXTURE/storage/room/schemas/app.openstory.storage.room.OpenStoryDatabase/1.json"
expect_failure 'a changed frozen schema 1'
make_fixture

current_database_version="$(
  sed -nE 's/^[[:space:]]*version[[:space:]]*=[[:space:]]*([0-9]+),[[:space:]]*$/\1/p' \
    "$FIXTURE/storage/room/src/main/kotlin/app/openstory/storage/room/OpenStoryDatabase.kt" | head -1
)"
next_database_version=$((current_database_version + 1))
sed -i "s/version = ${current_database_version},/version = ${next_database_version},/" \
  "$FIXTURE/storage/room/src/main/kotlin/app/openstory/storage/room/OpenStoryDatabase.kt"
expect_failure 'a database version without a contiguous exported schema'
make_fixture

printf 'include(":unapproved")\n' >> "$FIXTURE/settings.gradle.kts"
mkdir -p "$FIXTURE/unapproved"
expect_failure 'a module not declared by policy'
make_fixture

printf '\nimplementation(project(":storage:room"))\n' >> "$FIXTURE/library/build.gradle.kts"
expect_failure 'an edge not declared by policy'

# The policy is the source of truth for current edges: when the reviewed policy changes,
# the verifier follows it rather than freezing the current Wave 08 graph in shell code.
sed -i '/"\:library"[[:space:]]*:/,/"\:chapters"[[:space:]]*:/ s/":plugins:runtime"/":plugins:runtime", ":storage:room"/' \
  "$FIXTURE/config/architecture/module-boundaries.json"
verify

echo 'verify-current-architecture.sh contract verified.'
