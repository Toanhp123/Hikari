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
  cp "$ROOT_DIR/storage/room/schemas/app.openstory.storage.room.OpenStoryDatabase/1.json" \
    "$FIXTURE/storage/room/schemas/app.openstory.storage.room.OpenStoryDatabase/1.json"
  cp "$ROOT_DIR/storage/room/schemas/app.openstory.storage.room.OpenStoryDatabase/2.json" \
    "$FIXTURE/storage/room/schemas/app.openstory.storage.room.OpenStoryDatabase/2.json"
  cp "$ROOT_DIR/storage/room/schemas/app.openstory.storage.room.OpenStoryDatabase/3.json" \
    "$FIXTURE/storage/room/schemas/app.openstory.storage.room.OpenStoryDatabase/3.json"
  cp "$ROOT_DIR/storage/room/schemas/app.openstory.storage.room.OpenStoryDatabase/4.json" \
    "$FIXTURE/storage/room/schemas/app.openstory.storage.room.OpenStoryDatabase/4.json"
  cp "$ROOT_DIR/storage/room/schemas/app.openstory.storage.room.OpenStoryDatabase/5.json" \
    "$FIXTURE/storage/room/schemas/app.openstory.storage.room.OpenStoryDatabase/5.json"
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

make_fixture
verify

printf 'unregistered plugin package\n' > "$FIXTURE/app/src/main/assets/plugins/unregistered.osp"
expect_failure 'a production plugin asset missing from the bundled plugin registry'
make_fixture

module_count="$(grep -cE '^[[:space:]]*"\:[a-z0-9:-]+"[[:space:]]*:[[:space:]]*\{' "$FIXTURE/config/architecture/module-boundaries.json")"
[[ "$module_count" == 11 ]] || {
  echo "Wave 08 must retain exactly eleven production modules." >&2
  exit 1
}

printf '\n' >> "$FIXTURE/storage/room/schemas/app.openstory.storage.room.OpenStoryDatabase/1.json"
expect_failure 'a changed frozen schema 1'
make_fixture

sed -i 's/version = 5,/version = 6,/' \
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
