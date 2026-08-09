# Architecture Baseline 2 R5 - Cleanup and Quality Hardening Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Remove every active remnant of the superseded architecture, tighten Detekt/source/module gates around the final module graph, and leave no migration debt for Wave 06.

**Architecture:** R5 adds no product behavior. It is a deletion/clarification checkpoint: remove stale samples/docs/scripts/test infrastructure, collapse unjustified abstractions, enforce final exact boundaries, and use structural metrics as review signals rather than line-count gaming.

**Tech Stack:** Detekt 2.0.0-alpha.5, Gradle build logic, Bash verification, Kotlin source audit, Markdown SDK/governance docs.

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
### Task 1: Remove stale sample/test infrastructure tied to deleted contracts

**Files:**
- Delete: `sample-plugins/`
- Delete: `test/fixtures/`
- Delete: `core/common/src/main/kotlin/app/openstory/common/AppResult.kt`
- Delete: `core/common/src/main/kotlin/app/openstory/common/AppError.kt`
- Delete: `core/common/src/main/kotlin/app/openstory/common/AppResultExtensions.kt`
- Modify: `settings.gradle.kts`
- Modify: `config/architecture/module-boundaries.json`

**Interfaces:**
- Test fixtures for vNext plugin protocol live with their owning module under `plugins/api/src/test/resources` or test-local builders.
- Cross-feature mega-fixture module is not retained by default.

- [ ] **Step 1: Write the RED stale-fixture dependency gate**

Add a source/Gradle check that fails while `:test:fixtures` is included or referenced:

```bash
! grep -R -n 'project(":test:fixtures")\|include(":test:fixtures")' \
  --include='*.kts' .
```

Expected before deletion: **FAIL** because the legacy module is still included or referenced. Any target test still consuming it must receive an owning-module test-local fixture in this same task; do not retain the cross-feature fixture module. Also add a source check that fails on production imports/usages of legacy `AppResult`/`AppError`; R3/R4 must already have migrated target code to `Outcome` or capability-owned failures.

- [ ] **Step 2: Delete stale fixtures/samples**

Remove old catalog/content/selector sample manifests that describe deleted protocol generations, then delete legacy `AppResult.kt`, `AppError.kt`, and `AppResultExtensions.kt` after the source check confirms no target consumer remains.

- [ ] **Step 3: Run module graph verification**

```bash
./gradlew projects verifyArchitecture --stacktrace
```

Expected: **BUILD SUCCESSFUL**.

- [ ] **Step 4: Verify no active selector/sample contract text remains**

```bash
! grep -R -n 'Selector Schema\|selector.json\|PluginRuntime.DECLARATIVE'   plugins catalog feature app sample-plugins test 2>/dev/null
```

Expected: exit 0.

- [ ] **Step 5: Commit**

```bash
git add -A
git commit -m "test: remove obsolete plugin fixture architecture"
```

### Task 2: Rewrite active Plugin SDK docs around vNext protocol

**Files:**
- Modify: `docs/plugin-sdk/api-versioning.md`
- Delete: `docs/plugin-sdk/declarative-plugin-schema.md`
- Modify: `docs/plugin-sdk/package-format.md`
- Modify: `docs/plugin-sdk/repository-index.md`
- Create: `docs/plugin-sdk/javascript-runtime.md`
- Create: `docs/plugin-sdk/contract-testing.md`
- Create: `scripts/tests/plugin-sdk-current-contract-test.sh`

**Interfaces:**
- SDK describes only the R2 protocol/runtime.
- It names MyAnimeList as reference fixture/package, not a privileged execution path.

- [ ] **Step 1: Write RED doc-currentness test**

Create `scripts/tests/plugin-sdk-current-contract-test.sh`:

```bash
#!/usr/bin/env bash
set -euo pipefail
SDK=docs/plugin-sdk
grep -Rqs 'protocol' "$SDK"
grep -Rqs 'main.js' "$SDK"
for op in catalog.home catalog.search catalog.details catalog.filters; do grep -Rqs "$op" "$SDK"; done
grep -Rqs 'sha256' "$SDK"
for capability in 'host.http' 'host.html' 'host.log'; do grep -Rqs "$capability" "$SDK"; done
if grep -RqiE 'Selector Schema|selector.json|declarative runtime' "$SDK"; then
  echo "obsolete selector/declarative SDK text remains" >&2; exit 1
fi
```

