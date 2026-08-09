#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="${REPO_ROOT:-$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)}"

fail_matches() {
  local source_root="$1" pattern="$2" message="$3"
  [[ -d "$source_root" ]] || return 0

  local matches
  matches="$(grep -RInE --include='*.kt' "^[[:space:]]*import[[:space:]]+${pattern}" "$source_root" || true)"
  if [[ -n "$matches" ]]; then
    echo "$message" >&2
    echo "$matches" >&2
    return 1
  fi
}

feature_root="$ROOT_DIR/feature/catalog/src/main"
storage_root="$ROOT_DIR/storage/room/src/main"
plugin_api_root="$ROOT_DIR/plugins/api/src/main"
catalog_root="$ROOT_DIR/catalog/src/main"

fail_matches \
  "$feature_root" \
  'app\.openstory\.storage\.room(\.|$)' \
  'feature/catalog must not import Room storage internals.'
fail_matches \
  "$feature_root" \
  'app\.openstory\.plugins\.runtime(\.|$)' \
  'feature/catalog must not import plugin runtime.'
fail_matches \
  "$plugin_api_root" \
  '(android|androidx)(\.|$)' \
  'plugins/api must remain free of Android dependencies.'
fail_matches \
  "$catalog_root" \
  'androidx\.compose(\.|$)' \
  'catalog must remain free of Compose dependencies.'

if [[ -d "$storage_root" ]]; then
  while IFS= read -r match; do
    [[ -z "$match" ]] && continue
    import_name="${match#*import }"
    if [[ "$import_name" != app.openstory.plugins.runtime.persistence.* ]]; then
      echo 'storage/room may import only plugins.runtime.persistence SPI contracts.' >&2
      echo "$match" >&2
      exit 1
    fi
  done < <(
    grep -RInE --include='*.kt' \
      '^[[:space:]]*import[[:space:]]+app\.openstory\.plugins\.runtime(\.|$)' \
      "$storage_root" || true
  )
fi

echo "Package boundary policy verified."
