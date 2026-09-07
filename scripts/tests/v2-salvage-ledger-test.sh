#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="${REPO_ROOT:-$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)}"
LEDGER="$ROOT_DIR/docs/internal/v2/v1-salvage-ledger.md"

[[ -f "$LEDGER" ]] || {
  echo "Missing V2 salvage ledger: $LEDGER" >&2
  exit 1
}

required_rows=(
  '| `:reader:engine` | KEEP | TRANSPLANT |'
  '| `:plugins:api` | KEEP | TRANSPLANT |'
  '| `:catalog:engine` | REDESIGN | QUARANTINE |'
  '| `:catalog:model` | REDESIGN | QUARANTINE |'
  '| V1 Home aggregate implementation | DROP | NONE |'
  '| plugin host network/files/platform trust boundary | KEEP | REFERENCE |'
  '| HTTPS allowlist + redirect revalidation + bounded responses | KEEP | REFERENCE |'
  '| package verification + atomic activation/rollback semantics | KEEP | REFERENCE |'
  '| Reader checksum/security invalidation semantics | KEEP | REFERENCE |'
)

for row in "${required_rows[@]}"; do
  grep -Fq "$row" "$LEDGER" || {
    echo "Missing required salvage classification: $row" >&2
    exit 1
  }
done

if grep -Eiq '\b(TO''DO|T''BD|FIX''ME)\b' "$LEDGER"; then
  echo "Salvage ledger contains unresolved placeholder text." >&2
  exit 1
fi

echo "V2 salvage ledger verified."