- [ ] **Step 2: Verify RED**

```bash
bash scripts/tests/plugin-sdk-current-contract-test.sh
```

Expected: **FAIL**.

- [ ] **Step 3: Rewrite docs from tested protocol**

Do not copy old Kotlin interface docs. Document wire JSON shapes and contract test commands from `:plugins:api`.

- [ ] **Step 4: Run doc test + plugin API tests**

```bash
bash scripts/tests/plugin-sdk-current-contract-test.sh
./gradlew :plugins:api:test --stacktrace
```

Expected: **PASS**.

- [ ] **Step 5: Commit**

```bash
git add docs/plugin-sdk scripts/tests/plugin-sdk-current-contract-test.sh
git commit -m "docs: align plugin sdk with vnext runtime"
```

### Task 3: Harden Detekt configuration around responsibility smells

**Files:**
- Modify: `config/detekt/detekt.yml`
- Modify: `scripts/verify-source-layout.sh`
- Modify: `scripts/tests/verify-source-layout-test.sh`
- Create: `scripts/structural-review-report.sh`
- Create: `scripts/tests/structural-review-report-test.sh`

**Interfaces:**
- Hard limit remains 500 production lines unless explicit reviewed exception.
- 300+ lines is reported as review signal, not automatic architecture failure.
- Structural report also prints candidate long classes/functions, constructor parameter counts, public-method counts, broad import spans, and generic bucket names (`Utils`, `Helpers`, `Misc`, `Part1/Part2`; `Manager`/`Coordinator` as review-only signals) for manual R5/R6 audit.

- [ ] **Step 1: Write RED source-layout/report tests**

Extend the shell fixtures with exact generated files:

```bash
root="$(mktemp -d)"
trap 'rm -rf "$root"' EXIT
mkdir -p "$root/catalog/src/main/kotlin/app/openstory/catalog"
yes '// line' | head -n 501 > "$root/catalog/src/main/kotlin/app/openstory/catalog/Huge.kt"
if REPO_ROOT="$root" bash scripts/verify-source-layout.sh; then
  echo "501-line production source must fail" >&2; exit 1
fi
yes '// line' | head -n 301 > "$root/catalog/src/main/kotlin/app/openstory/catalog/Large.kt"
rm "$root/catalog/src/main/kotlin/app/openstory/catalog/Huge.kt"
REPO_ROOT="$root" bash scripts/verify-source-layout.sh 2>&1 | grep -q 'Large.kt'
touch "$root/catalog/src/main/kotlin/app/openstory/catalog/SearchPart1.kt"
touch "$root/catalog/src/main/kotlin/app/openstory/catalog/SearchPart2.kt"
REPO_ROOT="$root" bash scripts/structural-review-report.sh 2>&1 | grep -q 'SearchPart1.kt'
```

Keep the existing structural-suppression fixture as a separate assertion rather than teaching this script to permit suppressions.

- [ ] **Step 2: Enable Detekt smells explicitly**

Use Detekt 2.0 configuration keys and keep these findings as **review warnings**, not architecture verdicts:

```yaml
complexity:
  LongMethod:
    active: true
    severity: Warning
    allowedLines: 50
  LargeClass:
    active: true
    severity: Warning
    allowedLines: 200
  TooManyFunctions:
    active: true
    severity: Warning
    allowedFunctionsPerFile: 15
    allowedFunctionsPerClass: 15
    allowedFunctionsPerInterface: 12
    allowedFunctionsPerObject: 15
  LongParameterList:
    active: true
    severity: Warning
    allowedFunctionParameters: 6
    allowedConstructorParameters: 8
  NestedBlockDepth:
    active: true
    severity: Warning
    allowedDepth: 4
  CyclomaticComplexMethod:
    active: true
    severity: Warning
    allowedComplexity: 14
```

Keep production source in scope and exclude generated/build/test source through exact existing Detekt path filters rather than suppressing findings in Kotlin. The 500-line source-layout rule remains the separate hard size gate; these Detekt thresholds are review signals.

- [ ] **Step 3: Implement structural review report**

