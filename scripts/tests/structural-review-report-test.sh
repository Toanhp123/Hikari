#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
FIXTURE="$(mktemp -d)"
trap 'rm -rf "$FIXTURE"' EXIT

mkdir -p \
  "$FIXTURE/catalog/src/main/kotlin/app/openstory/catalog" \
  "$FIXTURE/feature/catalog/src/main/kotlin/app/openstory/catalog/ui" \
  "$FIXTURE/storage/room/src/main/kotlin/app/openstory/storage/room"

for line in $(seq 1 301); do printf '// line %s\n' "$line"; done \
  > "$FIXTURE/catalog/src/main/kotlin/app/openstory/catalog/Large.kt"
touch "$FIXTURE/catalog/src/main/kotlin/app/openstory/catalog/SearchPart1.kt"
touch "$FIXTURE/catalog/src/main/kotlin/app/openstory/catalog/SearchPart2.kt"
{
  printf 'package app.openstory.catalog\nfun longOperation() {\n'
  for line in $(seq 1 51); do printf '  // body %s\n' "$line"; done
  printf '}\n'
} > "$FIXTURE/catalog/src/main/kotlin/app/openstory/catalog/LongFunction.kt"
cat > "$FIXTURE/catalog/src/main/kotlin/app/openstory/catalog/EightParameters.kt" <<'KT'
package app.openstory.catalog
data class EightParameters(
  val one: String,
  val two: String,
  val three: String,
  val four: String,
  val five: String,
  val six: String,
  val seven: String,
  val eight: Map<String, List<String>>,
)
KT
cat > "$FIXTURE/catalog/src/main/kotlin/app/openstory/catalog/NineParameters.kt" <<'KT'
package app.openstory.catalog
data class NineParameters(
  val one: String,
  val two: String,
  val three: String,
  val four: String,
  val five: String,
  val six: String,
  val seven: String,
  val eight: String,
  val nine: String,
)
KT
cat > "$FIXTURE/catalog/src/main/kotlin/app/openstory/catalog/NinePlainParameters.kt" <<'KT'
package app.openstory.catalog
class NinePlainParameters(
  one: String,
  two: String,
  three: String,
  four: String,
  five: String,
  six: String,
  seven: String,
  eight: String,
  nine: String,
)
KT
{
  printf 'package app.openstory.catalog\ninterface Operation { fun execute() }\nclass LongOverride : Operation {\n'
  printf '  override fun execute() {\n'
  for line in $(seq 1 51); do printf '    // body %s\n' "$line"; done
  printf '  }\n}\n'
} > "$FIXTURE/catalog/src/main/kotlin/app/openstory/catalog/LongOverride.kt"
printf 'package app.openstory.catalog.ui\nclass CleanViewModel\n' \
  > "$FIXTURE/feature/catalog/src/main/kotlin/app/openstory/catalog/ui/CleanViewModel.kt"
printf 'package app.openstory.storage.room\nimport app.openstory.plugins.runtime.persistence.PluginStateStore\n' \
  > "$FIXTURE/storage/room/src/main/kotlin/app/openstory/storage/room/Clean.kt"

report="$(REPO_ROOT="$FIXTURE" bash "$ROOT_DIR/scripts/structural-review-report.sh")"
grep -q 'Large.kt' <<< "$report"
grep -q 'SearchPart1.kt' <<< "$report"
grep -q '\[review\]\[function-lines\].*LongFunction.kt' <<< "$report"
grep -q '\[review\]\[constructor-parameters\].*NineParameters.kt (9)' <<< "$report"
grep -q '\[review\]\[constructor-parameters\].*NinePlainParameters.kt (9)' <<< "$report"
grep -q '\[review\]\[function-lines\].*LongOverride.kt' <<< "$report"
if grep -q '\[review\]\[constructor-parameters\].*EightParameters.kt' <<< "$report"; then
  echo 'eight constructor parameters must remain below the review threshold' >&2
  exit 1
fi

printf 'package app.openstory.catalog\nimport android.content.Context\n' \
  > "$FIXTURE/catalog/src/main/kotlin/app/openstory/catalog/Bad.kt"
if REPO_ROOT="$FIXTURE" bash "$ROOT_DIR/scripts/structural-review-report.sh" >/dev/null 2>&1; then
  echo 'catalog Android Context import must fail' >&2
  exit 1
fi
rm "$FIXTURE/catalog/src/main/kotlin/app/openstory/catalog/Bad.kt"

printf 'package app.openstory.catalog.ui\nimport kotlinx.coroutines.CoroutineScope\nclass BadViewModel\n' \
  > "$FIXTURE/feature/catalog/src/main/kotlin/app/openstory/catalog/ui/BadViewModel.kt"
if REPO_ROOT="$FIXTURE" bash "$ROOT_DIR/scripts/structural-review-report.sh" >/dev/null 2>&1; then
  echo 'feature ViewModel custom scope import must fail' >&2
  exit 1
fi
rm "$FIXTURE/feature/catalog/src/main/kotlin/app/openstory/catalog/ui/BadViewModel.kt"

printf 'package app.openstory.storage.room\nimport app.openstory.plugins.runtime.execution.PluginOperationRunner\n' \
  > "$FIXTURE/storage/room/src/main/kotlin/app/openstory/storage/room/Bad.kt"
if REPO_ROOT="$FIXTURE" bash "$ROOT_DIR/scripts/structural-review-report.sh" >/dev/null 2>&1; then
  echo 'storage runtime implementation import must fail' >&2
  exit 1
fi
rm "$FIXTURE/storage/room/src/main/kotlin/app/openstory/storage/room/Bad.kt"

printf 'package app.openstory.storage.room\nval bad = app.openstory.plugins.runtime.execution.PluginOperationRunner::class\n' \
  > "$FIXTURE/storage/room/src/main/kotlin/app/openstory/storage/room/Bad.kt"
if REPO_ROOT="$FIXTURE" bash "$ROOT_DIR/scripts/structural-review-report.sh" >/dev/null 2>&1; then
  echo 'storage fully-qualified runtime implementation reference must fail' >&2
  exit 1
fi
rm "$FIXTURE/storage/room/src/main/kotlin/app/openstory/storage/room/Bad.kt"

printf 'package app.openstory.storage.room\nval bad = app.openstory.plugins\n  .runtime.execution.PluginOperationRunner::class\n' \
  > "$FIXTURE/storage/room/src/main/kotlin/app/openstory/storage/room/Bad.kt"
if REPO_ROOT="$FIXTURE" bash "$ROOT_DIR/scripts/structural-review-report.sh" >/dev/null 2>&1; then
  echo 'storage split fully-qualified runtime reference must fail' >&2
  exit 1
fi
rm "$FIXTURE/storage/room/src/main/kotlin/app/openstory/storage/room/Bad.kt"

printf 'package app.openstory.storage.room\nval bad = app.openstory.plugins.runtime.persistenceEvil.PluginStateStore::class\n' \
  > "$FIXTURE/storage/room/src/main/kotlin/app/openstory/storage/room/Bad.kt"
if REPO_ROOT="$FIXTURE" bash "$ROOT_DIR/scripts/structural-review-report.sh" >/dev/null 2>&1; then
  echo 'storage lookalike runtime persistence package must fail' >&2
  exit 1
fi

echo 'structural-review-report.sh contract verified.'
