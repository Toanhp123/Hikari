#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="${OPENSTORY_ROOT_DIR:-$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)}"
DATABASE_SOURCE="$ROOT_DIR/core/database/src/main/kotlin/app/openstory/database/OpenStoryDatabase.kt"
SCHEMA_DIR="$ROOT_DIR/core/database/schemas/app.openstory.database.OpenStoryDatabase"

if [[ ! -f "$DATABASE_SOURCE" ]]; then
  echo "Room database source does not exist: $DATABASE_SOURCE" >&2
  exit 1
fi

if ! grep -Eq 'version[[:space:]]*=[[:space:]]*1([^0-9]|$)' "$DATABASE_SOURCE"; then
  echo "OpenStoryDatabase must remain at pre-MVP baseline version 1." >&2
  exit 1
fi

if [[ ! -d "$SCHEMA_DIR" ]]; then
  echo "Room schema directory does not exist: $SCHEMA_DIR" >&2
  exit 1
fi

mapfile -d '' SCHEMA_FILES < <(find "$SCHEMA_DIR" -type f -name '*.json' -print0)
if [[ "${#SCHEMA_FILES[@]}" -ne 1 || "${SCHEMA_FILES[0]}" != "$SCHEMA_DIR/1.json" ]]; then
  echo "Room schema directory must contain exactly 1.json." >&2
  exit 1
fi

SELECTOR_ROOTS=(
  "$ROOT_DIR/core/plugin-api/src"
  "$ROOT_DIR/core/plugin-host/src"
  "$ROOT_DIR/sample-plugins"
)

for selector_root in "${SELECTOR_ROOTS[@]}"; do
  [[ -d "$selector_root" ]] || continue

  while IFS= read -r -d '' selector_file; do
    normalized_path="${selector_file,,}"
    file_name="$(basename "$selector_file")"

    if [[ "$normalized_path" == *selector* ]] &&
      [[ "$file_name" =~ [Vv]1|[Vv]2|[Ll]egacy|[Cc]ompat ]]; then
      echo "Generation-labelled selector filename is forbidden: $selector_file" >&2
      exit 1
    fi
  done < <(find "$selector_root" -type f -print0)
done

REMOVED_SYMBOLS='SelectorPluginDefinitionV2|DecodedSelectorDefinition|SelectorInterpreter|SelectorRuntime|SelectorValue|TransformRegistry|SelectAll|SelectText|SelectAttribute|NormalizeWhitespace'
for selector_root in "${SELECTOR_ROOTS[@]}"; do
  [[ -d "$selector_root" ]] || continue

  if grep -R -E -n --include='*.kt' --include='*.json' \
    "$REMOVED_SYMBOLS" "$selector_root" >/dev/null; then
    echo "Removed selector architecture symbol found under $selector_root." >&2
    grep -R -E -n --include='*.kt' --include='*.json' \
      "$REMOVED_SYMBOLS" "$selector_root" >&2
    exit 1
  fi
done

echo "Baseline architecture verified."
