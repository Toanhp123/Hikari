#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="${REPO_ROOT:-$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)}"
HARD_FAILURE=0

relative_path() {
  local path="${1#"$ROOT_DIR"/}"
  printf '%s' "${path//\\//}"
}

report_matches() {
  local label="$1" pattern="$2" source_root="$3"
  [[ -d "$source_root" ]] || return 0
  local matches
  matches="$(grep -RInE --include='*.kt' "$pattern" "$source_root" || true)"
  if [[ -n "$matches" ]]; then
    echo "[hard] $label" >&2
    echo "$matches" >&2
    HARD_FAILURE=1
  fi
}

echo "Structural review report"

while IFS= read -r -d '' source_file; do
  relative="$(relative_path "$source_file")"
  file_name="$(basename "$source_file")"
  line_count="$(awk 'END { print NR }' "$source_file")"
  import_count="$(grep -Ec '^[[:space:]]*import[[:space:]]+' "$source_file" || true)"
  public_method_count="$(grep -Ec '^[[:space:]]*(public[[:space:]]+)?((override|inline|operator|infix|tailrec|external|suspend)[[:space:]]+)*fun[[:space:]]+' "$source_file" || true)"

  if ((line_count > 500)); then
    echo "[hard] production source exceeds 500 lines: $relative ($line_count)" >&2
    HARD_FAILURE=1
  elif ((line_count > 300)); then
    echo "[review][lines] $relative ($line_count)"
  fi

  if [[ "$file_name" =~ (Utils|Helpers|Misc|Part1|Part2)\.kt$ ]]; then
    echo "[review][generic-name] $relative"
  elif [[ "$file_name" =~ (Manager|Coordinator)\.kt$ ]]; then
    echo "[review][broad-name] $relative"
  fi

  if ((import_count > 15)); then
    echo "[review][imports] $relative ($import_count)"
  fi
  if ((public_method_count > 12)); then
    echo "[review][public-methods] $relative ($public_method_count)"
  fi

  awk -v file="$relative" '
    function braces(value, opened, closed) {
      opened = gsub(/\{/, "{", value)
      closed = gsub(/\}/, "}", value)
      return opened - closed
    }
    function parameter_count(value, pos, char, angle, square, round, count) {
      gsub(/^[[:space:]]+|[[:space:]]+$/, "", value)
      sub(/,[[:space:]]*$/, "", value)
      if (value == "") return 0
      count = 1
      for (pos = 1; pos <= length(value); pos++) {
        char = substr(value, pos, 1)
        if (char == "<") angle++
        else if (char == ">" && angle > 0) angle--
        else if (char == "[") square++
        else if (char == "]" && square > 0) square--
        else if (char == "(") round++
        else if (char == ")" && round > 0) round--
        else if (char == "," && angle == 0 && square == 0 && round == 0) count++
      }
      return count
    }
    /^[[:space:]]*(public[[:space:]]+|internal[[:space:]]+)?(data[[:space:]]+|sealed[[:space:]]+|abstract[[:space:]]+)?class[[:space:]]+/ {
      if ($0 ~ /\(/) {
        declaration = $0
        while (declaration !~ /\)/ && getline next_line > 0) declaration = declaration " " next_line
        parameters = declaration
        sub(/^[^(]*\(/, "", parameters)
        sub(/\).*/, "", parameters)
        count = parameter_count(parameters)
        if (count > 8) printf "[review][constructor-parameters] %s (%d)\n", file, count
      }
    }
    /^[[:space:]]*(public[[:space:]]+|internal[[:space:]]+|private[[:space:]]+|protected[[:space:]]+)?((override|inline|operator|infix|tailrec|external|suspend)[[:space:]]+)*fun[[:space:]]+/ {
      active = 1
      start = NR
      depth = braces($0)
      saw_brace = index($0, "{") > 0
      next
    }
    active {
      depth += braces($0)
      if (index($0, "{") > 0) saw_brace = 1
      if (saw_brace && depth <= 0) {
        span = NR - start + 1
        if (span > 50) printf "[review][function-lines] %s:%d (%d)\n", file, start, span
        active = 0
      }
    }
  ' "$source_file"
done < <(
  find "$ROOT_DIR" \
    -path '*/build' -prune -o \
    -path '*/.gradle' -prune -o \
    -path '*/.git' -prune -o \
    -path '*/docs/internal/archive' -prune -o \
    -type f -path '*/src/main/*' -name '*.kt' -print0
)

report_matches \
  'catalog must not import Android Context or AppDispatchers.' \
  '^[[:space:]]*import[[:space:]]+(android\.content\.Context|app\.openstory\.common\.dispatchers\.AppDispatchers)' \
  "$ROOT_DIR/catalog/src/main"

if [[ -d "$ROOT_DIR/feature/catalog/src/main" ]]; then
  while IFS= read -r -d '' view_model; do
    matches="$(grep -nE '^[[:space:]]*import[[:space:]]+(kotlinx\.coroutines\.(CoroutineScope|CoroutineDispatcher|Dispatchers|SupervisorJob)|app\.openstory\.common\.dispatchers\.AppDispatchers)' "$view_model" || true)"
    if [[ -n "$matches" ]]; then
      echo "[hard] feature ViewModels must use viewModelScope and injected services, not own scopes or dispatchers: $(relative_path "$view_model")" >&2
      echo "$matches" >&2
      HARD_FAILURE=1
    fi
  done < <(find "$ROOT_DIR/feature/catalog/src/main" -type f -name '*ViewModel.kt' -print0)
fi

if [[ -d "$ROOT_DIR/storage/room/src/main" ]]; then
  while IFS= read -r match; do
    [[ -z "$match" ]] && continue
    import_name="${match#*import }"
    if [[ "$import_name" != app.openstory.plugins.runtime.persistence.* ]]; then
      echo "[hard] storage/room may import only plugins.runtime.persistence SPI contracts." >&2
      echo "$match" >&2
      HARD_FAILURE=1
    fi
  done < <(
    grep -RInE --include='*.kt' \
      '^[[:space:]]*import[[:space:]]+app\.openstory\.plugins\.runtime(\.|$)' \
      "$ROOT_DIR/storage/room/src/main" || true
  )

  while IFS= read -r -d '' source_file; do
    compact_source="$(tr -d '[:space:]' < "$source_file")"
    remainder="$(printf '%s\n' "$compact_source" | sed -E \
      's/app\.openstory\.plugins\.runtime\.persistence(\.([A-Za-z_][A-Za-z0-9_]*|\*))+/ALLOWED/g')"
    if [[ "$remainder" == *app.openstory.plugins.runtime* ]]; then
      echo '[hard] storage/room may reference only plugins.runtime.persistence SPI contracts.' >&2
      echo "$(relative_path "$source_file")" >&2
      HARD_FAILURE=1
    fi
  done < <(
    find "$ROOT_DIR/storage/room/src/main" -type f -name '*.kt' -print0
  )
fi

if ((HARD_FAILURE != 0)); then
  exit 1
fi

echo "Structural hard policies verified."
