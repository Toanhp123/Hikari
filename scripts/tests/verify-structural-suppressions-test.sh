#!/usr/bin/env bash
set -euo pipefail

ROOT="$(mktemp -d)"
trap 'rm -rf "$ROOT"' EXIT

mkdir -p "$ROOT/feature/home/src/main/kotlin/app/openstory/home/ui" "$ROOT/config/quality"
cat > "$ROOT/feature/home/src/main/kotlin/app/openstory/home/ui/SearchScreen.kt" <<'KT'
@file:Suppress("TooManyFunctions")
package app.openstory.home.ui
KT

: > "$ROOT/config/quality/structural-suppressions.txt"
if REPO_ROOT="$ROOT" bash scripts/verify-structural-suppressions.sh; then
  echo "expected unlisted structural suppression to fail" >&2
  exit 1
fi

echo 'feature/home/src/main/kotlin/app/openstory/home/ui/SearchScreen.kt|TooManyFunctions|fixture|R4' \
  > "$ROOT/config/quality/structural-suppressions.txt"
REPO_ROOT="$ROOT" bash scripts/verify-structural-suppressions.sh

sed -i 's/TooManyFunctions/LongMethod/' "$ROOT/config/quality/structural-suppressions.txt"
if REPO_ROOT="$ROOT" bash scripts/verify-structural-suppressions.sh; then
  echo "expected path with wrong rule to fail" >&2
  exit 1
fi
