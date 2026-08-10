#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
FIXTURE="$(mktemp -d)"
trap 'rm -rf "$FIXTURE"' EXIT

mkdir -p \
  "$FIXTURE/catalog/src/main/kotlin/app/openstory/catalog" \
  "$FIXTURE/feature/catalog/src/main/kotlin/app/openstory/catalog/ui" \
  "$FIXTURE/storage/room/src/main/kotlin/app/openstory/storage/room"

for line in $(seq 1 301); do printf '// line %s\n' "$line"; done \
  > "$FIXTURE/catalog/src/main/kotlin/app/openstory/catalog/Large.kt"
touch "$FIXTURE/catalog/src/main/kotlin/app/openstory/catalog/SearchPart1.kt"
touch "$FIXTURE/catalog/src/main/kotlin/app/openstory/catalog/SearchPart2.kt"
printf 'package app.openstory.catalog.ui\nclass CleanViewModel\n' \
  > "$FIXTURE/feature/catalog/src/main/kotlin/app/openstory/catalog/ui/CleanViewModel.kt"
printf 'package app.openstory.storage.room\nimport app.openstory.plugins.runtime.persistence.PluginStateStore\n' \
  > "$FIXTURE/storage/room/src/main/kotlin/app/openstory/storage/room/Clean.kt"

report="$(REPO_ROOT="$FIXTURE" bash "$ROOT_DIR/scripts/structural-review-report.sh")"
grep -q 'Large.kt' <<< "$report"
grep -q 'SearchPart1.kt' <<< "$report"

printf 'package app.openstory.catalog\nimport android.content.Context\n' \
  > "$FIXTURE/catalog/src/main/kotlin/app/openstory/catalog/Bad.kt"
if REPO_ROOT="$FIXTURE" bash "$ROOT_DIR/scripts/structural-review-report.sh" >/dev/null 2>&1; then
  echo 'catalog Android Context import must fail' >&2
  exit 1
fi
rm "$FIXTURE/catalog/src/main/kotlin/app/openstory/catalog/Bad.kt"

printf 'package app.openstory.catalog.ui\nimport kotlinx.coroutines.CoroutineScope\nclass BadViewModel\n' \
  > "$FIXTURE/feature/catalog/src/main/kotlin/app/openstory/catalog/ui/BadViewModel.kt"
if REPO_ROOT="$FIXTURE" bash "$ROOT_DIR/scripts/structural-review-report.sh" >/dev/null 2>&1; then
  echo 'feature ViewModel custom scope import must fail' >&2
  exit 1
fi
rm "$FIXTURE/feature/catalog/src/main/kotlin/app/openstory/catalog/ui/BadViewModel.kt"

printf 'package app.openstory.storage.room\nimport app.openstory.plugins.runtime.execution.PluginOperationRunner\n' \
  > "$FIXTURE/storage/room/src/main/kotlin/app/openstory/storage/room/Bad.kt"
if REPO_ROOT="$FIXTURE" bash "$ROOT_DIR/scripts/structural-review-report.sh" >/dev/null 2>&1; then
  echo 'storage runtime implementation import must fail' >&2
  exit 1
fi

echo 'structural-review-report.sh contract verified.'
