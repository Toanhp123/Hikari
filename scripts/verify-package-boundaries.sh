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

validate_project_imports() {
  local source_root="$1" allowed_pattern="$2" message="$3"
  [[ -d "$source_root" ]] || return 0

  while IFS= read -r match; do
    [[ -z "$match" ]] && continue
    import_name="${match#*import }"
    if [[ ! "$import_name" =~ $allowed_pattern ]]; then
      echo "$message" >&2
      echo "$match" >&2
      return 1
    fi
  done < <(
    grep -RInE --include='*.kt' \
      '^[[:space:]]*import[[:space:]]+app\.openstory\.' \
      "$source_root" || true
  )
}

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
validate_project_imports \
  "$plugin_api_root" \
  '^app\.openstory\.plugins\.api(\.|$)' \
  'plugins/api must not import host application modules.'
fail_matches \
  "$catalog_root" \
  'androidx\.compose(\.|$)' \
  'catalog must remain free of Compose dependencies.'
fail_matches \
  "$catalog_root" \
  'app\.openstory\.storage\.room(\.|$)' \
  'catalog must not import Room storage internals.'
fail_matches \
  "$catalog_root" \
  'app\.openstory\.model(\.|$)' \
  'Baseline 2 catalog code must import common IDs directly.'
fail_matches \
  "$ROOT_DIR/plugins/runtime/src/main" \
  'app\.openstory\.model(\.|$)' \
  'Baseline 2 plugin runtime code must import common IDs directly.'
fail_matches \
  "$storage_root" \
  'app\.openstory\.model(\.|$)' \
  'Baseline 2 Room code must import common IDs directly.'

validate_project_imports \
  "$feature_root" \
  '^app\.openstory\.(common|catalog)(\.|$)' \
  'feature/catalog may import only core common and catalog project packages.'

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
