#!/usr/bin/env bash
set -euo pipefail

SDK="${REPO_ROOT:-$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)}/docs/plugin-sdk"

grep -Rqs 'protocol' "$SDK"
grep -Rqs 'main.js' "$SDK"
for op in catalog.home catalog.search catalog.details catalog.filters content.search content.resolveUrl content.chapters content.chapter; do
  grep -Rqs "$op" "$SDK"
done
grep -Rqs 'sha256' "$SDK"
grep -Rqs '`operations`' "$SDK"
grep -Rqs 'Protocol `1` packages' "$SDK"
for capability in 'host.http' 'host.html' 'host.log'; do
  grep -Rqs "$capability" "$SDK"
done
if grep -RqiE 'Selector Schema|selector.json|declarative runtime' "$SDK"; then
  echo "obsolete selector/declarative SDK text remains" >&2
  exit 1
fi

echo "Plugin SDK current-contract documentation verified."
