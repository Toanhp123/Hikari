# Architecture Baseline 2 R0 - Freeze and Guardrails Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Freeze Wave 06, revalidate the behavior worth preserving, and install transition-safe guardrails before any production architecture replacement begins.

**Architecture:** R0 changes governance and verification, not product behavior. Existing architecture is temporarily grandfathered, while new debt is blocked through explicit suppression/package-boundary checks and a written invariant/test migration inventory.

**Tech Stack:** Gradle build logic, Bash verification, Markdown governance/checkpoint records, Detekt policy.

## Global Constraints

- Architecture source of truth: `docs/superpowers/specs/2026-08-09-architecture-baseline-2-design.md`.
- This work is pre-Wave-06; do not implement Library, chapter sync, Reader, downloads, background sync, authentication, notifications, or release-hardening behavior.
- Android-native Kotlin remains fixed.
- Package namespace/application ID remains `app.openstory`.
- Minimum SDK remains 26; compile/target SDK remain 37 unless a dedicated architecture decision changes them.
- Build runtime remains JDK 17, Gradle 9.5, Android Gradle Plugin 9.3.0, Kotlin 2.4.10.
- Current retained libraries may be replaced only when a plan task explicitly does so; do not change versions opportunistically during this reset.
- Pre-MVP compatibility is intentionally breakable. Do not add permanent `Legacy*`, `Compat*`, `V1/V2` adapters, dual mappers, or Room migrations merely to preserve development-only contracts.
- Temporary migration-scoped bridges are allowed only when this plan names the bridge and its deletion task explicitly.
- Package-first, Gradle-module-second: do not create extra production modules beyond the approved target graph without a new architecture decision.
- TDD is mandatory for behavior changes: focused RED -> smallest GREEN -> affected module suite -> commit.
- Every task ends in a buildable, testable, independently reviewable repository state.
- Do not make a checkpoint green with `TODO()`, `error("not implemented")`, unconditional empty production results, or broad structural suppressions.
- Tests protect revalidated product/security invariants, not historical class shapes.
- Production Room entities/DAOs stay private to the storage adapter.
- Production plugin JavaScript receives only host-controlled capabilities and never Android `Context`, Room, filesystem paths, raw OkHttp, reflection, or plaintext managed credentials.

---
### Task 1: Freeze execution at Architecture Baseline 2

**Files:**
- Modify: `docs/project/current-state.md`
- Modify: `docs/implementation/current-roadmap.md`
- Modify: `docs/project/document-governance.md`
- Create: `scripts/tests/architecture-baseline-2-state-test.sh`

**Interfaces:**
- Consumes: approved Baseline 2 design spec plus the Wave 05 checkpoint only as historical/current-position evidence; Wave 05 acceptance is not authority for what Baseline 2 keeps.
- Produces: canonical execution state `Architecture Baseline 2 / R0`, with Wave 06 explicitly frozen.

- [ ] **Step 1: Write the failing state-governance test**

Create `scripts/tests/architecture-baseline-2-state-test.sh`:

```bash
#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="${OPENSTORY_ROOT_DIR:-$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)}"

grep -q 'Architecture Baseline 2' "$ROOT_DIR/docs/project/current-state.md"
grep -q 'Wave 06.*frozen\|do not begin Wave 06' "$ROOT_DIR/docs/project/current-state.md"
grep -q 'Architecture Baseline 2' "$ROOT_DIR/docs/implementation/current-roadmap.md"
! grep -q 'Begin Wave 06 Task 01' "$ROOT_DIR/docs/implementation/current-roadmap.md"
```

- [ ] **Step 2: Run the focused test and verify RED**

Run:

```bash
bash scripts/tests/architecture-baseline-2-state-test.sh
```

Expected: **FAIL** because current state still names Wave 06 Task 01 as the active boundary.

- [ ] **Step 3: Update canonical execution documents**

Change `docs/project/current-state.md` executive state to:

```text
Current active boundary: Architecture Baseline 2 - R0 Freeze and Guardrails.
Wave 06 is frozen until Architecture Baseline 2 R6 is accepted.
Wave 05 checkpoint remains historical evidence, not a compatibility requirement.
```

Change `docs/implementation/current-roadmap.md` current position/execution rule to:

```text
Architecture Baseline 2 is active.
Do not begin Wave 06.
Execute R0 -> R6 from the Baseline 2 plan set.
```