The report is informational for 300-line/class/dependency/public-surface counts but returns non-zero for hard policy violations. Add final source-boundary checks that target `catalog` does not import Android `Context`/`AppDispatchers`, feature ViewModels do not own custom scopes/dispatchers, and `:storage:room` imports from plugin runtime only under `app.openstory.plugins.runtime.persistence`.

- [ ] **Step 4: Run quality gates**

```bash
bash scripts/tests/verify-source-layout-test.sh
bash scripts/tests/structural-review-report-test.sh
./scripts/verify-source-layout.sh
./scripts/structural-review-report.sh
./gradlew detekt --stacktrace
```

Expected: no unexplained target production finding.

- [ ] **Step 5: Commit**

```bash
git add config/detekt/detekt.yml scripts/verify-source-layout.sh   scripts/structural-review-report.sh scripts/tests
git commit -m "quality: harden structural code review gates"
```

### Task 4: Make final module graph exact and package rules non-transitional

**Files:**
- Modify: `config/architecture/module-boundaries.json`
- Modify: `scripts/verify-package-boundaries.sh`
- Modify: `scripts/tests/verify-package-boundaries-test.sh`
- Modify: `build-logic/src/test/kotlin/app/openstory/build/ModuleGraphTest.kt`

**Interfaces:**
- Final production modules exactly:

```text
:app
:core:common
:catalog
:feature:catalog
:storage:room
:plugins:api
:plugins:runtime
```

- Policy no longer mentions removed modules or migration allowances.

- [ ] **Step 1: Write RED final-graph test**

Extend `ModuleGraphTest.kt` with:

```kotlin
@Test fun finalBaselineTwoGraphContainsNoLegacyModules() {
    val settings = File("../settings.gradle.kts").readText()
    val policy = File("../config/architecture/module-boundaries.json").readText()
    val forbidden = listOf(
        ":core:model", ":core:database", ":core:matching", ":core:plugin-api",
        ":core:plugin-host", ":core:network", ":feature:home", ":feature:story", ":test:fixtures",
    )
    forbidden.forEach { module ->
        assertFalse(module in settings, "Legacy module still in settings: $module")
        assertFalse(module in policy, "Legacy module still in architecture policy: $module")
    }
}
```

- [ ] **Step 2: Change target policies from transitional allowlist to final exact/approved edges**

Use exact mode where actual dependencies are stable. Keep package-level restriction that storage may import only runtime persistence SPI.

- [ ] **Step 3: Run architecture suite**

```bash
./gradlew :build-logic:test verifyArchitecture --stacktrace
bash scripts/verify-package-boundaries.sh
```

Expected: **PASS**.

- [ ] **Step 4: Inspect Gradle dependency tree for accidental transitive architecture leaks**

```bash
./gradlew :feature:catalog:dependencies --configuration debugCompileClasspath
./gradlew :storage:room:dependencies --configuration debugCompileClasspath
```

Review project dependencies only; feature must not acquire storage/runtime project dependencies.

- [ ] **Step 5: Commit**

```bash
git add config/architecture/module-boundaries.json   scripts/verify-package-boundaries.sh scripts/tests/verify-package-boundaries-test.sh   build-logic/src/test/kotlin/app/openstory/build/ModuleGraphTest.kt
git commit -m "architecture: freeze final module boundaries"
```

### Task 5: Rename/remove Baseline 1 verification and stale checkpoint scripts

**Files:**
- Create: `scripts/verify-architecture-baseline-2.sh`
- Create: `scripts/tests/verify-architecture-baseline-2-test.sh`
- Delete: `scripts/verify-baseline-architecture.sh`
- Delete: `scripts/tests/verify-baseline-architecture-test.sh`
- Modify: `scripts/verify.sh`
- Rename/replace: `scripts/instrumentation/database.sh` -> `scripts/instrumentation/storage-room.sh`
- Rename/replace: `scripts/tests/instrumentation-database-test.sh` -> `scripts/tests/instrumentation-storage-room-test.sh`
- Delete: `scripts/checkpoints/database.sh`
- Delete: `scripts/tests/checkpoint-database-test.sh`
- Delete: `scripts/checkpoints/plugin-contracts.sh`
- Delete: `scripts/tests/checkpoint-plugin-contracts-test.sh`

**Interfaces:**
- New baseline verifier asserts final architecture, not Selector/old Room assumptions.

