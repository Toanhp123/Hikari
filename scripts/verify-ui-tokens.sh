#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$ROOT_DIR"

violations=0

is_token_definition() {
  case "$1" in
    core/designsystem/src/main/kotlin/app/openstory/designsystem/theme/*.kt) return 0 ;;
    *) return 1 ;;
  esac
}

check_file() {
  local file="$1"
  local pattern="$2"
  local label="$3"
  local matches
  matches="$(grep -nE "$pattern" "$file" || true)"
  if [[ -n "$matches" ]]; then
    echo "UI token policy violation ($label): $file" >&2
    printf '%s\n' "$matches" >&2
    violations=1
  fi
}

while IFS= read -r file; do
  relative="${file#./}"
  if is_token_definition "$relative"; then
    continue
  fi

  check_file "$relative" '(^|[^[:alnum:]_])([0-9]+([.][0-9]+)?)[.]dp([^[:alnum:]_]|$)' 'dp literal'
  check_file "$relative" '(^|[^[:alnum:]_])([0-9]+([.][0-9]+)?)[.]sp([^[:alnum:]_]|$)' 'sp literal'
  check_file "$relative" 'RoundedCornerShape[[:space:]]*\(' 'local rounded shape'
  check_file "$relative" 'MaterialTheme[.]shapes[.]' 'raw Material shape token'
  check_file "$relative" '(^|[^[:alnum:]_])CircleShape([^[:alnum:]_]|$)' 'local circle shape'
  check_file "$relative" 'Color[.](White|Black|Red|Green|Blue|Gray|Transparent)' 'direct palette color'
  check_file "$relative" 'Color[[:space:]]*\(0x' 'direct hex color'
  check_file "$relative" 'copy[[:space:]]*\([[:space:]]*alpha[[:space:]]*=[[:space:]]*[0-9]' 'literal alpha'
  check_file "$relative" 'FontWeight[.]' 'local font weight'
  check_file "$relative" 'FontFamily[.]' 'local font family'
done < <(
  for source_root in app feature core/designsystem; do
    [[ -d "$source_root" ]] || continue
    find "$source_root" -type f -path '*/src/main/kotlin/*' -name '*.kt'
  done | sort
)

if [[ "$violations" -ne 0 ]]; then
  exit 1
fi

echo "UI token policy verified."
