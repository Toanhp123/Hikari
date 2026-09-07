#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="${REPO_ROOT:-$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)}"
PROVENANCE="$ROOT_DIR/docs/internal/v2/cutover-provenance.md"
SETTINGS="$ROOT_DIR/settings.gradle.kts"

[[ -f "$PROVENANCE" ]] || {
  echo "Missing V2 cutover provenance: $PROVENANCE" >&2
  exit 1
}

grep -Fq 'Strategy: same Hikari Git repository; isolated V2 branch/workspace' "$PROVENANCE"
grep -Fq 'Second repository/project root: forbidden' "$PROVENANCE"
grep -Fq 'Peer `:app-v2`: forbidden' "$PROVENANCE"
grep -Eq '^- V1 cutover base commit: `[0-9a-f]{40}`$' "$PROVENANCE"

if grep -Fq 'include(":app-v2")' "$SETTINGS" || [[ -e "$ROOT_DIR/app-v2" ]]; then
  echo 'Peer :app-v2 is forbidden by the V2 cutover strategy.' >&2
  exit 1
fi

for path in app reader/engine catalog/engine plugins/api; do
  [[ -e "$ROOT_DIR/$path" ]] || {
    echo "Expected same-repository retained path is missing: $path" >&2
    exit 1
  }
done

echo 'V2 same-repository cutover context verified.'