Change `docs/project/document-governance.md` so the implementation-next precedence points to the Baseline 2 roadmap while this reset is active.

- [ ] **Step 4: Re-run the focused test**

Run:

```bash
bash scripts/tests/architecture-baseline-2-state-test.sh
```

Expected: **PASS**.

- [ ] **Step 5: Commit**

```bash
git add docs/project/current-state.md docs/implementation/current-roadmap.md   docs/project/document-governance.md scripts/tests/architecture-baseline-2-state-test.sh
git commit -m "architecture: freeze wave 06 for baseline two"
```

### Task 2: Record the KEEP/CHANGE/DELETE invariant inventory

**Files:**
- Create: `docs/internal/architecture-baseline-2/invariant-inventory.md`
- Create: `scripts/tests/architecture-baseline-2-invariant-inventory-test.sh`

**Interfaces:**
- Consumes: Wave 01-05 source/tests and Baseline 2 decisions.
- Produces: normative migration classification used by R2-R4; old checkpoint acceptance is not normative by itself.

- [ ] **Step 1: Write the failing inventory-presence test**

```bash
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
```

- [ ] **Step 2: Verify RED**

Run the new script. Expected: **FAIL** because the inventory does not exist.

- [ ] **Step 3: Create the exact initial classification**

Create `docs/internal/architecture-baseline-2/invariant-inventory.md` with this minimum table:

```markdown
| Decision | Behavior / invariant | Baseline 2 rationale |
|---|---|---|
| KEEP | `app.openstory`, JDK 17, Android minSdk 26 / target 37 | platform/bootstrap contract still valid |
| KEEP | local-first cached catalog reads | user value and resilience |
| KEEP | plugin host controls network/files/platform access | trust boundary |
| KEEP | HTTPS host allowlist + redirect revalidation + bounded responses | security invariant |
| KEEP | package bytes verified before activation; failed install leaves prior state usable | atomic plugin lifecycle |
| KEEP | update capability expansion requires review; rollback restores prior immutable version | security/lifecycle invariant |
| KEEP | source-specific catalog metadata remains source-preserving | product requirement |
| KEEP | one catalog source failure does not erase another source or the previous complete snapshot | failure isolation |
| KEEP | matching/ranking is deterministic and pure | reproducibility |
| KEEP | Home, Search, and canonical Story journeys | revalidated current product surface |
| KEEP | MyAnimeList remains the production reference catalog; Home uses MAL top-manga ranking, Search uses the manga search API, Details preserves MAL manga ID/source URL/score/author/cover/genre/popularity metadata | concrete reference-plugin behavior to port through the new protocol |
| KEEP | Room as the Android persistence adapter implementation | target ownership changes; Room no longer defines domain boundaries |
| KEEP | Hilt as minimal compile-time wiring and Navigation 3 as app navigation | remove manual graph/factories rather than replacing frameworks without a concrete problem |
| KEEP | AndroidX JavaScriptEngine, OkHttp, and Jsoup inside the plugin security subsystem | they fit the single JS runtime + bounded HTTP/HTML capability design |
| CHANGE | canonical story model shape | keep concept; rebuild ownership/model |
| CHANGE | catalog repository and refresh/search/details orchestration | move to `:catalog`; durable-state repository only |
| CHANGE | plugin API and `.osp` package contract | replace Kotlin host contracts with pure wire protocol |
| CHANGE | JavaScript bridge | replace with operation protocol + capability broker |
| CHANGE | Room schema | reset to new schema 1; no dev migration chain |
| CHANGE | Hilt/manual composition | remove service-locator graph; constructor injection |
| CHANGE | Navigation Story route | canonical route carries only `StoryId` |
| CHANGE | tests/fixtures | port by invariant, not file |
| DELETE | declarative Selector runtime/schema as production execution model | JS-only runtime |
| DELETE | generic `:core:network` | current source audit shows it serves plugin HTTP/session policy; network becomes a plugin capability |
| DELETE | roadmap-wide `:core:model` | capability-owned models |
| DELETE | speculative Library/chapter/release/progress persistence before owning capability starts | YAGNI |
| DELETE | `OpenStoryAppGraph` and custom ViewModel factories | Hilt lifecycle wiring |
| DELETE | production default/selector demonstration catalogs | MAL is the production reference plugin |
| DELETE | structural suppression used only to satisfy Detekt | anti-gaming rule |
```