- [ ] **Step 1: Write RED Baseline 2 verifier fixture test**

Create `scripts/tests/verify-architecture-baseline-2-test.sh` around an isolated fake repo. At minimum mutate one invariant at a time and require failure:

```bash
#!/usr/bin/env bash
set -euo pipefail
ROOT="$(mktemp -d)"
trap 'rm -rf "$ROOT"' EXIT
make_valid_fixture "$ROOT"
REPO_ROOT="$ROOT" bash scripts/verify-architecture-baseline-2.sh
mkdir -p "$ROOT/core/plugin-host"
if REPO_ROOT="$ROOT" bash scripts/verify-architecture-baseline-2.sh; then echo 'legacy module accepted' >&2; exit 1; fi
rm -rf "$ROOT/core/plugin-host"
touch "$ROOT/bundled-plugins/myanimelist-catalog/selector.json"
if REPO_ROOT="$ROOT" bash scripts/verify-architecture-baseline-2.sh; then echo 'selector.json accepted' >&2; exit 1; fi
rm "$ROOT/bundled-plugins/myanimelist-catalog/selector.json"
printf '@file:Suppress("TooManyFunctions")\n' > "$ROOT/feature/catalog/src/main/kotlin/Bad.kt"
if REPO_ROOT="$ROOT" bash scripts/verify-architecture-baseline-2.sh; then echo 'unlisted suppression accepted' >&2; exit 1; fi
```

Define `make_valid_fixture` in the same test script to create the seven final module directories, one Room `1.json`, an empty suppression allowlist, and no deleted package prefixes.

- [ ] **Step 2: Implement/rename scripts**

Update `scripts/verify.sh` to call `verify-architecture-baseline-2.sh`.

- [ ] **Step 3: Run all shell contract tests**

```bash
for test_script in scripts/tests/*.sh; do bash "$test_script"; done
```

Expected: **PASS**.

- [ ] **Step 4: Run fast repository verification**

```bash
./scripts/verify.sh
```

Expected: **PASS**.

- [ ] **Step 5: Commit**

```bash
git add -A
git commit -m "architecture: replace baseline one verification"
```

### Task 6: Empty structural suppression debt and perform the explicit R5 ownership audit

**Files:**
- Modify: `config/quality/structural-suppressions.txt`
- Create: `docs/internal/architecture-baseline-2/r5-ownership-audit.md`
- Create: `docs/internal/checkpoints/architecture-baseline-2-r5.md`
- Modify: `docs/project/current-state.md`

**Interfaces:**
- Produces documented final ownership review before acceptance.

- [ ] **Step 1: Require empty suppression file**

```bash
test ! -s config/quality/structural-suppressions.txt
bash scripts/verify-structural-suppressions.sh
```

Expected: **PASS**.

- [ ] **Step 2: Generate and review structural report**

```bash
./scripts/structural-review-report.sh > /tmp/ab2-structural-report.txt
cat /tmp/ab2-structural-report.txt
```

Any target file >300 lines or class with unusually broad dependencies must be reviewed. If a file mixes responsibilities, split by named ownership (e.g. parser vs executor vs storage transaction), never `Part1/Part2`/generic helper buckets.

- [ ] **Step 3: Write exact ownership audit**

`r5-ownership-audit.md` must answer for each final module:
- owned models;
- public interfaces;
- allowed callers;
- persistence owner;
- plugin execution owner;
- transaction owner;
- UI-state owner;
- any file >300 lines and why it remains cohesive or how it was split.

No answer may be "shared by convenience".

- [ ] **Step 4: Run R5 gate**

```bash
./scripts/verify.sh
./scripts/check-module-dependencies.sh
./gradlew detekt lintDebug :app:assembleDebug --stacktrace
```

Expected: **PASS**.

Record actual evidence, then set:

```text
Architecture Baseline 2 R5: ACCEPTED
Current active boundary: R6 - Architecture Acceptance
```

- [ ] **Step 5: Commit**

```bash
git add config/quality/structural-suppressions.txt   docs/internal/architecture-baseline-2/r5-ownership-audit.md   docs/internal/checkpoints/architecture-baseline-2-r5.md   docs/project/current-state.md
git commit -m "architecture: complete baseline two cleanup"
```
