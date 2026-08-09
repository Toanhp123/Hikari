#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="${OPENSTORY_ROOT_DIR:-$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)}"
FILE="$ROOT_DIR/docs/internal/architecture-baseline-2/invariant-inventory.md"

test -f "$FILE"
grep -q '| KEEP |' "$FILE"
grep -q '| CHANGE |' "$FILE"
grep -q '| DELETE |' "$FILE"
grep -q 'Selector runtime' "$FILE"
grep -q 'partial source failure' "$FILE"
grep -q 'OpenStoryAppGraph' "$FILE"
