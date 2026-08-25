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

fail_references() {
  local source_root="$1" pattern="$2" message="$3"
  [[ -d "$source_root" ]] || return 0

  local matches
  matches="$(grep -RInE --include='*.kt' "${pattern}" "$source_root" || true)"
  if [[ -n "$matches" ]]; then
    echo "$message" >&2
    echo "$matches" >&2
    return 1
  fi
}

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

fail_engine_type_leaks() {
  local root="$1" allowed_reader_root="$2" allowed_engine_root="$3"
  [[ -d "$root" ]] || return 0

  while IFS= read -r -d '' source_file; do
    case "$source_file" in
      "$allowed_reader_root"/*|"$allowed_engine_root"/*) continue ;;
    esac
    local matches
    matches="$(grep -nE '^[[:space:]]*import[[:space:]]+app\.openstory\.reader\.engine(\.|$)|app\.openstory\.reader\.engine\.' "$source_file" || true)"
    if [[ -n "$matches" ]]; then
      echo 'Only :reader and :reader:engine may reference HES engine types.' >&2
      echo "$source_file:$matches" >&2
      return 1
    fi
  done < <(
    find "$root" -type f -name '*.kt' -path '*/src/main/*' -print0
  )
}

fail_forbidden_runtime_references() {
  local source_root="$1"
  [[ -d "$source_root" ]] || return 0

  while IFS= read -r -d '' source_file; do
    compact_source="$(tr -d '[:space:]' < "$source_file")"
    remainder="$(printf '%s\n' "$compact_source" | sed -E \
      's/app\.openstory\.plugins\.runtime\.persistence(\.([A-Za-z_][A-Za-z0-9_]*|\*))+/ALLOWED/g')"
    if [[ "$remainder" == *app.openstory.plugins.runtime* ]]; then
      echo 'storage/room may reference only plugins.runtime.persistence SPI contracts.' >&2
      echo "$source_file" >&2
      return 1
    fi
  done < <(
    find "$source_root" -type f -name '*.kt' -print0
  )
}

core_root="$ROOT_DIR/core/common/src/main"
designsystem_root="$ROOT_DIR/core/designsystem/src/main"
catalog_root="$ROOT_DIR/catalog/src/main"
library_root="$ROOT_DIR/library/src/main"
chapters_root="$ROOT_DIR/chapters/src/main"
reader_root="$ROOT_DIR/reader/src/main"
reader_engine_root="$ROOT_DIR/reader/engine/src/main"
feature_catalog_root="$ROOT_DIR/feature/catalog/src/main"
feature_reader_root="$ROOT_DIR/feature/reader/src/main"
storage_root="$ROOT_DIR/storage/room/src/main"
plugin_api_root="$ROOT_DIR/plugins/api/src/main"
plugin_runtime_root="$ROOT_DIR/plugins/runtime/src/main"

fail_matches "$core_root" '(android|androidx)(\.|$)' \
  'core/common must remain free of Android dependencies.'
validate_project_imports "$core_root" '^app\.openstory\.common(\.|$)' \
  'core/common may import only its own project packages.'

validate_project_imports "$designsystem_root" '^app\.openstory\.designsystem(\.|$)' \
  'core/designsystem may import only its own project packages.'

fail_matches "$plugin_api_root" '(android|androidx)(\.|$)' \
  'plugins/api must remain free of Android dependencies.'
validate_project_imports "$plugin_api_root" '^app\.openstory\.plugins\.api(\.|$)' \
  'plugins/api may import only its own project packages.'

validate_project_imports "$plugin_runtime_root" '^app\.openstory\.(common|plugins\.api|plugins\.runtime)(\.|$)' \
  'plugins/runtime may import only core common, plugin API, and its own packages.'

fail_matches "$catalog_root" 'android\.content\.Context(\.|$)' \
  'catalog must not import Android Context.'
fail_matches "$catalog_root" 'androidx\.compose(\.|$)' \
  'catalog must remain free of Compose dependencies.'
fail_matches "$catalog_root" 'app\.openstory\.common\.dispatchers\.AppDispatchers(\.|$)' \
  'catalog must not depend on application dispatchers.'
validate_project_imports "$catalog_root" '^app\.openstory\.(common|catalog|plugins\.api|plugins\.runtime)(\.|$)' \
  'catalog may import only core common, plugin API/runtime, and its own packages.'

fail_matches "$library_root" 'android\.content\.Context(\.|$)|androidx\.(compose|room|work)(\.|$)' \
  'library must remain free of Android platform, Compose, Room, and WorkManager APIs.'
validate_project_imports "$library_root" \
  '^app\.openstory\.(common|library|plugins\.api)(\.|$)|^app\.openstory\.catalog\.model\.ContentType$|^app\.openstory\.catalog\.projection(\.|$)|^app\.openstory\.plugins\.runtime\.(InstalledPlugin|PluginCallResult|PluginRuntime)$' \
  'library may import only core common, narrow catalog projections/models, plugin wire contracts, the public runtime facade, and its own packages.'

fail_matches "$chapters_root" 'android\.content\.Context(\.|$)|androidx\.(compose|room|work)(\.|$)' \
  'chapters must remain free of Android platform, Compose, Room, and WorkManager APIs.'
validate_project_imports "$chapters_root" \
  '^app\.openstory\.(common|chapters|library|plugins\.api)(\.|$)|^app\.openstory\.plugins\.runtime\.(InstalledPlugin|PluginCallResult|PluginRuntime)$' \
  'chapters may import only core common, Library, plugin wire contracts, the public runtime facade, and its own packages.'

fail_matches "$reader_root" 'android\.content\.Context(\.|$)|androidx\.(compose|room|work)(\.|$)' \
  'reader must remain free of Android platform, Compose, Room, and WorkManager APIs.'
validate_project_imports "$reader_root" \
  '^app\.openstory\.(common|chapters|reader|plugins\.api)(\.|$)|^app\.openstory\.plugins\.runtime\.(InstalledPlugin|PluginCallResult|PluginRuntime)$' \
  'reader may import only core common, Chapters, plugin wire contracts, the public runtime facade, and its own packages.'

fail_matches "$reader_engine_root" '(android|androidx)(\.|$)|kotlinx\.(coroutines|serialization)(\.|$)|java\.(io|net)(\.|$)' \
  'reader/engine must remain free of Android, coroutines, serialization, filesystem, and network APIs.'
fail_matches "$reader_engine_root" 'app\.openstory\.common\.(Clock|SystemClock|FakeClock)(\.|$)|app\.openstory\.common\.dispatchers(\.|$)' \
  'reader/engine must not import clocks or dispatcher/effect abstractions from core/common.'
validate_project_imports "$reader_engine_root" '^app\.openstory\.(common|reader\.engine)(\.|$)' \
  'reader/engine may import only pure core-common values and its own engine packages.'
fail_references "$reader_engine_root" '(android|androidx)\.|app\.openstory\.(chapters|reader\.(content|routing)|plugins|downloads|storage)\.|kotlinx\.(coroutines|serialization)\.|java\.(io|net)\.|app\.openstory\.common\.(Clock|SystemClock|FakeClock)([^A-Za-z0-9_]|$)|app\.openstory\.common\.dispatchers\.' \
  'reader/engine contains a forbidden effect/framework reference (including fully-qualified references).'

validate_project_imports "$feature_catalog_root" '^app\.openstory\.(common|designsystem|catalog|library|chapters|reader|downloads)(\.|$)' \
  'feature/catalog may import only core common, design system, Catalog, Library, Chapters, Reader, and Downloads project packages.'

validate_project_imports "$feature_reader_root" '^app\.openstory\.(common|designsystem|chapters|reader|downloads)(\.|$)' \
  'feature/reader may import only core common, design system, Chapters, Reader, and Downloads project packages.'

validate_project_imports "$storage_root" '^app\.openstory\.(common|catalog|library|chapters|reader|downloads|plugins\.api|plugins\.runtime\.persistence|storage\.room)(\.|$)' \
  'storage/room may import only capability contracts, runtime persistence SPI, and its own packages.'
fail_forbidden_runtime_references "$storage_root"
fail_engine_type_leaks "$ROOT_DIR" "$reader_root" "$reader_engine_root"

echo "Package boundary policy verified."
