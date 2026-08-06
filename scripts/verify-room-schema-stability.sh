#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
SCHEMA_DIR="${ROOM_SCHEMA_DIR:-$ROOT_DIR/core/database/schemas}"
EXPECTED_FINGERPRINT="${1:-}"

if [[ ! -d "$SCHEMA_DIR" ]]; then
  echo "Room schema directory does not exist: $SCHEMA_DIR" >&2
  exit 2
fi

SCHEMA_FILES=()
while IFS= read -r -d '' schema_file; do
  SCHEMA_FILES+=("$schema_file")
done < <(
  find "$SCHEMA_DIR" -type f -name '*.json' -print0 |
    sort -z
)

if [[ "${#SCHEMA_FILES[@]}" -eq 0 ]]; then
  echo "No Room schema JSON files found in $SCHEMA_DIR." >&2
  exit 2
fi

CURRENT_FINGERPRINT="$({
  for schema_file in "${SCHEMA_FILES[@]}"; do
    relative_path="${schema_file#"$SCHEMA_DIR"/}"
    printf '%s  %s\n' \
      "$(sha256sum "$schema_file" | awk '{ print $1 }')" \
      "$relative_path"
  done
} | sha256sum | awk '{ print $1 }')"

if [[ -z "$EXPECTED_FINGERPRINT" ]]; then
  printf '%s\n' "$CURRENT_FINGERPRINT"
  exit 0
fi

if [[ "$CURRENT_FINGERPRINT" != "$EXPECTED_FINGERPRINT" ]]; then
  echo \
    "Room schema drift detected. Commit the schema exported by the Room compiler and rerun verification." \
    >&2
  echo "Expected fingerprint: $EXPECTED_FINGERPRINT" >&2
  echo "Current fingerprint:  $CURRENT_FINGERPRINT" >&2
  exit 1
fi

echo "Room schema export remained stable during verification."
