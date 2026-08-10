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
    "$root/app/src/main/assets/plugins" \
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
{"schemaVersion":2,"modules":{":app":{"path":"app","platform":"android-application","dependencyMode":"exact","productionDependencies":[],"testDependencies":[],"forbiddenProductionImports":[]},":core:common":{"path":"core/common","platform":"jvm","dependencyMode":"exact","productionDependencies":[],"testDependencies":[],"forbiddenProductionImports":[]},":catalog":{"path":"catalog","platform":"android-library","dependencyMode":"exact","productionDependencies":[],"testDependencies":[],"forbiddenProductionImports":[]},":feature:catalog":{"path":"feature/catalog","platform":"android-library","dependencyMode":"exact","productionDependencies":[],"testDependencies":[],"forbiddenProductionImports":[]},":storage:room":{"path":"storage/room","platform":"android-library","dependencyMode":"exact","productionDependencies":[],"testDependencies":[],"forbiddenProductionImports":[]},":plugins:api":{"path":"plugins/api","platform":"jvm","dependencyMode":"exact","productionDependencies":[],"testDependencies":[],"forbiddenProductionImports":[]},":plugins:runtime":{"path":"plugins/runtime","platform":"android-library","dependencyMode":"exact","productionDependencies":[],"testDependencies":[],"forbiddenProductionImports":[]}}}
POLICY
  : > "$root/config/quality/structural-suppressions.txt"
  printf '{"formatVersion":1,"database":{"version":1}}\n' \
    > "$root/storage/room/schemas/app.openstory.storage.room.OpenStoryDatabase/1.json"
  printf 'canonical plugin package\n' \
    > "$root/app/src/main/assets/plugins/myanimelist-catalog.osp"
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

if ! REPO_ROOT="$FIXTURE" bash -c 'enable -n mapfile; source "$1"' \
  _ "$ROOT_DIR/scripts/verify-architecture-baseline-2.sh" >/dev/null 2>&1; then
  echo 'Baseline 2 verifier must run without the Bash 4 mapfile builtin.' >&2
  exit 1
fi

mkdir -p "$FIXTURE/bin"
cat > "$FIXTURE/bin/find" <<'FIND'
#!/usr/bin/env bash
for argument in "$@"; do
  if [[ "$argument" == '-maxdepth' ]]; then
    echo 'find: -maxdepth is unavailable' >&2
    exit 2
  fi
done
exec /usr/bin/find "$@"
FIND
chmod +x "$FIXTURE/bin/find"
if ! PATH="$FIXTURE/bin:$PATH" REPO_ROOT="$FIXTURE" \
  bash "$ROOT_DIR/scripts/verify-architecture-baseline-2.sh" >/dev/null 2>&1; then
  echo 'Baseline 2 verifier must not require GNU find extensions.' >&2
  exit 1
fi
rm -rf "$FIXTURE/bin"

touch "$FIXTURE/app/src/main/assets/plugins/unexpected.osp"
expect_failure 'an extra production bundled plugin asset'
rm "$FIXTURE/app/src/main/assets/plugins/unexpected.osp"

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