- [ ] **Step 4: Re-run inventory test**

Expected: **PASS**.

- [ ] **Step 5: Commit**

```bash
git add docs/internal/architecture-baseline-2/invariant-inventory.md   scripts/tests/architecture-baseline-2-invariant-inventory-test.sh
git commit -m "architecture: revalidate baseline invariants"
```

### Task 3: Inventory legacy tests by invariant

**Files:**
- Create: `docs/internal/architecture-baseline-2/test-migration-inventory.md`
- Create: `scripts/tests/architecture-baseline-2-test-inventory-test.sh`

**Interfaces:**
- Consumes: current Wave 01-05 tests.
- Produces: exact `REWRITE`, `KEEP_UNTIL_REPLACED`, or `DELETE_WITH_OWNER` migration intent for high-value suites.

- [ ] **Step 1: Write the failing inventory test**

The test must require entries for these current suites:

```text
core/plugin-api/src/test/kotlin/app/openstory/plugin/api/PluginManifestTest.kt
core/plugin-api/src/test/kotlin/app/openstory/plugin/api/testing/PluginContractSuiteTest.kt
core/plugin-host/src/test/kotlin/app/openstory/plugin/host/js/JavaScriptPluginRuntimeTest.kt
core/plugin-host/src/test/kotlin/app/openstory/plugin/host/selector/runtime/SelectorEndpointCoverageTest.kt
core/database/src/androidTest/kotlin/app/openstory/database/repository/RoomCatalogRepositoryTest.kt
core/matching/src/test/kotlin/app/openstory/matching/CatalogStoryResolverTest.kt
feature/home/src/test/kotlin/app/openstory/home/domain/RefreshHomeTest.kt
feature/home/src/test/kotlin/app/openstory/home/domain/SearchCatalogsTest.kt
feature/story/src/test/kotlin/app/openstory/story/ui/StoryDetailViewModelTest.kt
app/src/androidTest/kotlin/app/openstory/MyAnimeListCatalogContractIntegrationTest.kt
```

Implement the shell test with `grep -F` for every path.

- [ ] **Step 2: Verify RED**

Expected: **FAIL**.

- [ ] **Step 3: Create the migration table**

Use these classifications:

```markdown
| Current test | Action | New owner / reason |
|---|---|---|
| `core/plugin-api/src/test/kotlin/app/openstory/plugin/api/PluginManifestTest.kt` | REWRITE | `:plugins:api`; new pure manifest |
| `core/plugin-api/src/test/kotlin/app/openstory/plugin/api/testing/PluginContractSuiteTest.kt` | REWRITE | `:plugins:api`; protocol contract suite |
| `core/plugin-host/src/test/kotlin/app/openstory/plugin/host/js/JavaScriptPluginRuntimeTest.kt` | REWRITE | `:plugins:runtime`; isolated operation runtime |
| `core/plugin-host/src/test/kotlin/app/openstory/plugin/host/selector/runtime/SelectorEndpointCoverageTest.kt` | DELETE_WITH_OWNER | Selector runtime removed in R2 |
| `core/database/src/androidTest/kotlin/app/openstory/database/repository/RoomCatalogRepositoryTest.kt` | REWRITE | `:storage:room`; semantic catalog commit contract |
| `core/matching/src/test/kotlin/app/openstory/matching/CatalogStoryResolverTest.kt` | REWRITE | `:catalog`; pure matcher |
| `feature/home/src/test/kotlin/app/openstory/home/domain/RefreshHomeTest.kt` | REWRITE | `:catalog`; refresh service |
| `feature/home/src/test/kotlin/app/openstory/home/domain/SearchCatalogsTest.kt` | REWRITE | `:catalog`; search service |
| `feature/story/src/test/kotlin/app/openstory/story/ui/StoryDetailViewModelTest.kt` | REWRITE | R4 `:feature:catalog` ViewModel |
| `app/src/androidTest/kotlin/app/openstory/MyAnimeListCatalogContractIntegrationTest.kt` | REWRITE | community-style MAL reference contract |
```

Add `KEEP_UNTIL_REPLACED` rows for security tests whose invariant must stay active until the replacement runtime is green: archive traversal/size, redirect denial, rollback, and secret redaction.

- [ ] **Step 4: Re-run inventory test**

