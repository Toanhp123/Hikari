#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="${REPO_ROOT:-${OPENSTORY_ROOT_DIR:-$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)}}"

fail() {
  echo "$1" >&2
  exit 1
}

if git -C "$ROOT_DIR" rev-parse --is-inside-work-tree >/dev/null 2>&1; then
  TRACKED_IDE="$(git -C "$ROOT_DIR" ls-files '.idea')"
  [[ -z "$TRACKED_IDE" ]] || fail "Tracked IDE metadata is forbidden: $TRACKED_IDE"
elif [[ -d "$ROOT_DIR/.idea" ]]; then
  fail "IDE metadata directory is forbidden: $ROOT_DIR/.idea"
fi

[[ ! -f "$ROOT_DIR/config/detekt/baseline.xml" ]] ||
  fail "Detekt baseline debt is forbidden."

if [[ -d "$ROOT_DIR/storage/room/src/main" ]] &&
  grep -R -n -E '^import app\.openstory\.plugin\.host\.install\.' \
    "$ROOT_DIR/storage/room/src/main" >/dev/null; then
  fail "Database production source imports plugin installer internals."
fi

while IFS= read -r -d '' source_file; do
  relative_path="${source_file#"$ROOT_DIR"/}"
  file_name="$(basename "$source_file")"

  if [[ "$file_name" =~ ([Vv]1|[Vv]2|[Ll]egacy|[Cc]ompat) ]]; then
    fail "Generation-labelled active source filename is forbidden: $relative_path"
  fi

  if [[ "$relative_path" =~ /src/(test|androidTest)/ ]] &&
    [[ "$file_name" =~ (Wave[0-9]+|Checkpoint|Remediation) ]]; then
    fail "Development-history test filename is forbidden: $relative_path"
  fi

  line_count="$(wc -l < "$source_file")"
  if [[ "$relative_path" =~ /src/main/ ]] && ((line_count > 500)); then
    fail "Production Kotlin source exceeds 500 lines: $relative_path ($line_count)"
  fi
  if [[ "$relative_path" =~ /src/main/ ]] && ((line_count > 300)); then
    echo "Structural review candidate exceeds 300 lines: $relative_path ($line_count)" >&2
  fi
  if [[ "$relative_path" =~ /src/(test|androidTest)/ ]] && ((line_count > 750)); then
    fail "Test Kotlin source exceeds 750 lines: $relative_path ($line_count)"
  fi
done < <(
  find "$ROOT_DIR" \
    -path '*/build' -prune -o \
    -path '*/.gradle' -prune -o \
    -path '*/.git' -prune -o \
    -path '*/docs/internal/archive' -prune -o \
    -type f -name '*.kt' -print0
)

echo "Source layout verified."
