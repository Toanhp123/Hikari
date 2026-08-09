#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
TEMP_DIR="$(mktemp -d)"
trap 'rm -rf "$TEMP_DIR"' EXIT

FAKE_ROOT="$TEMP_DIR/repository"
mkdir -p "$FAKE_ROOT/core/sample/src/main/kotlin" "$FAKE_ROOT/core/sample/src/test/kotlin"
printf 'class CleanSource\n' > "$FAKE_ROOT/core/sample/src/main/kotlin/CleanSource.kt"

verify() {
  OPENSTORY_ROOT_DIR="$FAKE_ROOT" "$ROOT_DIR/scripts/verify-source-layout.sh" >/dev/null 2>&1
}

assert_failure() {
  local description="$1"
  shift
  set +e
  "$@"
  local status=$?
  set -e
  if [[ "$status" -eq 0 ]]; then
    echo "Expected source-layout verification to reject $description." >&2
    exit 1
  fi
}

write_lines() {
  local count="$1"
  local target="$2"
  : > "$target"
  for ((line = 1; line <= count; line += 1)); do
    printf '// line %s\n' "$line" >> "$target"
  done
}

verify

printf 'class SelectorThingV2\n' > "$FAKE_ROOT/core/sample/src/main/kotlin/SelectorThingV2.kt"
assert_failure "generation-labelled architecture names" verify
rm "$FAKE_ROOT/core/sample/src/main/kotlin/SelectorThingV2.kt"

mkdir -p "$FAKE_ROOT/.idea"
printf '<project/>\n' > "$FAKE_ROOT/.idea/misc.xml"
assert_failure "IDE metadata" verify
rm -rf "$FAKE_ROOT/.idea"

mkdir -p "$FAKE_ROOT/storage/room/src/main/kotlin"
printf 'import app.openstory.plugin.host.install.StagedPluginPackage\n' \
  > "$FAKE_ROOT/storage/room/src/main/kotlin/BadImport.kt"
assert_failure "database installer imports" verify
rm "$FAKE_ROOT/storage/room/src/main/kotlin/BadImport.kt"

write_lines 501 "$FAKE_ROOT/core/sample/src/main/kotlin/Oversized.kt"
assert_failure "a 501-line production source" verify
rm "$FAKE_ROOT/core/sample/src/main/kotlin/Oversized.kt"

write_lines 751 "$FAKE_ROOT/core/sample/src/test/kotlin/OversizedTest.kt"
assert_failure "a 751-line test source" verify

echo "verify-source-layout.sh contract verified."