Expected: **PASS**.

- [ ] **Step 5: Commit**

```bash
git add docs/internal/architecture-baseline-2/test-migration-inventory.md   scripts/tests/architecture-baseline-2-test-inventory-test.sh
git commit -m "test: map legacy suites to baseline invariants"
```

### Task 4: Add structural suppression policy with one explicit legacy allowance

**Files:**
- Create: `config/quality/structural-suppressions.txt`
- Create: `scripts/verify-structural-suppressions.sh`
- Create: `scripts/tests/verify-structural-suppressions-test.sh`

**Interfaces:**
- Consumes: production Kotlin source.
- Produces: default-deny policy for `LargeClass`, `LongMethod`, `TooManyFunctions`, `ComplexMethod`, `LongParameterList`, `NestedBlockDepth`.

- [ ] **Step 1: Write the failing checker test**

Create `scripts/tests/verify-structural-suppressions-test.sh` with an isolated fixture root:

```bash
#!/usr/bin/env bash
set -euo pipefail
ROOT="$(mktemp -d)"
trap 'rm -rf "$ROOT"' EXIT
mkdir -p "$ROOT/feature/home/src/main/kotlin/app/openstory/home/ui" "$ROOT/config/quality"
cat > "$ROOT/feature/home/src/main/kotlin/app/openstory/home/ui/SearchScreen.kt" <<'KT'
@file:Suppress("TooManyFunctions")
package app.openstory.home.ui
KT
: > "$ROOT/config/quality/structural-suppressions.txt"
if REPO_ROOT="$ROOT" bash scripts/verify-structural-suppressions.sh; then
  echo "expected unlisted structural suppression to fail" >&2; exit 1
fi
echo 'feature/home/src/main/kotlin/app/openstory/home/ui/SearchScreen.kt|TooManyFunctions|fixture|R4' \
  > "$ROOT/config/quality/structural-suppressions.txt"
REPO_ROOT="$ROOT" bash scripts/verify-structural-suppressions.sh
sed -i 's/TooManyFunctions/LongMethod/' "$ROOT/config/quality/structural-suppressions.txt"
if REPO_ROOT="$ROOT" bash scripts/verify-structural-suppressions.sh; then
  echo "expected path with wrong rule to fail" >&2; exit 1
fi
```

- [ ] **Step 2: Verify RED**

Run:

```bash
bash scripts/tests/verify-structural-suppressions-test.sh
```

Expected: **FAIL** because the checker does not exist.

- [ ] **Step 3: Implement the checker and the single transition allowance**

Create `config/quality/structural-suppressions.txt`:

```text
feature/home/src/main/kotlin/app/openstory/home/ui/SearchScreen.kt|TooManyFunctions|legacy Wave 05 screen pending R4 replacement|R4
```

`verify-structural-suppressions.sh` must scan `*/src/main/**/*.kt`, extract structural suppressions, and require an exact `path|rule` row. Ignore non-structural suppressions such as `UNCHECKED_CAST`; do not whitelist them here.

- [ ] **Step 4: Run checker against the repository**

```bash
bash scripts/tests/verify-structural-suppressions-test.sh
bash scripts/verify-structural-suppressions.sh
```

Expected: both **PASS** and the current SearchScreen suppression is the only structural allowance.

- [ ] **Step 5: Commit**

```bash
git add config/quality/structural-suppressions.txt   scripts/verify-structural-suppressions.sh   scripts/tests/verify-structural-suppressions-test.sh
git commit -m "quality: gate structural suppressions"
```

### Task 5: Add package-boundary verifier that is safe before target modules exist

**Files:**
- Create: `scripts/verify-package-boundaries.sh`
- Create: `scripts/tests/verify-package-boundaries-test.sh`

**Interfaces:**
- Consumes: target module directories when present.
- Produces: source-level checks that complement Gradle module boundaries.

- [ ] **Step 1: Write failing fixture tests**

Create `scripts/tests/verify-package-boundaries-test.sh`. The fixture helper writes one Kotlin file, invokes the checker with `REPO_ROOT`, and asserts the exit code:

