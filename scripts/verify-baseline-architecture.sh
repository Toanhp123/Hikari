#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="${OPENSTORY_ROOT_DIR:-$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)}"
DATABASE_SOURCE="$ROOT_DIR/core/database/src/main/kotlin/app/openstory/database/OpenStoryDatabase.kt"
SCHEMA_DIR="$ROOT_DIR/core/database/schemas/app.openstory.database.OpenStoryDatabase"

grep -Eq 'version[[:space:]]*=[[:space:]]*1([^0-9]|$)' "$DATABASE_SOURCE" || {
  echo "OpenStoryDatabase must remain at pre-MVP baseline version 1." >&2
  exit 1
}

mapfile -d '' SCHEMA_FILES < <(find "$SCHEMA_DIR" -type f -name '*.json' -print0)
[[ "${#SCHEMA_FILES[@]}" -eq 1 && "${SCHEMA_FILES[0]}" == "$SCHEMA_DIR/1.json" ]] || {
  echo "Room schema directory must contain exactly 1.json." >&2
  exit 1
}

for removed in core/plugin-api core/plugin-host core/network; do
  [[ ! -e "$ROOT_DIR/$removed" ]] || { echo "Legacy module still exists: $removed" >&2; exit 1; }
done

if find "$ROOT_DIR/bundled-plugins" -type f -name selector.json -print -quit | grep -q .; then
  echo "Production bundled plugins must not contain selector.json." >&2
  exit 1
fi

if grep -R -E -n --include='*.kt' 'app\.openstory\.plugin\.(api|host)' \
  "$ROOT_DIR/app/src/main" "$ROOT_DIR/catalog/src/main" "$ROOT_DIR/core" "$ROOT_DIR/feature" >/dev/null; then
  echo "Legacy plugin imports remain in production." >&2
  exit 1
fi

ASSET_DIR="$ROOT_DIR/app/src/main/assets/plugins"
mapfile -t PLUGIN_ASSETS < <(find "$ASSET_DIR" -maxdepth 1 -type f -name '*.osp' -printf '%f\n' | sort)
[[ "${PLUGIN_ASSETS[*]}" == "myanimelist-catalog.osp" ]] || {
  echo "Only myanimelist-catalog.osp may remain bundled." >&2
  exit 1
}

echo "Baseline architecture verified."
