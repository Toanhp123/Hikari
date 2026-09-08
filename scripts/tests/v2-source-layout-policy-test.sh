#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="${REPO_ROOT:-$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)}"
ALLOWLIST="$ROOT_DIR/config/source-layout-allowlist.txt"
VERIFY_SCRIPT="$ROOT_DIR/scripts/verify-source-layout.sh"

fail() {
  echo "$1" >&2
  exit 1
}

[[ -f "$ALLOWLIST" ]] || fail "Missing source-layout allowlist: $ALLOWLIST"
[[ -x "$VERIFY_SCRIPT" ]] || fail "Missing executable source-layout verifier: $VERIFY_SCRIPT"

active_rows=0
while IFS= read -r row || [[ -n "$row" ]]; do
  [[ -z "${row//[[:space:]]/}" ]] && continue
  [[ "$row" =~ ^[[:space:]]*# ]] && continue

  active_rows=$((active_rows + 1))
  IFS='|' read -r path max_lines reason extra <<< "$row"
  [[ -n "$path" && "$max_lines" =~ ^[0-9]+$ && -n "$reason" && -z "$extra" ]] ||
    fail "Invalid source-layout allowlist row: $row"

  if [[ "$reason" =~ [Tt]emporary|[Pp]ending|[Gg]eneration|[Vv][0-9] ]]; then
    fail "Temporary or generation debt is forbidden in the V2 source-layout allowlist: $row"
  fi
  [[ -e "$ROOT_DIR/$path" ]] || fail "Stale source-layout allowlist target: $path"
done < "$ALLOWLIST"

((active_rows == 0)) ||
  fail "V2 Step 1 requires zero active source-layout allowances; found $active_rows."

FIXTURE="$(mktemp -d)"
trap 'rm -rf "$FIXTURE"' EXIT
mkdir -p "$FIXTURE/config" "$FIXTURE/core/sample/src/main/kotlin"
printf 'class Sample\n' > "$FIXTURE/core/sample/src/main/kotlin/Sample.kt"
printf '# path|max_lines|reviewed reason\n' > "$FIXTURE/config/source-layout-allowlist.txt"
REPO_ROOT="$FIXTURE" "$VERIFY_SCRIPT" >/dev/null

printf '%s\n' \
  'core/sample/src/main/kotlin/Missing.kt|600|Reviewed extraction ceiling.' \
  > "$FIXTURE/config/source-layout-allowlist.txt"
if REPO_ROOT="$FIXTURE" "$VERIFY_SCRIPT" >/dev/null 2>&1; then
  fail 'Source-layout verification accepted a missing allowlist target.'
fi

printf '%s\n' \
  'core/sample/src/main/kotlin/Sample.kt|600|Temporary migration ceiling.' \
  > "$FIXTURE/config/source-layout-allowlist.txt"
if REPO_ROOT="$FIXTURE" "$VERIFY_SCRIPT" >/dev/null 2>&1; then
  fail 'Source-layout verification accepted temporary debt language.'
fi

echo "V2 source-layout policy verified."