```bash
#!/usr/bin/env bash
set -euo pipefail
run_case() {
  local path="$1" import_line="$2" expected="$3"
  local root; root="$(mktemp -d)"
  mkdir -p "$root/$(dirname "$path")"
  printf 'package fixture\n%s\n' "$import_line" > "$root/$path"
  if REPO_ROOT="$root" bash scripts/verify-package-boundaries.sh >/dev/null 2>&1; then actual=0; else actual=1; fi
  rm -rf "$root"
  [[ "$actual" == "$expected" ]] || { echo "case failed: $path $import_line" >&2; exit 1; }
}
run_case 'feature/catalog/src/main/kotlin/F.kt' 'import app.openstory.storage.room.OpenStoryDatabase' 1
run_case 'feature/catalog/src/main/kotlin/F.kt' 'import app.openstory.plugins.runtime.PluginRuntime' 1
run_case 'storage/room/src/main/kotlin/F.kt' 'import app.openstory.plugins.runtime.execution.PluginOperationRunner' 1
run_case 'plugins/api/src/main/kotlin/F.kt' 'import android.content.Context' 1
run_case 'catalog/src/main/kotlin/F.kt' 'import androidx.compose.runtime.Composable' 1
run_case 'storage/room/src/main/kotlin/F.kt' 'import app.openstory.plugins.runtime.persistence.PluginStateStore' 0
run_case 'catalog/src/main/kotlin/F.kt' 'import app.openstory.plugins.runtime.PluginRuntime' 0
```

These cases correspond to the forbidden/allowed imports below.

Create temporary fake roots proving these fail:

```text
feature/catalog imports app.openstory.storage.room.*
feature/catalog imports app.openstory.plugins.runtime.*
storage/room imports app.openstory.plugins.runtime.execution.*
storage/room imports app.openstory.plugins.runtime.capabilities.*
plugins/api imports android.*
catalog imports androidx.compose.*
```

And these pass:

```text
storage/room imports app.openstory.plugins.runtime.persistence.*
catalog imports app.openstory.plugins.runtime.PluginRuntime
feature/catalog imports app.openstory.catalog.*
```

- [ ] **Step 2: Verify RED**

Expected: **FAIL**.

- [ ] **Step 3: Implement `verify-package-boundaries.sh`**

The script must skip target directories that do not exist yet, so R0 remains green before R1 creates them. Use exact source roots:

```text
feature/catalog/src/main
storage/room/src/main
plugins/api/src/main
catalog/src/main
```

- [ ] **Step 4: Run fixture and repository checks**

```bash
bash scripts/tests/verify-package-boundaries-test.sh
bash scripts/verify-package-boundaries.sh
```

Expected: **PASS**.

- [ ] **Step 5: Commit**

```bash
git add scripts/verify-package-boundaries.sh   scripts/tests/verify-package-boundaries-test.sh
git commit -m "architecture: add package boundary checks"
```

### Task 6: Wire R0 gates into verification and record R0 acceptance

**Files:**
- Modify: `scripts/verify.sh`
- Create: `docs/internal/checkpoints/architecture-baseline-2-r0.md`
- Modify: `docs/project/current-state.md`

**Interfaces:**
- Consumes: Tasks 1-5.
- Produces: accepted R0 boundary and next entry point R1.

- [ ] **Step 1: Add the new fast gates before Gradle verification**

Insert into `scripts/verify.sh` after existing shell test scripts:

```bash
./scripts/verify-structural-suppressions.sh
./scripts/verify-package-boundaries.sh
```

- [ ] **Step 2: Run the complete fast verification**

```bash
./scripts/verify.sh
```

Expected: **BUILD SUCCESSFUL** and all new shell gates pass.

- [ ] **Step 3: Run architecture verification explicitly**

```bash
./scripts/check-module-dependencies.sh
```

Expected: **BUILD SUCCESSFUL**.

- [ ] **Step 4: Record actual R0 evidence**

Create `docs/internal/checkpoints/architecture-baseline-2-r0.md` with the exact commands above, date, environment, and actual PASS/FAIL output summary. Do not mark unrun commands as PASS.

Update `docs/project/current-state.md` only after evidence is reviewed:

```text
Architecture Baseline 2 R0: ACCEPTED
Current active boundary: R1 - Foundation and Module Graph
Wave 06 remains frozen.
```

- [ ] **Step 5: Commit**

```bash
git add scripts/verify.sh docs/internal/checkpoints/architecture-baseline-2-r0.md   docs/project/current-state.md
git commit -m "architecture: accept baseline two r0"
```
