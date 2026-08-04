#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$ROOT_DIR"

fail() {
  echo "Module boundary violation: $1" >&2
  exit 1
}

assert_allowed_project_dependencies() {
  local module="$1"
  local build_file="$2"
  shift 2

  local allowed=("$@")
  local dependency
  local candidate
  local accepted

  while IFS= read -r dependency; do
    [[ -z "$dependency" ]] && continue

    accepted=false

    for candidate in "${allowed[@]}"; do
      if [[ "$dependency" == "$candidate" ]]; then
        accepted=true
        break
      fi
    done

    if [[ "$accepted" != true ]]; then
      fail "$module must not depend on $dependency"
    fi
  done < <(
    {
      grep -Eo 'project\(":[^"]+"\)' "$build_file" || true
    } |
      sed -E 's/project\("([^"]+)"\)/\1/' |
      sort -u
  )
}

assert_allowed_project_dependencies \
  ":core:common" \
  "core/common/build.gradle.kts"

assert_allowed_project_dependencies \
  ":core:model" \
  "core/model/build.gradle.kts" \
  ":core:common"

assert_allowed_project_dependencies \
  ":test:fixtures" \
  "test/fixtures/build.gradle.kts" \
  ":core:common" \
  ":core:model"

CORE_MODEL_SOURCE="core/model/src/main"

if [[ -d "$CORE_MODEL_SOURCE" ]]; then
  forbidden_imports="$(
    grep -RInE \
      '^[[:space:]]*import[[:space:]]+(android\.|androidx\.compose)' \
      "$CORE_MODEL_SOURCE" ||
      true
  )"

  if [[ -n "$forbidden_imports" ]]; then
    echo "$forbidden_imports" >&2
    fail ":core:model imports Android or Compose APIs"
  fi
fi

echo "Module dependency boundaries verified."
