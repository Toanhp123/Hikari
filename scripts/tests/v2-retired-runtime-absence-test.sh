#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="${REPO_ROOT:-$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)}"

for retired in \
  library \
  chapters \
  downloads \
  settings \
  storage \
  plugins/runtime \
  bundled-plugins \
  app/src/main/assets/plugins \
  app/src/main/keepRules; do
  if [[ -e "$ROOT_DIR/$retired" ]]; then
    echo "Retired V1 runtime path still exists: $retired" >&2
    exit 1
  fi
done

if find "$ROOT_DIR/feature" -mindepth 1 -maxdepth 1 ! -name catalog \
  -print -quit 2>/dev/null | grep -q .; then
  echo "Unadmitted V2 feature path exists outside feature/catalog." >&2
  exit 1
fi

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
  core/designsystem \
  catalog/model \
  catalog/engine \
  feature/catalog \
  reader/engine \
  plugins/api; do
  [[ -d "$ROOT_DIR/$retained" ]] || {
    echo "Required retained/quarantined source missing: $retained" >&2
    exit 1
  }
done

echo "V2 retired runtime absence verified."
