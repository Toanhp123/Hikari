#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="${REPO_ROOT:-$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)}"

for retired in \
  core/designsystem \
  library \
  chapters \
  downloads \
  settings \
  storage \
  plugins/runtime \
  feature \
  bundled-plugins \
  app/src/main/assets/plugins \
  app/src/main/keepRules; do
  if [[ -e "$ROOT_DIR/$retired" ]]; then
    echo "Retired V1 runtime path still exists: $retired" >&2
    exit 1
  fi
done

for retired in \
  catalog/build.gradle.kts \
  catalog/src \
  reader/build.gradle.kts \
  reader/src \
  app/src/main/res/xml/backup_rules.xml \
  app/src/main/res/xml/data_extraction_rules.xml; do
  if [[ -e "$ROOT_DIR/$retired" ]]; then
    echo "Retired V1 integration path still exists: $retired" >&2
    exit 1
  fi
done

for retained in \
  core/common \
  catalog/model \
  catalog/engine \
  reader/engine \
  plugins/api; do
  [[ -d "$ROOT_DIR/$retained" ]] || {
    echo "Required retained/quarantined source missing: $retained" >&2
    exit 1
  }
done

echo "V2 retired runtime absence verified."
