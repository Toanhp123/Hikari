#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
TEMP_DIR="$(mktemp -d)"
trap 'rm -rf "$TEMP_DIR"' EXIT

FAKE_ROOT="$TEMP_DIR/repository"
DATABASE_SOURCE="$FAKE_ROOT/core/database/src/main/kotlin/app/openstory/database/OpenStoryDatabase.kt"
SCHEMA_DIR="$FAKE_ROOT/core/database/schemas/app.openstory.database.OpenStoryDatabase"
ASSET_DIR="$FAKE_ROOT/app/src/main/assets/plugins"

mkdir -p "$(dirname "$DATABASE_SOURCE")" "$SCHEMA_DIR" "$ASSET_DIR"
printf '@Database(version = 1)\n' > "$DATABASE_SOURCE"
printf '{"formatVersion":1,"database":{"version":1}}\n' > "$SCHEMA_DIR/1.json"
printf 'placeholder\n' > "$ASSET_DIR/myanimelist-catalog.osp"

verify() {
  OPENSTORY_ROOT_DIR="$FAKE_ROOT" \
    "$ROOT_DIR/scripts/verify-baseline-architecture.sh" >/dev/null 2>&1
}

assert_failure() {
  local description="$1"
  shift

  set +e
  "$@"
  local status=$?
  set -e

  if [[ "$status" -eq 0 ]]; then
    echo "Expected baseline verification to reject $description." >&2
    exit 1
  fi
}

verify

printf 'placeholder\n' > "$ASSET_DIR/default-catalog.osp"
assert_failure "a second production plugin asset" verify
rm "$ASSET_DIR/default-catalog.osp"

printf '{"formatVersion":1,"database":{"version":2}}\n' > "$SCHEMA_DIR/2.json"
assert_failure "an additional Room schema" verify
rm "$SCHEMA_DIR/2.json"

mkdir -p "$FAKE_ROOT/core/plugin-host"
assert_failure "a removed legacy module" verify

echo "verify-baseline-architecture.sh contract verified."
