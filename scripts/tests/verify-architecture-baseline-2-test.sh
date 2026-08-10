#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
FIXTURE="$(mktemp -d)"
trap 'rm -rf "$FIXTURE"' EXIT

make_valid_fixture() {
  local root="$1"
  local modules=(app core/common catalog feature/catalog storage/room plugins/api plugins/runtime)
  for module in "${modules[@]}"; do
    mkdir -p "$root/$module/src/main/kotlin"
  done
  mkdir -p \
    "$root/config/architecture" \
    "$root/config/quality" \
    "$root/storage/room/schemas/app.openstory.storage.room.OpenStoryDatabase" \
    "$root/bundled-plugins/myanimelist-catalog"
  cat > "$root/settings.gradle.kts" <<'SETTINGS'
include(":app")
include(":core:common")
include(":catalog")
include(":feature:catalog")
include(":storage:room")
include(":plugins:api")
include(":plugins:runtime")
SETTINGS
  cat > "$root/config/architecture/module-boundaries.json" <<'POLICY'
{"modules":{":app":{},":core:common":{},":catalog":{},":feature:catalog":{},":storage:room":{},":plugins:api":{},":plugins:runtime":{}}}
POLICY
  : > "$root/config/quality/structural-suppressions.txt"
  printf '{"formatVersion":1,"database":{"version":1}}\n' \
    > "$root/storage/room/schemas/app.openstory.storage.room.OpenStoryDatabase/1.json"
}

verify() {
  REPO_ROOT="$FIXTURE" bash "$ROOT_DIR/scripts/verify-architecture-baseline-2.sh" >/dev/null 2>&1
}

expect_failure() {
  local description="$1"
  if verify; then
    echo "Expected Baseline 2 verifier to reject $description." >&2
    exit 1
  fi
}

make_valid_fixture "$FIXTURE"
verify

mkdir -p "$FIXTURE/core/plugin-host"
expect_failure 'a legacy module'
rm -rf "$FIXTURE/core/plugin-host"

touch "$FIXTURE/bundled-plugins/myanimelist-catalog/selector.json"
expect_failure 'legacy selector package content'
rm "$FIXTURE/bundled-plugins/myanimelist-catalog/selector.json"

printf '@file:Suppress("TooManyFunctions")\n' \
  > "$FIXTURE/feature/catalog/src/main/kotlin/Bad.kt"
expect_failure 'a production structural suppression'
rm "$FIXTURE/feature/catalog/src/main/kotlin/Bad.kt"

printf 'package fixture\nimport app.openstory.model.StoryId\n' \
  > "$FIXTURE/catalog/src/main/kotlin/Bad.kt"
expect_failure 'a legacy package import'
rm "$FIXTURE/catalog/src/main/kotlin/Bad.kt"

printf 'legacy allowance\n' > "$FIXTURE/config/quality/structural-suppressions.txt"
expect_failure 'non-empty suppression debt'

echo 'verify-architecture-baseline-2.sh contract verified.'
