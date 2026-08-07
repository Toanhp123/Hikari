#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
TEMP_DIR="$(mktemp -d)"
trap 'rm -rf "$TEMP_DIR"' EXIT

mkdir -p "$TEMP_DIR/schemas"
printf '{"version":1}\n' > "$TEMP_DIR/schemas/1.json"
printf '{"version":2}\n' > "$TEMP_DIR/schemas/2.json"

EXPECTED="$(
  ROOM_SCHEMA_DIR="$TEMP_DIR/schemas" \
    "$ROOT_DIR/scripts/verify-room-schema-stability.sh"
)"

ROOM_SCHEMA_DIR="$TEMP_DIR/schemas" \
  "$ROOT_DIR/scripts/verify-room-schema-stability.sh" "$EXPECTED" >/dev/null

printf '{"version":3}\n' >> "$TEMP_DIR/schemas/2.json"

set +e
ROOM_SCHEMA_DIR="$TEMP_DIR/schemas" \
  "$ROOT_DIR/scripts/verify-room-schema-stability.sh" "$EXPECTED" >/dev/null 2>&1
STATUS=$?
set -e

if [[ "$STATUS" -eq 0 ]]; then
  echo "Expected Room schema drift to fail." >&2
  exit 1
fi

echo "verify-room-schema-stability.sh contract verified."
