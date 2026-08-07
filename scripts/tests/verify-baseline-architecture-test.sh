#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
TEMP_DIR="$(mktemp -d)"
trap 'rm -rf "$TEMP_DIR"' EXIT

FAKE_ROOT="$TEMP_DIR/repository"
DATABASE_SOURCE="$FAKE_ROOT/core/database/src/main/kotlin/app/openstory/database/OpenStoryDatabase.kt"
SCHEMA_DIR="$FAKE_ROOT/core/database/schemas/app.openstory.database.OpenStoryDatabase"
SELECTOR_DIR="$FAKE_ROOT/core/plugin-api/src/main/kotlin/app/openstory/plugin/api/selector"

mkdir -p "$(dirname "$DATABASE_SOURCE")" "$SCHEMA_DIR" "$SELECTOR_DIR"
printf '@Database(version = 1)\n' > "$DATABASE_SOURCE"
printf '{"formatVersion":1,"database":{"version":1}}\n' > "$SCHEMA_DIR/1.json"
printf 'data class SelectorDefinition(val schemaVersion: Int = 1)\n' > "$SELECTOR_DIR/SelectorDefinition.kt"

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

printf 'data class SelectorPluginDefinitionV2(val schemaVersion: Int = 2)\n' \
  > "$SELECTOR_DIR/SelectorPluginDefinitionV2.kt"
assert_failure "a generation-labelled selector filename" verify
rm "$SELECTOR_DIR/SelectorPluginDefinitionV2.kt"

printf '{"formatVersion":1,"database":{"version":2}}\n' > "$SCHEMA_DIR/2.json"
assert_failure "an additional Room schema" verify
rm "$SCHEMA_DIR/2.json"

printf 'class SelectorInterpreter\n' > "$SELECTOR_DIR/SelectorInterpreter.kt"
assert_failure "a removed selector runtime symbol" verify

echo "verify-baseline-architecture.sh contract verified."
