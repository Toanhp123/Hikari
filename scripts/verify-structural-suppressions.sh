#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="${REPO_ROOT:-$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)}"
ALLOWLIST="$ROOT_DIR/config/quality/structural-suppressions.txt"

STRUCTURAL_RULES='^(LargeClass|LongMethod|TooManyFunctions|ComplexMethod|LongParameterList|NestedBlockDepth)$'

if [[ ! -f "$ALLOWLIST" ]]; then
  echo "Missing structural suppression allowlist: $ALLOWLIST" >&2
  exit 1
fi

observed="$(mktemp)"
trap 'rm -f "$observed"' EXIT

while IFS= read -r -d '' file; do
  relative="${file#"$ROOT_DIR"/}"
  relative="${relative//\\//}"
  while IFS= read -r annotation; do
    while IFS= read -r quoted; do
      rule="${quoted#\"}"
      rule="${rule%\"}"
      if [[ "$rule" =~ $STRUCTURAL_RULES ]]; then
        printf '%s|%s\n' "$relative" "$rule" >> "$observed"
      fi
    done < <(printf '%s\n' "$annotation" | grep -oE '"[^"]+"' || true)
  done < <(grep -E '@(file:)?Suppress\(' "$file" || true)
done < <(find "$ROOT_DIR" -type f -path '*/src/main/*' -name '*.kt' -print0)

sort -u -o "$observed" "$observed"

while IFS='|' read -r path rule reason removal; do
  [[ -z "${path// }" ]] && continue
  [[ "$path" == \#* ]] && continue
  if [[ -z "$rule" || -z "$reason" || -z "$removal" ]]; then
    echo "Invalid structural suppression allowlist row: $path|$rule|$reason|$removal" >&2
    exit 1
  fi
  if ! grep -Fxq "$path|$rule" "$observed"; then
    echo "Stale structural suppression allowance: $path|$rule" >&2
    exit 1
  fi
done < "$ALLOWLIST"

while IFS= read -r suppression; do
  [[ -z "$suppression" ]] && continue
  if ! grep -Fq "$suppression|" "$ALLOWLIST"; then
    echo "Unapproved structural suppression: $suppression" >&2
    exit 1
  fi
done < "$observed"

echo "Structural suppression policy verified."
