# Hikari V2 Step 1 — Foundation + Clean Boot Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Cut Hikari over to a production-shaped V2 shell whose process start performs no product-domain work, whose first frame is not gated by install-state I/O, and whose retained V1 knowledge/core candidates are explicitly quarantined or preserved behind enforceable architectural gates.

**Architecture:** Begin inside the existing Hikari Git repository with an isolated V2 branch/worktree; do not create a second repository or a new Android project root. Then build the V2 foundation in two ordered gates. First preserve V1 invariants, freeze the V2 performance constitution, and install machine-enforced source/structure/manifest guards. Then cut `:app` to a minimal shell, reduce the active Gradle graph, retire V1 runtime source, add the tiny launch-state store and static FirstRun/Home destinations, and establish fresh/returning cold-start benchmarks. No real product capability is admitted in Step 1.

**Tech Stack:** Kotlin 2.4.10, JDK 17, Android Gradle Plugin 9.3.0, Android minSdk 26 / targetSdk 37, Jetpack Compose, Preferences DataStore, Kotlin coroutines, Gradle build logic, AndroidX Macrobenchmark/Baseline Profile + ProfileInstaller, JUnit4, AndroidX instrumentation/Compose UI testing, Bash verification scripts.

**Spec:** `docs/superpowers/specs/2026-09-07-hikari-v2-foundation-clean-boot-design.md`

## Global Constraints

- Release Android identity remains `app.openstory`; JDK is exactly 17; Android minSdk is 26 and targetSdk is 37.
- Step 1 uses a branch-level V2 cutover. Do not create or retain a peer `:app-v2` module.
- Task 0 establishes V2 **inside the existing Hikari Git repository**. Do not run `git init`, `gradle init`, Android Studio **New Project**, create a second repository root, copy retained modules into another project, or bootstrap a peer Android project beside Hikari.
- Execute the plan from an isolated V2 branch/worktree rooted at the same Git common directory as V1. If the execution harness already provides an isolated worktree, reuse it rather than nesting another worktree.
- V1 runtime source must not be retired until the V1 salvage ledger is committed and green.
- Accepted transplant candidates are `:reader:engine`, `:plugins:api`, and reviewed narrow `:core:common` primitives.
- `:catalog:engine` and `:catalog:model` remain quarantine/reference candidates; no Step 1 code may label them accepted V2 runtime.
- The target active graph is `:app`, `:core:common`, `:catalog:model`, `:catalog:engine`, `:reader:engine`, `:plugins:api`, and `:benchmark`.
- `:app` has zero production project dependencies. Its only project edge is the existing Baseline Profile test/tooling edge to `:benchmark`.
- Step 1 production code does not use Hilt, Room, WorkManager, OkHttp, Coil, Navigation 3, JavaScriptEngine, plugin runtime, Reader runtime/integration, Catalog runtime/orchestration, Downloads, Chapters, Library, V1 Settings, storage adapters, or the old feature modules.
- `androidx.profileinstaller` is the one explicitly classified shell/tooling runtime dependency allowed to contribute an AndroidX Startup initializer in Step 1. The merged-manifest gate must accept only `androidx.profileinstaller.ProfileInstallerInitializer`; any other initializer/provider is red until separately reviewed. This keeps Baseline Profile install/measurement support while preventing silent domain initialization.
- `Application` and `MainActivity` do not construct, inject, start, schedule, recover, scan, refresh, reconcile, provision, or observe a product domain.
- The first application-owned frame must be renderable without awaiting install-state I/O. The tiny launch-state read may run concurrently with rendering.
- Spec consistency resolution: BOOT-02, §8.3, and SR-19 are authoritative for launch-state timing. The older diagrams in §3/§21 that visually place state resolution strictly after first frame are treated as stale summaries; implementation must allow concurrent read while never gating first frame.
- Persist exactly one launch fact: `initial_setup_completed`. Absence is `FirstRun`; `true` is `Ready`.
- Launch-state read failure resolves conservatively to the FirstRun-safe shell. Cancellation propagates. Failed completion persistence does not transition durably to Home.
- Step 1 development uses `app.openstory.v2dev`; benchmark/non-minified benchmark target builds use `app.openstory.v2benchmark`; production release remains `app.openstory`.
- Step 1 sets `android:allowBackup="false"` so V1/cloud restore cannot silently seed the new launch-state file. Same-application-ID V1→V2 upgrade is outside Step 1.
- `Unknown`, `FirstRun`, and `Ready` are state resolution, not Navigation destinations backed by Navigation 3. Initial state changes do not animate.
- No real Home, Discover, Search, Story, Reader, Library, Downloads, plugin provisioning, background scheduling, notification delivery, deep links, final onboarding, final design system, or final DI framework is built in this plan.
- V2 production source starts with no temporary/pending/generation-debt exemptions, no test-only production APIs, and no same-module package SCC in newly introduced `:app` code.
- Every task is test-first where behavior or policy is introduced, ends green, and commits independently.
- Do not modify quarantined Catalog algorithms or retained Reader engine behavior during Step 1 merely to simplify the cutover.

---

## File/Ownership Map

The implementation should converge on this ownership before Task 15 closes.

### Canonical V2 knowledge/policy

- `docs/internal/v2/cutover-provenance.md` — records the same-repository cutover strategy, V1 base commit, V2 cutover branch, and origin used to establish the V2 workspace.
- `docs/superpowers/specs/2026-09-07-hikari-v2-foundation-clean-boot-design.md` — approved design; no implementation notes mixed in.
- `docs/internal/v2/v1-salvage-ledger.md` — actionable KEEP/REDESIGN/DROP invariant map plus transplant/quarantine/reference ownership.
- `docs/internal/v2/capability-admission-contract.md` — operational PERF-01..08 rules and the mandatory ten-field admission record for Step 2+.
- `config/architecture/v2-foundation-policy.json` — machine-readable Step 1 source/build/structure/manifest policy.
- `config/architecture/module-boundaries.json` — active-module graph only.

### Build logic

- `build-logic/src/main/kotlin/app/openstory/build/architecture/FoundationPolicy.kt` — parsed policy model.
- `build-logic/src/main/kotlin/app/openstory/build/architecture/FoundationPolicyLoader.kt` — strict JSON loader.
- `build-logic/src/main/kotlin/app/openstory/build/architecture/BootSourceBoundaryVerifier.kt` — raw source/build-script forbidden-token verifier.
- `build-logic/src/main/kotlin/app/openstory/build/architecture/VerifyBootSourceBoundaryTask.kt` — Gradle task wrapper.
- `build-logic/src/main/kotlin/app/openstory/build/architecture/AppStructuralVerifier.kt` — app-only line-budget, broad-authority, test-only-API, and package-SCC ratchet.
- `build-logic/src/main/kotlin/app/openstory/build/architecture/VerifyAppStructureTask.kt` — Gradle task wrapper.
- `build-logic/src/main/kotlin/app/openstory/build/architecture/MergedManifestStartupVerifier.kt` — merged-manifest hidden-startup verifier.
- `build-logic/src/main/kotlin/app/openstory/build/architecture/VerifyMergedManifestStartupTask.kt` — AGP merged-manifest task wrapper.
- `build-logic/src/main/kotlin/app/openstory/build/FoundationConventionPlugin.kt` — registers the three V2 gates and aggregates them as `:app:verifyFoundation`.
- `build-logic/src/main/kotlin/app/openstory/build/ArchitectureConventionPlugin.kt` — makes root `verifyArchitecture` depend on the app V2 foundation gate after the app cutover.

### Minimal V2 app shell

- `app/src/main/kotlin/app/openstory/HikariApplication.kt` — no-op Android `Application`.
- `app/src/main/kotlin/app/openstory/MainActivity.kt` — window setup + `setContent()` only.
- `app/src/main/kotlin/app/openstory/ui/HikariBootTheme.kt` — minimal shell theme only.
- `app/src/main/kotlin/app/openstory/ui/HikariBootSurface.kt` — stable base surface and test-tag resource-id semantics.
- `app/src/main/kotlin/app/openstory/startup/AppLaunchState.kt` — `Unknown`, `FirstRun`, `Ready`.
- `app/src/main/kotlin/app/openstory/startup/AppLaunchStateStore.kt` — Preferences DataStore read/write semantics.
- `app/src/main/kotlin/app/openstory/startup/AppLaunchStateDataStore.kt` — one application-context DataStore delegate/factory.
- `app/src/main/kotlin/app/openstory/startup/ui/StartupGate.kt` — narrow state resolution and completion transition owner.
- `app/src/main/kotlin/app/openstory/startup/ui/UnknownScreen.kt` — neutral unresolved shell.
- `app/src/main/kotlin/app/openstory/startup/ui/FirstRunScreen.kt` — minimal setup-completion shell.
- `app/src/main/kotlin/app/openstory/startup/ui/HomeShell.kt` — static returning-user shell.
- `app/src/main/kotlin/app/openstory/startup/StartupTrace.kt` — trace labels/top-level trace helpers only.

Package direction must remain one-way:

```text
app.openstory.MainActivity
        |
        v
app.openstory.startup.ui
        |                \
        v                 v
app.openstory.startup   app.openstory.ui

app.openstory.startup --> no UI package
app.openstory.ui      --> no startup package
```

### Benchmark-only state preparation

- `app/src/benchmarkRelease/kotlin/app/openstory/benchmark/BenchmarkLaunchStateActivity.kt` — benchmark-only deterministic `initial_setup_completed=true` writer.
- `app/src/benchmarkRelease/AndroidManifest.xml` — exports only that benchmark fixture activity.
- `benchmark/src/main/kotlin/app/openstory/benchmark/HikariBenchmarkDriver.kt` — clear-data, fixture, launch, and destination-wait helpers.
- `benchmark/src/main/kotlin/app/openstory/benchmark/HikariMacrobenchmark.kt` — exactly fresh-install and returning cold-start measurements for Step 1.
- `benchmark/src/main/kotlin/app/openstory/benchmark/BaselineProfileGenerator.kt` — startup-only V2 profile journey.

### Permanent verification entrypoints

- `scripts/verify.sh` — full V2 static + Gradle verification.
- `scripts/verify-fast.sh` — fast V2 static + focused Gradle verification.
- `scripts/verification-common.sh` — only V2/generic static gates.
- `scripts/verify-source-layout.sh` — generic layout gate with no inherited V1 allowances.
- `scripts/verify-structural-suppressions.sh` — generic structural suppression gate.
- `scripts/structural-review-report.sh` — active-tree review report; no deleted-module assumptions.
- `scripts/tests/v2-cutover-context-test.sh`
- `scripts/tests/v2-salvage-ledger-test.sh`
- `scripts/tests/v2-capability-admission-contract-test.sh`
- `scripts/tests/v2-verification-entrypoints-test.sh`
- `scripts/tests/v2-source-layout-policy-test.sh`
- `scripts/tests/v2-retired-runtime-absence-test.sh`
- `scripts/tests/v2-build-surface-test.sh`

---

## Task 0: Establish the same-repository V2 cutover workspace

> **Required execution skill:** invoke `superpowers:using-git-worktrees` before changing source. The user has already chosen the same-repository V2 branch/worktree strategy, so do not replace this gate with a second repository or Android Studio project.

**Files:**
- Create: `docs/internal/v2/cutover-provenance.md`
- Create: `scripts/tests/v2-cutover-context-test.sh`
- Do not modify production source, Gradle module topology, manifests, or retained engine code in this task.

**Interfaces:**
- Consumes: the existing Hikari Git repository at the V1 cutover base.
- Produces: one isolated V2 execution workspace sharing the **same Git common directory/history** as V1, plus an auditable provenance record.
- Produces no Android project, no peer `:app-v2`, no nested repository, and no copied engine tree.
- Task 1 and every later task execute from this workspace.

- [ ] **Step 1: Verify the starting checkout is the existing Hikari repository and is clean**

Run from the current Hikari checkout:

```bash
set -euo pipefail

REPO_ROOT="$(git rev-parse --show-toplevel)"
cd "$REPO_ROOT"

test -f settings.gradle.kts
test -f build.gradle.kts
test -d app
test -d reader/engine
test -d catalog/engine
test -d plugins/api

git status --short
```

Expected:

- all repository-shape checks pass;
- `git status --short` prints nothing;
- this is the existing Hikari repository, not an empty/new Android project.

If the checkout is dirty, stop before creating the V2 workspace. Do not hide unrelated changes with `git stash`, `git reset`, or a cleanup commit.

- [ ] **Step 2: Record the V1 cutover base before creating/reusing the V2 workspace**

Run:

```bash
set -euo pipefail

HIKARI_V1_BASE_COMMIT="$(git rev-parse HEAD)"
HIKARI_GIT_COMMON_DIR="$(cd "$(git rev-parse --git-common-dir)" && pwd -P)"
HIKARI_ORIGIN_URL="$(git remote get-url origin 2>/dev/null || printf '%s' '(no origin remote)')"
HIKARI_CUTOVER_MARKER="$HIKARI_GIT_COMMON_DIR/hikari-v2-cutover-base.env"

printf 'HIKARI_V1_BASE_COMMIT=%q\nHIKARI_GIT_COMMON_DIR=%q\nHIKARI_ORIGIN_URL=%q\n' \
  "$HIKARI_V1_BASE_COMMIT" \
  "$HIKARI_GIT_COMMON_DIR" \
  "$HIKARI_ORIGIN_URL" \
  > "$HIKARI_CUTOVER_MARKER"
chmod 600 "$HIKARI_CUTOVER_MARKER"

printf 'V1 base: %s\nGit common dir: %s\nOrigin: %s\nMarker: %s\n' \
  "$HIKARI_V1_BASE_COMMIT" \
  "$HIKARI_GIT_COMMON_DIR" \
  "$HIKARI_ORIGIN_URL" \
  "$HIKARI_CUTOVER_MARKER"
```

Expected: all values are concrete. The temporary marker lives inside the Git common directory, is not a tracked repository file, and remains readable from any linked worktree even when the execution harness changes shell sessions.

- [ ] **Step 3: Enter or create the isolated V2 workspace using the worktree workflow**

Invoke `superpowers:using-git-worktrees` and follow its detection order:

1. detect whether the execution harness already placed you in a linked worktree;
2. if already isolated, reuse that workspace;
3. otherwise use the platform/native worktree mechanism when available;
4. only if no native mechanism exists, use `git worktree` fallback;
5. when manual fallback is required, use branch `v2/foundation-clean-boot` and the repository's ignored `.worktrees/` directory.

Manual fallback only, after verifying `.worktrees/` is ignored:

```bash
set -euo pipefail
COMMON_DIR="$(cd "$(git rev-parse --git-common-dir)" && pwd -P)"
# shellcheck disable=SC1090
source "$COMMON_DIR/hikari-v2-cutover-base.env"

git check-ignore -q .worktrees || {
  echo '.worktrees/ must be ignored before manual worktree creation.' >&2
  exit 1
}

if git show-ref --verify --quiet refs/heads/v2/foundation-clean-boot; then
  git worktree add .worktrees/v2-foundation-clean-boot v2/foundation-clean-boot
else
  git worktree add .worktrees/v2-foundation-clean-boot -b v2/foundation-clean-boot "$HIKARI_V1_BASE_COMMIT"
fi

cd .worktrees/v2-foundation-clean-boot
```

Forbidden in this step:

```text
git init
gradle init
Android Studio -> New Project
creating Hikari-V2/ as another repository root
creating :app-v2
copying :reader:engine / :catalog:engine / :plugins:api into a new project
```

- [ ] **Step 4: Prove the V2 workspace shares the same Git repository/history**

Run inside the V2 workspace:

```bash
set -euo pipefail

CURRENT_COMMON_DIR="$(cd "$(git rev-parse --git-common-dir)" && pwd -P)"
CUTOVER_MARKER="$CURRENT_COMMON_DIR/hikari-v2-cutover-base.env"
[[ -f "$CUTOVER_MARKER" ]] || {
  echo 'Original Hikari cutover marker is not reachable from this workspace.' >&2
  exit 1
}
# shellcheck disable=SC1090
source "$CUTOVER_MARKER"

CURRENT_HEAD="$(git rev-parse HEAD)"
CURRENT_BRANCH="$(git branch --show-current)"

[[ "$CURRENT_COMMON_DIR" == "$HIKARI_GIT_COMMON_DIR" ]] || {
  echo 'V2 workspace is not attached to the original Hikari Git repository.' >&2
  exit 1
}

[[ "$CURRENT_HEAD" == "$HIKARI_V1_BASE_COMMIT" ]] || {
  echo 'V2 workspace did not start from the recorded V1 cutover base.' >&2
  exit 1
}

[[ -n "$CURRENT_BRANCH" ]] || {
  echo 'V2 execution workspace must be on an explicit branch.' >&2
  exit 1
}

test -f settings.gradle.kts
test -d app
test -d reader/engine
test -d catalog/engine
test -d plugins/api
```

Expected: PASS. This is the same repository/history at the exact cutover base, now isolated for V2 work.

- [ ] **Step 5: Run the retained-core V1 baseline before any cutover edits**

Run:

```bash
./gradlew \
  :core:common:test \
  :catalog:model:test \
  :catalog:engine:test \
  :reader:engine:test \
  :plugins:api:test \
  --no-daemon

bash scripts/verify-current-architecture.sh
```

Expected: PASS and `git status --short` remains empty.

If this baseline is red, stop and report the pre-existing failure. Do not begin Task 1 with an ambiguous baseline.

- [ ] **Step 6: Write the permanent same-repository cutover guard first**

Create `scripts/tests/v2-cutover-context-test.sh`:

```bash
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
```

Run it now:

```bash
bash scripts/tests/v2-cutover-context-test.sh
```

Expected: FAIL with `Missing V2 cutover provenance`.

- [ ] **Step 7: Create the cutover provenance from actual Git values**

Run inside the V2 workspace before any source change:

```bash
set -euo pipefail
mkdir -p docs/internal/v2

CURRENT_COMMON_DIR="$(cd "$(git rev-parse --git-common-dir)" && pwd -P)"
# shellcheck disable=SC1090
source "$CURRENT_COMMON_DIR/hikari-v2-cutover-base.env"

CUTOVER_BRANCH="$(git branch --show-current)"
BASE_COMMIT="$HIKARI_V1_BASE_COMMIT"
ORIGIN_URL="$HIKARI_ORIGIN_URL"

cat > docs/internal/v2/cutover-provenance.md <<EOF
# Hikari V2 Cutover Provenance

- Strategy: same Hikari Git repository; isolated V2 branch/workspace
- V1 cutover base commit: \`$BASE_COMMIT\`
- V2 cutover branch: \`$CUTOVER_BRANCH\`
- Origin at cutover: \`$ORIGIN_URL\`
- Second repository/project root: forbidden
- Peer \`:app-v2\`: forbidden
- Android project strategy: rebuild the product shell in the existing repository; retain/quarantine approved modules in place rather than copying them into another project
EOF
```

Do not record absolute local/worktree paths in the committed file.

- [ ] **Step 8: Run the cutover guard and verify this task changed only provenance/policy files**

Run:

```bash
bash scripts/tests/v2-cutover-context-test.sh

git status --short
```

Expected:

```text
V2 same-repository cutover context verified.
```

and the only uncommitted paths are:

```text
docs/internal/v2/cutover-provenance.md
scripts/tests/v2-cutover-context-test.sh
```

No Gradle, Android, application, engine, or runtime source file changes are allowed in Task 0.

- [ ] **Step 9: Commit the workspace provenance gate**

```bash
git add docs/internal/v2/cutover-provenance.md \
  scripts/tests/v2-cutover-context-test.sh
git commit -m "chore(v2): establish same-repo cutover workspace"
```

- [ ] **Step 10: Stop and re-verify the Task 0 invariant**

```bash
git status --short
bash scripts/tests/v2-cutover-context-test.sh

COMMON_DIR="$(cd "$(git rev-parse --git-common-dir)" && pwd -P)"
rm -f "$COMMON_DIR/hikari-v2-cutover-base.env"
test ! -e "$COMMON_DIR/hikari-v2-cutover-base.env"
```

Expected: clean worktree, PASS, and the untracked Git-common-dir cutover marker is removed. Only now may Task 1 begin.

---

## Task 1: Preserve V1 invariants before any runtime deletion

**Files:**
- Create: `docs/internal/v2/v1-salvage-ledger.md`
- Create: `scripts/tests/v2-salvage-ledger-test.sh`
- Read only: `docs/internal/architecture-baseline-2/invariant-inventory.md`
- Read only: `docs/internal/performance-big-update-v3/Hikari-performance-architecture-audit-whole-app-big-update-2026-09-07.md`
- Read only: `docs/internal/performance-big-update-v3/Hikari-structural-simplification-deep-audit-2026-09-07.md`
- Read only: Reader/plugin security and continuity checkpoints referenced by the approved spec

**Interfaces:**
- Consumes: Task 0's same-repository V2 cutover workspace, the approved Step 1 spec, and V1 audit/invariant evidence.
- Produces: a ledger table with columns `ID | Contract / asset | Decision | Retention | Future owner | Evidence / reason`.
- Decision values are exactly `KEEP`, `REDESIGN`, or `DROP`.
- Retention values are exactly `TRANSPLANT`, `QUARANTINE`, `REFERENCE`, or `NONE`.

- [ ] **Step 1: Write the failing ledger policy test**

Create `scripts/tests/v2-salvage-ledger-test.sh`:

```bash
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
```

- [ ] **Step 2: Run the test and confirm it fails because the ledger does not exist**

Run:

```bash
bash scripts/tests/v2-salvage-ledger-test.sh
```

Expected: FAIL with `Missing V2 salvage ledger`.

- [ ] **Step 3: Create the ledger with the minimum canonical classifications**

The ledger must include, at minimum, explicit rows for:

```text
KEEP / TRANSPLANT
- :reader:engine
- :plugins:api
- reviewed pure :core:common primitives needed by retained contracts

REDESIGN / QUARANTINE
- :catalog:engine because A1/A2/A3 are confirmed and A4 is a scaling risk
- :catalog:model because X3 shows over-wide model/allocation amplification

KEEP / REFERENCE CONTRACT
- app.openstory release identity; JDK 17; minSdk 26; targetSdk 37
- local-first product resilience semantics
- plugin host owns network/files/platform trust boundary
- HTTPS host allowlist, redirect revalidation, bounded responses
- package bytes verified before activation
- failed install leaves prior usable state intact
- capability expansion review and rollback semantics
- per-source failure isolation
- deterministic matching/ranking as a semantic property
- Reader route/security/checksum invalidation invariants
- integrity verification must not be removed merely for fewer copies
- explicit download durability semantics
- source-specific metadata preservation

REDESIGN / REFERENCE
- Catalog reconciliation/indexing A1/A2/A3/A4
- current broad canonical read/projection paths A5-A8
- foreground execution ownership L1/L3-L8
- historical evidence lifetime D1
- X1-X19 families that belong to capabilities later admitted
- Room/schema ownership
- background/durable execution ownership
- plugin runtime control-plane/payload split
- Reader asset/cache integration X8-X12
- Chapter sync X13-X15

DROP / NONE
- V1 Home aggregate implementation
- V1 Android orchestration/composition root
- current app-wide startup observers/coordinators
- V1 feature ViewModels as migration targets
- V1 benchmark product journeys
- old runtime-specific structural suppressions/temporary source-layout allowances
```

Do not copy implementation details into the ledger. Preserve the invariant, the reason, and its future owner.

- [ ] **Step 4: Run the ledger test**

Run:

```bash
bash scripts/tests/v2-salvage-ledger-test.sh
```

Expected: PASS with `V2 salvage ledger verified.`

- [ ] **Step 5: Review the ledger against the source invariant inventory**

Run:

```bash
grep -nE '^\| (KEEP|CHANGE|DELETE) ' docs/internal/architecture-baseline-2/invariant-inventory.md
grep -nE 'A1|A2|A3|A4|X8|X9|X10|X11|X12|X13|X14|X15|X16|X17|X18|X19' \
  docs/internal/performance-big-update-v3/*.md
```

Expected: every security/correctness item that survives conceptually has an explicit ledger owner; Catalog engine/model remain quarantined.

- [ ] **Step 6: Commit**

```bash
git add docs/internal/v2/v1-salvage-ledger.md scripts/tests/v2-salvage-ledger-test.sh
git commit -m "docs(v2): preserve v1 invariant salvage ledger"
```

---

## Task 2: Freeze the V2 performance constitution and capability admission form

**Files:**
- Create: `docs/internal/v2/capability-admission-contract.md`
- Create: `scripts/tests/v2-capability-admission-contract-test.sh`

**Interfaces:**
- Consumes: PERF-01..08 and the ten-field Capability Admission Contract from the Step 1 spec.
- Produces: the exact operational review form every Step 2+ capability must fill before app integration.
- Does not produce a `CapabilityManager`, registry, service locator, or runtime framework.

- [ ] **Step 1: Write the failing policy test**

```bash
#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="${REPO_ROOT:-$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)}"
CONTRACT="$ROOT_DIR/docs/internal/v2/capability-admission-contract.md"

[[ -f "$CONTRACT" ]] || {
  echo "Missing capability admission contract." >&2
  exit 1
}

for id in PERF-01 PERF-02 PERF-03 PERF-04 PERF-05 PERF-06 PERF-07 PERF-08; do
  grep -Fq "$id" "$CONTRACT" || {
    echo "Missing performance rule: $id" >&2
    exit 1
  }
done

required_fields=(
  'Activation trigger and owner'
  'Deactivation / quiescence rule'
  'Production dependency graph'
  'Foreground working-set / cardinality'
  'Observer keys, invalidation scope, and lifetime'
  'CPU / I/O / network execution owner'
  'Durable / background work owner'
  'Retention / aging / eviction'
  'Failure / retry / terminal-state ownership'
  'Benchmark / scaling delta'
)

for field in "${required_fields[@]}"; do
  grep -Fq "$field" "$CONTRACT" || {
    echo "Missing admission field: $field" >&2
    exit 1
  }
done

if grep -Eiq '\b(CapabilityManager|CapabilityRegistry|ServiceLocator)\b' "$CONTRACT"; then
  echo "Admission contract must not introduce a runtime capability framework." >&2
  exit 1
fi

echo "V2 capability admission contract verified."
```

- [ ] **Step 2: Run the test and observe the missing-file failure**

```bash
bash scripts/tests/v2-capability-admission-contract-test.sh
```

Expected: FAIL with `Missing capability admission contract.`

- [ ] **Step 3: Create the operational contract**

The document must contain:

```markdown
# V2 Capability Admission Contract

## Normative performance rules
PERF-01 — Working-set scope
PERF-02 — Narrow read/allocation ownership
PERF-03 — Reactive scope matches semantic demand
PERF-04 — Batch work has batch semantics
PERF-05 — One execution owner per expensive work item
PERF-06 — Lifetime/aging is bounded
PERF-07 — Control plane does not imply payload execution
PERF-08 — Performance evidence is part of the interface

## Required capability record
1. Activation trigger and owner
2. Deactivation / quiescence rule
3. Production dependency graph
4. Foreground working-set / cardinality
5. Observer keys, invalidation scope, and lifetime
6. CPU / I/O / network execution owner
7. Durable / background work owner
8. Retention / aging / eviction
9. Failure / retry / terminal-state ownership
10. Benchmark / scaling delta
```

Under each PERF rule, copy the normative meaning from the approved spec without adding a runtime abstraction.

- [ ] **Step 4: Run the contract test**

```bash
bash scripts/tests/v2-capability-admission-contract-test.sh
```

Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add docs/internal/v2/capability-admission-contract.md \
  scripts/tests/v2-capability-admission-contract-test.sh
git commit -m "docs(v2): freeze capability admission contract"
```

---

## Task 3: Add a strict V2 source/build boundary policy and verifier

**Files:**
- Create: `config/architecture/v2-foundation-policy.json`
- Create: `build-logic/src/main/kotlin/app/openstory/build/architecture/FoundationPolicy.kt`
- Create: `build-logic/src/main/kotlin/app/openstory/build/architecture/FoundationPolicyLoader.kt`
- Create: `build-logic/src/main/kotlin/app/openstory/build/architecture/BootSourceBoundaryVerifier.kt`
- Create: `build-logic/src/main/kotlin/app/openstory/build/architecture/VerifyBootSourceBoundaryTask.kt`
- Create: `build-logic/src/test/kotlin/app/openstory/build/architecture/FoundationPolicyLoaderTest.kt`
- Create: `build-logic/src/test/kotlin/app/openstory/build/architecture/FoundationTestPolicy.kt`
- Create: `build-logic/src/test/kotlin/app/openstory/build/architecture/BootSourceBoundaryVerifierTest.kt`

**Interfaces:**
- Produces:

```kotlin
data class FoundationPolicy(
    val schemaVersion: Int,
    val maxProductionKotlinLines: Int,
    val forbiddenSourceTokens: Set<String>,
    val forbiddenBuildTokens: Set<String>,
    val forbiddenBroadTypeSuffixes: Set<String>,
    val forbiddenManifestPermissions: Set<String>,
    val allowedStartupInitializers: Set<String>,
)

data class FoundationViolation(
    val code: String,
    val detail: String,
) : Comparable<FoundationViolation>
```

- `BootSourceBoundaryVerifier.verify(...)` returns all violations rather than failing on the first.
- Later tasks consume the same policy for structure and manifest checks.
- Shared test-only policy helper used by Tasks 3–5:

```kotlin
internal fun foundationTestPolicy(
    maxProductionKotlinLines: Int = 300,
    forbiddenSourceTokens: Set<String> = emptySet(),
    forbiddenBuildTokens: Set<String> = emptySet(),
    forbiddenBroadTypeSuffixes: Set<String> = setOf(
        "Manager",
        "Coordinator",
        "Registry",
        "ServiceLocator",
    ),
    forbiddenManifestPermissions: Set<String> = setOf(
        "android.permission.INTERNET",
        "android.permission.ACCESS_NETWORK_STATE",
        "android.permission.POST_NOTIFICATIONS",
    ),
    allowedStartupInitializers: Set<String> = setOf(
        "androidx.profileinstaller.ProfileInstallerInitializer",
    ),
): FoundationPolicy = FoundationPolicy(
    schemaVersion = 1,
    maxProductionKotlinLines = maxProductionKotlinLines,
    forbiddenSourceTokens = forbiddenSourceTokens,
    forbiddenBuildTokens = forbiddenBuildTokens,
    forbiddenBroadTypeSuffixes = forbiddenBroadTypeSuffixes,
    forbiddenManifestPermissions = forbiddenManifestPermissions,
    allowedStartupInitializers = allowedStartupInitializers,
)
```

- [ ] **Step 1: Write policy-loader tests before the loader**

Test these cases:

```kotlin
@Test
fun loadsVersionOnePolicy() {
    val policy = FoundationPolicyLoader.parse(
        """
        {
          "schemaVersion": 1,
          "maxProductionKotlinLines": 300,
          "forbiddenSourceTokens": ["androidx.room."],
          "forbiddenBuildTokens": ["implementation(project("],
          "forbiddenBroadTypeSuffixes": ["Manager"],
          "forbiddenManifestPermissions": ["android.permission.INTERNET"],
          "allowedStartupInitializers": ["androidx.profileinstaller.ProfileInstallerInitializer"]
        }
        """.trimIndent(),
    )

    assertEquals(1, policy.schemaVersion)
    assertEquals(300, policy.maxProductionKotlinLines)
    assertEquals(setOf("androidx.room."), policy.forbiddenSourceTokens)
}

@Test
fun rejectsUnknownPolicyField() {
    assertFailsWith<IllegalArgumentException> {
        FoundationPolicyLoader.parse(
            """
            {
              "schemaVersion": 1,
              "maxProductionKotlinLines": 300,
              "forbiddenSourceTokens": [],
              "forbiddenBuildTokens": [],
              "forbiddenBroadTypeSuffixes": [],
              "forbiddenManifestPermissions": [],
              "allowedStartupInitializers": [],
              "surprise": true
            }
            """.trimIndent(),
        )
    }
}
```

- [ ] **Step 2: Run the loader tests and confirm compile failure**

```bash
./gradlew :build-logic:test --tests '*FoundationPolicyLoaderTest*' --no-daemon
```

Expected: FAIL because the V2 policy types do not exist.

- [ ] **Step 3: Implement the strict policy model/loader**

Use `kotlinx.serialization.json` already available to build logic. The loader must:

- accept schema version `1` only;
- reject unknown root fields;
- require positive `maxProductionKotlinLines`;
- reject blank/duplicate policy values, including initializer names;
- return deterministic linked sets;
- require every `allowedStartupInitializers` value to be a fully-qualified class name.

Use this concrete parser shape rather than permissive serializer defaults:

```kotlin
internal object FoundationPolicyLoader {
    private val json = Json { ignoreUnknownKeys = false }
    private val expectedKeys = setOf(
        "schemaVersion",
        "maxProductionKotlinLines",
        "forbiddenSourceTokens",
        "forbiddenBuildTokens",
        "forbiddenBroadTypeSuffixes",
        "forbiddenManifestPermissions",
        "allowedStartupInitializers",
    )

    fun parse(text: String): FoundationPolicy {
        val root = json.parseToJsonElement(text).jsonObject
        require(root.keys == expectedKeys) {
            "v2_foundation.policy_keys: expected=$expectedKeys actual=${root.keys}"
        }

        fun stringSet(name: String): Set<String> {
            val values = root.getValue(name).jsonArray.map { it.jsonPrimitive.content }
            require(values.all { it.isNotBlank() }) { "v2_foundation.blank_value:$name" }
            require(values.size == values.toSet().size) { "v2_foundation.duplicate_value:$name" }
            return values.toCollection(linkedSetOf())
        }

        val schemaVersion = root.getValue("schemaVersion").jsonPrimitive.int
        require(schemaVersion == 1) { "v2_foundation.schema:$schemaVersion" }

        val maxLines = root.getValue("maxProductionKotlinLines").jsonPrimitive.int
        require(maxLines > 0) { "v2_foundation.max_lines:$maxLines" }

        val initializers = stringSet("allowedStartupInitializers")
        require(initializers.all { value ->
            value.contains('.') && value.split('.').all { part ->
                part.isNotBlank() && (part.first().isLetter() || part.first() == '_')
            }
        }) { "v2_foundation.initializer_name" }

        return FoundationPolicy(
            schemaVersion = schemaVersion,
            maxProductionKotlinLines = maxLines,
            forbiddenSourceTokens = stringSet("forbiddenSourceTokens"),
            forbiddenBuildTokens = stringSet("forbiddenBuildTokens"),
            forbiddenBroadTypeSuffixes = stringSet("forbiddenBroadTypeSuffixes"),
            forbiddenManifestPermissions = stringSet("forbiddenManifestPermissions"),
            allowedStartupInitializers = initializers,
        )
    }
}
```

- [ ] **Step 4: Add source-boundary verifier tests**

```kotlin
@Test
fun reportsForbiddenSourceAndBuildTokens() {
    val policy = foundationTestPolicy(
        forbiddenSourceTokens = setOf("androidx.work.", "app.openstory.reader."),
        forbiddenBuildTokens = setOf("implementation(project("),
    )

    val violations = BootSourceBoundaryVerifier.verify(
        sources = mapOf(
            "MainActivity.kt" to
                "package app.openstory\nimport androidx.work.WorkManager",
            "StartupGate.kt" to
                "package app.openstory.startup\nval x = app.openstory.reader.ReaderRuntime",
        ),
        buildScript = """implementation(project(":reader"))""",
        policy = policy,
    )

    assertEquals(
        setOf(
            "v2_boot.forbidden_build_reference",
            "v2_boot.forbidden_source_reference",
        ),
        violations.map { it.code }.toSet(),
    )
}

@Test
fun allowsComposeAndDataStoreShellReferences() {
    val policy = foundationTestPolicy()
    val violations = BootSourceBoundaryVerifier.verify(
        sources = mapOf(
            "StartupGate.kt" to """
                package app.openstory.startup.ui
                import androidx.compose.runtime.Composable
                import androidx.datastore.preferences.core.Preferences
            """.trimIndent(),
        ),
        buildScript = "implementation(libs.androidx.datastore.preferences)",
        policy = policy,
    )

    assertTrue(violations.isEmpty())
}
```

- [ ] **Step 5: Implement `BootSourceBoundaryVerifier`**

Required behavior:

```kotlin
object BootSourceBoundaryVerifier {
    fun verify(
        sources: Map<String, String>,
        buildScript: String,
        policy: FoundationPolicy,
    ): List<FoundationViolation> = buildList {
        sources.toSortedMap().forEach { (path, text) ->
            policy.forbiddenSourceTokens
                .filter(text::contains)
                .forEach { token ->
                    add(
                        FoundationViolation(
                            code = "v2_boot.forbidden_source_reference",
                            detail = "$path:$token",
                        ),
                    )
                }
        }

        policy.forbiddenBuildTokens
            .filter(buildScript::contains)
            .forEach { token ->
                add(
                    FoundationViolation(
                        code = "v2_boot.forbidden_build_reference",
                        detail = token,
                    ),
                )
            }
    }.distinct().sorted()
}
```

- [ ] **Step 6: Add the Gradle task wrapper**

`VerifyBootSourceBoundaryTask` takes:

```kotlin
@get:InputFile
@get:PathSensitive(PathSensitivity.RELATIVE)
abstract val policyFile: RegularFileProperty

@get:InputFile
@get:PathSensitive(PathSensitivity.RELATIVE)
abstract val appBuildScript: RegularFileProperty

@get:InputFiles
@get:PathSensitive(PathSensitivity.RELATIVE)
abstract val productionSources: ConfigurableFileCollection
```

The task loads all `.kt`/`.java` source text by relative path, runs the verifier, and throws one `GradleException` containing every violation.

- [ ] **Step 7: Create the Step 1 policy file**

`config/architecture/v2-foundation-policy.json` must contain:

```json
{
  "schemaVersion": 1,
  "maxProductionKotlinLines": 300,
  "forbiddenSourceTokens": [
    "androidx.room.",
    "androidx.work.",
    "androidx.navigation",
    "androidx.lifecycle.viewmodel",
    "androidx.startup.",
    "okhttp3.",
    "coil.",
    "dagger.hilt.",
    "javax.inject.",
    "app.openstory.catalog.",
    "app.openstory.library.",
    "app.openstory.chapters.",
    "app.openstory.reader.",
    "app.openstory.downloads.",
    "app.openstory.settings.",
    "app.openstory.storage.",
    "app.openstory.plugins."
  ],
  "forbiddenBuildTokens": [
    "id(\"openstory.hilt\")",
    "id(\"openstory.room\")",
    "implementation(project(",
    "libs.androidx.room",
    "libs.androidx.work",
    "libs.androidx.navigation3",
    "libs.androidx.javascriptengine",
    "libs.okhttp",
    "libs.coil",
    "libs.hilt",
    "libs.androidx.hilt",
    "libs.javax.inject",
    "libs.backdrop",
    "libs.roborazzi"
  ],
  "forbiddenBroadTypeSuffixes": [
    "Manager",
    "Coordinator",
    "Registry",
    "ServiceLocator"
  ],
  "forbiddenManifestPermissions": [
    "android.permission.INTERNET",
    "android.permission.ACCESS_NETWORK_STATE",
    "android.permission.POST_NOTIFICATIONS"
  ],
  "allowedStartupInitializers": [
    "androidx.profileinstaller.ProfileInstallerInitializer"
  ]
}
```

- [ ] **Step 8: Run focused build-logic tests**

```bash
./gradlew :build-logic:test \
  --tests '*FoundationPolicyLoaderTest*' \
  --tests '*BootSourceBoundaryVerifierTest*' \
  --no-daemon
```

Expected: PASS.

- [ ] **Step 9: Commit**

```bash
git add config/architecture/v2-foundation-policy.json build-logic
git commit -m "build(v2): add boot source boundary policy"
```

---

## Task 4: Add the V2 structural ratchet for newly introduced app code

**Files:**
- Create: `build-logic/src/main/kotlin/app/openstory/build/architecture/AppStructuralVerifier.kt`
- Create: `build-logic/src/main/kotlin/app/openstory/build/architecture/VerifyAppStructureTask.kt`
- Create: `build-logic/src/test/kotlin/app/openstory/build/architecture/AppStructuralVerifierTest.kt`

**Interfaces:**
- Consumes: app production source map + `FoundationPolicy`.
- Produces violation codes:
  - `v2_structure.line_budget_exceeded`
  - `v2_structure.broad_authority`
  - `v2_structure.test_only_production_api`
  - `v2_structure.package_cycle`

- [ ] **Step 1: Write the failing structural tests**

Include all four behaviors:

```kotlin
@Test
fun rejectsBroadAuthorityAndTestOnlyProductionApi() {
    val violations = AppStructuralVerifier.verify(
        sources = mapOf(
            "app/openstory/startup/StartupManager.kt" to """
                package app.openstory.startup
                class StartupManager
                fun createForTest() = StartupManager()
            """.trimIndent(),
        ),
        policy = foundationTestPolicy(),
    )

    assertTrue(violations.any { it.code == "v2_structure.broad_authority" })
    assertTrue(violations.any { it.code == "v2_structure.test_only_production_api" })
}

@Test
fun rejectsPackageCycle() {
    val violations = AppStructuralVerifier.verify(
        sources = mapOf(
            "a/A.kt" to """
                package app.openstory.a
                import app.openstory.b.B
                class A
            """.trimIndent(),
            "b/B.kt" to """
                package app.openstory.b
                import app.openstory.a.A
                class B
            """.trimIndent(),
        ),
        policy = foundationTestPolicy(),
    )

    assertTrue(violations.any { it.code == "v2_structure.package_cycle" })
}

@Test
fun acceptsOneWayStartupPackageGraph() {
    val violations = AppStructuralVerifier.verify(
        sources = mapOf(
            "ui/HikariBootSurface.kt" to
                "package app.openstory.ui\nclass HikariBootSurface",
            "startup/AppLaunchState.kt" to
                "package app.openstory.startup\nsealed interface AppLaunchState",
            "startup/ui/StartupGate.kt" to """
                package app.openstory.startup.ui
                import app.openstory.startup.AppLaunchState
                import app.openstory.ui.HikariBootSurface
                class StartupGate
            """.trimIndent(),
        ),
        policy = foundationTestPolicy(),
    )

    assertTrue(violations.isEmpty())
}
```

Also test `maxProductionKotlinLines + 1` produces a line-budget violation.

- [ ] **Step 2: Run and observe compile failure**

```bash
./gradlew :build-logic:test --tests '*AppStructuralVerifierTest*' --no-daemon
```

Expected: FAIL because the verifier does not exist.

- [ ] **Step 3: Implement structural parsing**

Implementation requirements:

1. Count source lines; fail above the policy budget for new `:app` production code.
2. Find class/object/interface names and reject names ending in one of the configured broad suffixes.
3. Reject raw production symbols containing `forTest(` or `createForTest(`.
4. Parse declared packages.
5. Parse project imports.
6. Resolve each import to the longest declared package prefix in the same source set.
7. Build a package dependency graph.
8. Run Tarjan/Kosaraju SCC detection.
9. Report only SCCs with more than one package; self-edges are not package SCCs.
10. Sort violations deterministically.

Do not extend this task to retained/quarantined engines; Step 1's zero-debt ratchet applies to newly introduced app-shell code.

- [ ] **Step 4: Add the task wrapper**

`VerifyAppStructureTask` uses the same policy file and app production source collection as Task 3 and fails with a complete violation report.

- [ ] **Step 5: Run focused tests**

```bash
./gradlew :build-logic:test --tests '*AppStructuralVerifierTest*' --no-daemon
```

Expected: PASS.

- [ ] **Step 6: Commit**

```bash
git add build-logic/src/main/kotlin/app/openstory/build/architecture/AppStructuralVerifier.kt \
  build-logic/src/main/kotlin/app/openstory/build/architecture/VerifyAppStructureTask.kt \
  build-logic/src/test/kotlin/app/openstory/build/architecture/AppStructuralVerifierTest.kt
git commit -m "build(v2): add structural debt ratchet"
```

---

## Task 5: Add merged-manifest hidden-startup verification and aggregate plugin

**Files:**
- Create: `build-logic/src/main/kotlin/app/openstory/build/architecture/MergedManifestStartupVerifier.kt`
- Create: `build-logic/src/main/kotlin/app/openstory/build/architecture/VerifyMergedManifestStartupTask.kt`
- Create: `build-logic/src/main/kotlin/app/openstory/build/FoundationConventionPlugin.kt`
- Create: `build-logic/src/test/kotlin/app/openstory/build/architecture/MergedManifestStartupVerifierTest.kt`
- Modify: `build-logic/build.gradle.kts`

**Interfaces:**
- `MergedManifestStartupVerifier.verify(xml, policy)` rejects:
  - every merged `<service>`;
  - every provider except `androidx.startup.InitializationProvider` when **all** of its initializer `<meta-data android:name=...>` entries are present in `allowedStartupInitializers`;
  - an AndroidX Startup provider with zero classified initializer entries;
  - any unclassified initializer such as `WorkManagerInitializer`, `ProcessLifecycleInitializer`, or a future library initializer;
  - each permission listed by `forbiddenManifestPermissions`.
- `openstory.foundation` registers:
  - `verifyBootSourceBoundary`
  - `verifyAppStructure`
  - per-variant merged-manifest verification
  - aggregate `verifyFoundation`

No app module applies this plugin until Task 6.

- [ ] **Step 1: Write merged-manifest tests**

```kotlin
@Test
fun rejectsUnclassifiedStartupProviderAndBackgroundService() {
    val xml = """
        <manifest xmlns:android="http://schemas.android.com/apk/res/android">
          <application>
            <provider
              android:name="androidx.startup.InitializationProvider"
              android:authorities="app.openstory.androidx-startup">
              <meta-data
                android:name="androidx.work.WorkManagerInitializer"
                android:value="androidx.startup" />
            </provider>
            <service android:name="example.BackgroundService" />
          </application>
        </manifest>
    """.trimIndent()

    val violations = MergedManifestStartupVerifier.verify(xml, foundationTestPolicy())

    assertTrue(violations.any { it.code == "v2_manifest.initializer_unclassified" })
    assertTrue(violations.any { it.code == "v2_manifest.service_forbidden" })
}

@Test
fun acceptsOnlyClassifiedProfileInstallerInitializer() {
    val xml = """
        <manifest xmlns:android="http://schemas.android.com/apk/res/android">
          <application>
            <provider
              android:name="androidx.startup.InitializationProvider"
              android:authorities="app.openstory.androidx-startup">
              <meta-data
                android:name="androidx.profileinstaller.ProfileInstallerInitializer"
                android:value="androidx.startup" />
            </provider>
          </application>
        </manifest>
    """.trimIndent()

    assertTrue(MergedManifestStartupVerifier.verify(xml, foundationTestPolicy()).isEmpty())
}

@Test
fun rejectsStepOneNetworkAndNotificationPermissions() {
    val xml = """
        <manifest xmlns:android="http://schemas.android.com/apk/res/android">
          <uses-permission android:name="android.permission.INTERNET" />
          <application />
        </manifest>
    """.trimIndent()

    assertTrue(
        MergedManifestStartupVerifier.verify(xml, foundationTestPolicy())
            .any { it.code == "v2_manifest.permission_forbidden" },
    )
}

@Test
fun acceptsSingleActivityShellManifest() {
    val xml = """
        <manifest xmlns:android="http://schemas.android.com/apk/res/android">
          <application android:name="app.openstory.HikariApplication">
            <activity android:name="app.openstory.MainActivity" />
          </application>
        </manifest>
    """.trimIndent()

    assertTrue(MergedManifestStartupVerifier.verify(xml, foundationTestPolicy()).isEmpty())
}
```

- [ ] **Step 2: Run and confirm failure**

```bash
./gradlew :build-logic:test --tests '*MergedManifestStartupVerifierTest*' --no-daemon
```

Expected: FAIL because the verifier is not implemented.

- [ ] **Step 3: Implement the XML verifier**

Use a namespace-aware JDK XML parser. Read the Android namespace URI:

```kotlin
private const val ANDROID_NS = "http://schemas.android.com/apk/res/android"
```

Collect provider/service names, AndroidX Startup initializer metadata, and `uses-permission` names. The only Step 1 classified initializer is `androidx.profileinstaller.ProfileInstallerInitializer`; this exception is tooling/shell infrastructure, not a product capability. Any future initializer/provider must update the policy and verifier in the owning capability admission rather than inheriting it invisibly. Do **not** broaden the exception to the whole `InitializationProvider`.

- [ ] **Step 4: Add `VerifyMergedManifestStartupTask`**

The task has:

```kotlin
@get:InputFile
@get:PathSensitive(PathSensitivity.RELATIVE)
abstract val policyFile: RegularFileProperty

@get:InputFile
@get:PathSensitive(PathSensitivity.RELATIVE)
abstract val mergedManifest: RegularFileProperty
```

It parses the generated merged manifest and fails with all violations.

- [ ] **Step 5: Register the V2 convention plugin**

Add to `build-logic/build.gradle.kts`:

```kotlin
register("v2Foundation") {
    id = "openstory.foundation"
    implementationClass = "app.openstory.build.FoundationConventionPlugin"
}
```

`FoundationConventionPlugin` must:

1. wait for `com.android.application`;
2. register source and structural tasks over `app/src/main`;
3. use `ApplicationAndroidComponentsExtension` and `SingleArtifact.MERGED_MANIFEST`;
4. register manifest checks for every application variant;
5. register `verifyFoundation` depending on all three categories.

It must not apply Hilt, Room, WorkManager, or any Android runtime library.

- [ ] **Step 6: Run all new build-logic tests**

```bash
./gradlew :build-logic:test \
  --tests '*FoundationPolicyLoaderTest*' \
  --tests '*BootSourceBoundaryVerifierTest*' \
  --tests '*AppStructuralVerifierTest*' \
  --tests '*MergedManifestStartupVerifierTest*' \
  --no-daemon
```

Expected: PASS.

- [ ] **Step 7: Commit**

```bash
git add build-logic
git commit -m "build(v2): verify hidden startup and aggregate foundation gates"
```

---

## Task 6: Cut `:app` to the minimal V2 shell and isolate development identity

**Files:**
- Replace: `app/build.gradle.kts`
- Replace: `app/src/main/AndroidManifest.xml`
- Delete: all existing `app/src/main/kotlin/app/openstory/**`
- Delete: existing V1 `app/src/test/**`
- Delete: existing V1 `app/src/androidTest/**`
- Delete: existing V1 `app/src/benchmarkRelease/**`
- Delete: `app/src/release/generated/baselineProfiles/baseline-prof.txt`
- Delete: `app/src/release/generated/baselineProfiles/startup-prof.txt`
- Create: `app/src/main/kotlin/app/openstory/HikariApplication.kt`
- Create: `app/src/main/kotlin/app/openstory/MainActivity.kt`
- Create: `app/src/main/kotlin/app/openstory/ui/HikariBootTheme.kt`
- Create: `app/src/main/kotlin/app/openstory/ui/HikariBootSurface.kt`
- Create: `app/src/test/kotlin/app/openstory/AppShellContractTest.kt`
- Create: `app/src/androidTest/kotlin/app/openstory/AppLaunchSmokeTest.kt`
- Replace: `app/src/main/res/values/themes.xml`
- Create: `app/src/main/res/values-night/themes.xml`

**Interfaces:**
- `HikariApplication : Application` has no domain fields and no overridden startup work.
- `MainActivity` owns only edge-to-edge/window setup and `setContent`.
- `HikariBootSurface` provides the stable background and test-tag resource-id semantics.
- At this task boundary the only rendered destination is a static `startup-shell`; launch-state logic arrives later.
- Root `verifyArchitecture` is intentionally not run in this task because the V1 module-boundary policy still expects the old app edges. Task 7 updates the graph/policy and then integrates `:app:verifyFoundation` into root architecture verification.

- [ ] **Step 1: Apply the foundation plugin first and prove V1 `:app` fails**

Add `id("openstory.foundation")` to the app plugin block, then run:

```bash
./gradlew :app:verifyFoundation --no-daemon
```

Expected: FAIL with multiple boot violations from the existing V1 app. This is the regression gate proving the verifier is live.

- [ ] **Step 2: Replace the app build script with the minimal Step 1 dependency surface**

Keep only:

```kotlin
plugins {
    alias(libs.plugins.androidx.baselineprofile)
    id("openstory.android.application")
    id("openstory.compose")
    id("openstory.foundation")
}

android {
    namespace = "app.openstory"

    defaultConfig {
        applicationId = "app.openstory"
        versionCode = 1
        versionName = "2.0-step1"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        testInstrumentationRunnerArguments["clearPackageData"] = "true"
    }

    buildTypes {
        getByName("debug") {
            applicationIdSuffix = ".v2dev"
        }
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            optimization {
                enable = true
            }
        }
        create("benchmarkRelease") {
            signingConfig = signingConfigs.getByName("debug")
            applicationIdSuffix = ".v2benchmark"
        }
        create("nonMinifiedRelease") {
            signingConfig = signingConfigs.getByName("debug")
            applicationIdSuffix = ".v2benchmark"
        }
    }

    testOptions {
        unitTests.isIncludeAndroidResources = true
        execution = "ANDROIDX_TEST_ORCHESTRATOR"
    }
}

baselineProfile {
    automaticGenerationDuringBuild = false
    dexLayoutOptimization = true
}

dependencies {
    "baselineProfile"(project(":benchmark"))

    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.profileinstaller)

    testImplementation(libs.junit)

    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestUtil(libs.androidx.test.orchestrator)

    debugImplementation(libs.androidx.compose.ui.test.manifest)
}
```

Retain `androidx.profileinstaller` deliberately. `CompilationMode.Partial(BaselineProfileMode.Require)` and API 26–27 Baseline Profile installation need the library; the V2 merged-manifest policy classifies exactly its `ProfileInstallerInitializer` and rejects any additional AndroidX Startup initializer. The initializer is measured as part of the production-shaped baseline rather than hidden by removing the tooling dependency.

- [ ] **Step 3: Replace the manifest**

Use only:

```xml
<?xml version="1.0" encoding="utf-8"?>
<manifest xmlns:android="http://schemas.android.com/apk/res/android">
    <application
        android:name="app.openstory.HikariApplication"
        android:allowBackup="false"
        android:icon="@mipmap/ic_launcher"
        android:label="@string/app_name"
        android:roundIcon="@mipmap/ic_launcher_round"
        android:supportsRtl="true"
        android:theme="@style/Theme.Hikari">
        <activity
            android:name="app.openstory.MainActivity"
            android:exported="true"
            android:theme="@style/Theme.Hikari"
            android:windowSoftInputMode="adjustResize">
            <intent-filter>
                <action android:name="android.intent.action.MAIN" />
                <category android:name="android.intent.category.LAUNCHER" />
            </intent-filter>
        </activity>
    </application>
</manifest>
```

No network, notification, provider, service, login, deep-link, backup-rule, or recovery manifest entry survives.

- [ ] **Step 4: Delete the V1 app implementation/tests/fixtures and stale generated profiles**

Use `git rm` for the V1 app source trees, then add back only the files listed in this task. Do not leave old app packages dormant.

- [ ] **Step 5: Add the no-op `Application`**

```kotlin
package app.openstory

import android.app.Application

class HikariApplication : Application()
```

No override is needed before trace instrumentation arrives in Task 13.

- [ ] **Step 6: Make the platform theme and Compose base surface share the same light/dark boot background**

`app/src/main/res/values/themes.xml`:

```xml
<?xml version="1.0" encoding="utf-8"?>
<resources>
    <style name="Theme.Hikari" parent="android:Theme.Material.Light.NoActionBar">
        <item name="android:windowBackground">@android:color/white</item>
        <item name="android:colorBackground">@android:color/white</item>
        <item name="android:windowLightStatusBar">true</item>
    </style>
</resources>
```

`app/src/main/res/values-night/themes.xml`:

```xml
<?xml version="1.0" encoding="utf-8"?>
<resources>
    <style name="Theme.Hikari" parent="android:Theme.Material.NoActionBar">
        <item name="android:windowBackground">@android:color/black</item>
        <item name="android:colorBackground">@android:color/black</item>
        <item name="android:windowLightStatusBar">false</item>
    </style>
</resources>
```

`HikariBootTheme.kt`:

```kotlin
package app.openstory.ui

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

@Composable
internal fun HikariBootTheme(content: @Composable () -> Unit) {
    val colors = if (isSystemInDarkTheme()) {
        darkColorScheme(
            background = Color.Black,
            surface = Color.Black,
        )
    } else {
        lightColorScheme(
            background = Color.White,
            surface = Color.White,
        )
    }
    MaterialTheme(
        colorScheme = colors,
        content = content,
    )
}
```

This is deliberately not the final design system; it only prevents the shell from switching between unrelated platform/Compose backgrounds.

- [ ] **Step 7: Add the stable boot surface**

`HikariBootSurface.kt`:

```kotlin
package app.openstory.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.testTagsAsResourceId

@Composable
internal fun HikariBootSurface(
    content: @Composable () -> Unit,
) {
    Surface(
        modifier = Modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background,
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .semantics { testTagsAsResourceId = true },
        ) {
            content()
        }
    }
}
```

- [ ] **Step 8: Add minimal `MainActivity`**

```kotlin
package app.openstory

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.material3.Text
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import app.openstory.ui.HikariBootSurface
import app.openstory.ui.HikariBootTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            HikariBootTheme {
                HikariBootSurface {
                    Text(
                        text = "Hikari",
                        modifier = Modifier.testTag("startup-shell"),
                    )
                }
            }
        }
    }
}
```

- [ ] **Step 9: Write a plain unit architecture contract**

`AppShellContractTest.kt` must assert:

```kotlin
@Test
fun applicationHasNoStartupOverride() {
    val source = rootFile("app/src/main/kotlin/app/openstory/HikariApplication.kt").readText()
    assertFalse("override fun onCreate" in source)
}

@Test
fun mainActivityHasNoDomainOrSchedulerOwnership() {
    val source = rootFile("app/src/main/kotlin/app/openstory/MainActivity.kt").readText()
    listOf(
        "@AndroidEntryPoint",
        "WorkManager",
        "NotificationIntentParser",
        "Reader",
        "Catalog",
        "PluginRuntime",
        "lifecycleScope",
    ).forEach { forbidden ->
        assertFalse(forbidden in source, "Forbidden MainActivity ownership: $forbidden")
    }
}
```

Use a local `rootFile(relativePath)` helper in the test, not a production helper:

```kotlin
private fun rootFile(relativePath: String): File {
    var current = File(System.getProperty("user.dir")).canonicalFile
    while (!File(current, "settings.gradle.kts").isFile) {
        current = current.parentFile
            ?: error("Repository root not found from ${System.getProperty("user.dir")}")
    }
    return File(current, relativePath)
}
```

Import `java.io.File` in the test file.

- [ ] **Step 10: Add a minimal launch smoke test**

`AppLaunchSmokeTest.kt` uses `createAndroidComposeRule<MainActivity>()` and:

```kotlin
@Test
fun appRendersStableShell() {
    composeRule.onNodeWithTag("startup-shell").assertIsDisplayed()
}
```

- [ ] **Step 11: Run focused app gates**

```bash
./gradlew \
  :app:verifyFoundation \
  :app:testDebugUnitTest \
  :app:assembleDebug \
  --no-daemon
```

Expected: PASS. Inspect the merged-manifest task output and confirm no provider/service is present. Do not run root `verifyArchitecture` until Task 7 updates the exact module policy.

- [ ] **Step 12: Commit**

```bash
git add app
git commit -m "feat(v2): cut app to clean boot shell"
```

---

## Task 7: Cut the active Gradle graph to the retained/quarantined Step 1 set

**Files:**
- Modify: `settings.gradle.kts`
- Replace: `config/architecture/module-boundaries.json`
- Modify: `build-logic/src/main/kotlin/app/openstory/build/ArchitectureConventionPlugin.kt`
- Replace: `build-logic/src/test/kotlin/app/openstory/build/ModuleGraphTest.kt`
- Modify or delete V1-only assertions in: `build-logic/src/test/kotlin/app/openstory/build/RepositoryHygieneTest.kt`
- Delete: `build-logic/src/test/kotlin/app/openstory/build/ContentStateContractArchitectureTest.kt`

**Interfaces:**
- Active modules become exactly:
  - `:app`
  - `:core:common`
  - `:catalog:model`
  - `:catalog:engine`
  - `:reader:engine`
  - `:plugins:api`
  - `:benchmark`
- Exact project edges:
  - `:app` production `[]`, test/tooling `[:benchmark]`
  - `:core:common` `[]`
  - `:catalog:model` `[:core:common]`
  - `:catalog:engine` `[:core:common, :catalog:model]`
  - `:reader:engine` `[:core:common]`
  - `:plugins:api` `[]`
  - `:benchmark` test `[:app]`

- [ ] **Step 1: Rewrite `ModuleGraphTest` to the target graph before changing settings**

The test must load `module-boundaries.json` and assert:

```kotlin
assertEquals(
    setOf(
        ":app",
        ":core:common",
        ":catalog:model",
        ":catalog:engine",
        ":reader:engine",
        ":plugins:api",
        ":benchmark",
    ),
    policy.modules.keys,
)
assertEquals(emptySet(), policy.modules.getValue(":app").productionDependencies)
assertEquals(setOf(":benchmark"), policy.modules.getValue(":app").testDependencies)
assertEquals(
    setOf(":core:common", ":catalog:model"),
    policy.modules.getValue(":catalog:engine").productionDependencies,
)
```

- [ ] **Step 2: Run it and confirm the V1 policy fails**

```bash
./gradlew :build-logic:test --tests '*ModuleGraphTest*' --no-daemon
```

Expected: FAIL because the policy still contains the V1 module set.

- [ ] **Step 3: Reduce `settings.gradle.kts`**

Keep only:

```kotlin
include(":app")
include(":core:common")
include(":catalog:model")
include(":catalog:engine")
include(":reader:engine")
include(":plugins:api")
include(":benchmark")
```

Do not include root `:catalog`, root `:reader`, or any runtime/integration module.

- [ ] **Step 4: Replace the module-boundary policy**

Use `dependencyMode = "exact"` for all six production modules. Keep `:benchmark` as `android-test` with its test edge to `:app`.

For `:app`, include the same heavy-domain forbidden imports as `v2-foundation-policy.json` so module-policy verification provides a second independent layer.

- [ ] **Step 5: Integrate the app foundation gate into root architecture verification**

Now that the target module policy is being installed, update `ArchitectureConventionPlugin` so root `verifyArchitecture` depends on `:app:verifyFoundation`.

Do not add this dependency earlier than Task 7; the pre-cutover exact module policy would correctly report the Task 6 app edge removals as stale allowances.

- [ ] **Step 6: Remove build-logic tests that require deleted V1 feature/runtime modules**

Delete `ContentStateContractArchitectureTest.kt`.

Rewrite `RepositoryHygieneTest.kt` so it checks only durable repository identity/toolchain facts that are still valid:

```text
rootProject.name == Hikari
release applicationId == app.openstory
debug suffix == .v2dev
benchmark suffix == .v2benchmark
no tracked .idea
```

Do not assert V1 Hilt/Navigation/Coil/Backdrop dependencies remain.

- [ ] **Step 7: Run architecture and retained-module tests**

```bash
./gradlew \
  verifyArchitecture \
  :build-logic:test \
  :core:common:test \
  :catalog:model:test \
  :catalog:engine:test \
  :reader:engine:test \
  :plugins:api:test \
  :app:testDebugUnitTest \
  --no-daemon
```

Expected: PASS.

- [ ] **Step 8: Verify Catalog is still quarantine, not app-reachable**

```bash
grep -F '| `:catalog:engine` | REDESIGN | QUARANTINE |' \
  docs/internal/v2/v1-salvage-ledger.md
./gradlew verifyArchitecture --no-daemon
```

Expected: both PASS.

- [ ] **Step 9: Commit the active-graph cutover**

```bash
git add settings.gradle.kts \
  config/architecture/module-boundaries.json \
  build-logic/src/main/kotlin/app/openstory/build/ArchitectureConventionPlugin.kt \
  build-logic/src/test
git commit -m "refactor(v2): cut active graph to foundation modules"
```

---

## Task 8A: Retire obsolete V1 runtime source without touching retained/quarantined cores

**Files/Directories to delete:**
- `core/designsystem/`
- root `catalog/build.gradle.kts` and root `catalog/src/` only — keep `catalog/model/` and `catalog/engine/`
- `library/`
- `chapters/`
- root `reader/build.gradle.kts` and root `reader/src/` only — keep `reader/engine/`
- `downloads/`
- `settings/`
- `storage/`
- `plugins/runtime/`
- `feature/`
- `bundled-plugins/`
- residual V1 app assets/rules no longer referenced by the Step 1 shell:
  - `app/src/main/assets/plugins/`
  - `app/src/main/keepRules/`
  - `app/src/main/res/xml/backup_rules.xml`
  - `app/src/main/res/xml/data_extraction_rules.xml`
- Create: `scripts/tests/v2-retired-runtime-absence-test.sh`

**Interfaces:**
- Consumes: Task 1 salvage ledger and Task 7 seven-module active graph.
- Produces: a repository tree with no dormant V1 runtime implementation while preserving these exact source roots unchanged: `core/common`, `catalog/model`, `catalog/engine`, `reader/engine`, `plugins/api`.
- Does **not** prune Gradle plugin classes or version-catalog aliases; Task 8B owns build-tool cleanup so source retirement and build-tool retirement have separate review gates.

- [ ] **Step 1: Write the failing retired-runtime absence test**

Create `scripts/tests/v2-retired-runtime-absence-test.sh`:

```bash
#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="${REPO_ROOT:-$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)}"

for retired in \
  core/designsystem \
  library \
  chapters \
  downloads \
  settings \
  storage \
  plugins/runtime \
  feature \
  bundled-plugins \
  app/src/main/assets/plugins \
  app/src/main/keepRules; do
  if [[ -e "$ROOT_DIR/$retired" ]]; then
    echo "Retired V1 runtime path still exists: $retired" >&2
    exit 1
  fi
done

for retired in \
  catalog/build.gradle.kts \
  catalog/src \
  reader/build.gradle.kts \
  reader/src \
  app/src/main/res/xml/backup_rules.xml \
  app/src/main/res/xml/data_extraction_rules.xml; do
  if [[ -e "$ROOT_DIR/$retired" ]]; then
    echo "Retired V1 integration path still exists: $retired" >&2
    exit 1
  fi
done

for retained in \
  core/common \
  catalog/model \
  catalog/engine \
  reader/engine \
  plugins/api; do
  [[ -d "$ROOT_DIR/$retained" ]] || {
    echo "Required retained/quarantined source missing: $retained" >&2
    exit 1
  }
done

echo "V2 retired runtime absence verified."
```

- [ ] **Step 2: Run the test before deletion and prove the old runtime is still present**

```bash
bash scripts/tests/v2-retired-runtime-absence-test.sh
```

Expected: FAIL on the first existing V1 runtime path.

- [ ] **Step 3: Record retained/quarantined source hashes before deletion**

```bash
find core/common catalog/model catalog/engine reader/engine plugins/api \
  -type f -not -path '*/build/*' -print0 |
  sort -z |
  xargs -0 sha256sum > /tmp/hikari-v2-retained-before.sha256
```

- [ ] **Step 4: Delete only the explicitly retired paths**

Use `git rm -r`/`git rm` for exactly the paths listed in this task. Do not use broad commands such as `git rm -r catalog reader plugins` because those parent directories contain retained/quarantined submodules.

- [ ] **Step 5: Verify retained/quarantined source hashes are byte-identical**

```bash
find core/common catalog/model catalog/engine reader/engine plugins/api \
  -type f -not -path '*/build/*' -print0 |
  sort -z |
  xargs -0 sha256sum > /tmp/hikari-v2-retained-after.sha256

diff -u /tmp/hikari-v2-retained-before.sha256 /tmp/hikari-v2-retained-after.sha256
```

Expected: no diff.

- [ ] **Step 6: Run the absence gate and retained-module regressions**

```bash
bash scripts/tests/v2-retired-runtime-absence-test.sh
./gradlew \
  verifyArchitecture \
  :core:common:test \
  :catalog:model:test \
  :catalog:engine:test \
  :reader:engine:test \
  :plugins:api:test \
  :app:testDebugUnitTest \
  --no-daemon
```

Expected: PASS.

- [ ] **Step 7: Commit only source retirement**

```bash
git add -A
git commit -m "refactor(v2): retire v1 runtime source"
```

---

## Task 8B: Prune build logic and dependency catalog to the active V2 surface

**Files to delete:**
- `build-logic/src/main/kotlin/app/openstory/build/AndroidLibraryConventionPlugin.kt`
- `build-logic/src/main/kotlin/app/openstory/build/HiltConventionPlugin.kt`
- `build-logic/src/main/kotlin/app/openstory/build/RoomConventionPlugin.kt`
- `build-logic/src/main/kotlin/app/openstory/build/packaging/CanonicalPluginPackageTask.kt`

**Files to modify:**
- `build-logic/build.gradle.kts`
- `gradle/libs.versions.toml`
- root `build.gradle.kts`
- Create: `scripts/tests/v2-build-surface-test.sh`

**Interfaces:**
- Active build-logic plugins after this task are exactly those needed by the seven-module graph: architecture, Android application, Compose, Kotlin JVM, V2 foundation.
- `androidx.profileinstaller` remains in the version catalog and app dependency surface because the accepted benchmark contract uses `BaselineProfileMode.Require` and the merged manifest explicitly classifies its initializer.
- No retained/quarantined engine behavior changes in this task.

- [ ] **Step 1: Write the failing build-surface test**

Create `scripts/tests/v2-build-surface-test.sh`:

```bash
#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="${REPO_ROOT:-$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)}"

for removed in \
  AndroidLibraryConventionPlugin.kt \
  HiltConventionPlugin.kt \
  RoomConventionPlugin.kt; do
  if find "$ROOT_DIR/build-logic/src/main/kotlin" -name "$removed" -print -quit | grep -q .; then
    echo "Inactive convention plugin still exists: $removed" >&2
    exit 1
  fi
done

[[ ! -e "$ROOT_DIR/build-logic/src/main/kotlin/app/openstory/build/packaging/CanonicalPluginPackageTask.kt" ]] || {
  echo "Inactive plugin packaging build task still exists." >&2
  exit 1
}

for forbidden in \
  'hilt = ' \
  'room = ' \
  'workManager = ' \
  'okhttp = ' \
  'coil = ' \
  'navigation3 = ' \
  'javascriptEngine = ' \
  'backdrop = ' \
  'roborazzi = '; do
  if grep -Fq "$forbidden" "$ROOT_DIR/gradle/libs.versions.toml"; then
    echo "Inactive version-catalog surface remains: $forbidden" >&2
    exit 1
  fi
done

grep -Fq 'profileInstaller = ' "$ROOT_DIR/gradle/libs.versions.toml" || {
  echo "ProfileInstaller version was removed from the accepted V2 toolchain." >&2
  exit 1
}
grep -Fq 'androidx-profileinstaller' "$ROOT_DIR/gradle/libs.versions.toml" || {
  echo "ProfileInstaller library alias was removed from the accepted V2 toolchain." >&2
  exit 1
}

echo "V2 build surface verified."
```

- [ ] **Step 2: Run it and prove inactive build tooling is still present**

```bash
bash scripts/tests/v2-build-surface-test.sh
```

Expected: FAIL before pruning.

- [ ] **Step 3: Remove unused convention plugins and their build-logic dependencies**

`build-logic/build.gradle.kts` must keep only plugin/runtime dependencies needed by:

```text
Android Gradle Plugin
Kotlin Gradle Plugin
Kotlin Compose Gradle Plugin
kotlinx.serialization JSON (foundation/module policy parsing)
JUnit/Kotlin test dependencies
```

Delete registrations for:

```text
openstory.android.library
openstory.hilt
openstory.room
```

Keep registrations for:

```text
openstory.architecture
openstory.android.application
openstory.compose
openstory.kotlin.jvm
openstory.foundation
```

- [ ] **Step 4: Shrink the version catalog to actual Step 1 consumers**

Retain aliases/versions required by:

```text
AGP / Kotlin / Kotlin serialization / Compose compiler
Android core / Activity Compose / Compose BOM / Material3 / Compose UI
Preferences DataStore / coroutines
ProfileInstaller
JUnit / coroutine-test
AndroidX JUnit / test-core / runner / services / orchestrator / Espresso / Compose UI test
Macrobenchmark / UiAutomator / Baseline Profile
Detekt
```

Remove Hilt, KSP, Room, WorkManager, OkHttp, Jsoup, BouncyCastle, JavaScriptEngine, Coil, Backdrop, Roborazzi, Navigation 3, AndroidX Hilt, `javax.inject`, and their plugin aliases when no active build file references them.

- [ ] **Step 5: Remove stale root plugin aliases**

After catalog pruning, make `build.gradle.kts` declare only root plugins whose aliases still exist and are actually used. Do not keep `roborazzi` or other removed aliases merely as `apply false` placeholders.

- [ ] **Step 6: Run the build-surface test and compile the active graph**

```bash
bash scripts/tests/v2-build-surface-test.sh
./gradlew \
  verifyArchitecture \
  :build-logic:test \
  :core:common:test \
  :catalog:model:test \
  :catalog:engine:test \
  :reader:engine:test \
  :plugins:api:test \
  :app:testDebugUnitTest \
  --no-daemon
```

Expected: PASS.

- [ ] **Step 7: Verify no active build script names a retired project/plugin**

```bash
if grep -RInE \
  'project\(":(library|chapters|downloads|settings|storage|plugins:runtime|feature:|reader"|catalog")|openstory\.(hilt|room|android\.library)' \
  --include='*.gradle.kts' . \
  --exclude-dir=build \
  --exclude-dir=docs; then
  echo "Retired V1 project/plugin reference remains in active build logic." >&2
  exit 1
fi
```

Expected: no matches.

- [ ] **Step 8: Commit build-tool pruning separately**

```bash
git add -A build-logic gradle/libs.versions.toml build.gradle.kts scripts/tests/v2-build-surface-test.sh
git commit -m "build(v2): prune inactive build surface"
```

---

## Task 9: Reset verification entrypoints and debt allowlists to a V2 baseline

**Files:**
- Replace: `scripts/verification-common.sh`
- Replace: `scripts/verify.sh`
- Replace: `scripts/verify-fast.sh`
- Modify: `scripts/verify-source-layout.sh`
- Modify: `scripts/structural-review-report.sh`
- Keep/adjust: `scripts/verify-structural-suppressions.sh`
- Replace: `config/source-layout-allowlist.txt` with comments only
- Keep empty: `config/quality/structural-suppressions.txt`
- Delete V1-only static scripts and tests that target removed modules/waves
- Create: `scripts/tests/v2-verification-entrypoints-test.sh`
- Create: `scripts/tests/v2-source-layout-policy-test.sh`

**Interfaces:**
- `run_repository_static_gates` runs only tests/policies whose targets exist in the V2 tree.
- Missing policy targets fail closed.
- The source-layout allowlist starts with zero active entries.
- `verifyArchitecture` remains the authoritative module + V2 boot/structure/manifest gate.

- [ ] **Step 1: Write the verification-entrypoint test**

The test must assert that:

```text
scripts/verify.sh calls run_repository_static_gates and verifyArchitecture
scripts/verify-fast.sh calls run_repository_static_gates and verifyArchitecture
neither entrypoint calls Room schema verification
neither entrypoint calls wave-specific production policy
neither entrypoint calls deleted V1 package/current-architecture scripts
```

Example:

```bash
for entry in scripts/verify.sh scripts/verify-fast.sh; do
  grep -Fq 'run_repository_static_gates' "$entry"
  grep -Fq 'verifyArchitecture' "$entry"
  if grep -Eq 'room-schema|wave-10|verify-current-architecture|verify-package-boundaries' "$entry"; then
    echo "V1 verification call remains in $entry" >&2
    exit 1
  fi
done
```

- [ ] **Step 2: Write the source-layout ratchet test**

It must fail if any active allowlist row contains a reason with temporary/pending/generation language and must fail if a listed path does not exist.

For Step 1, `config/source-layout-allowlist.txt` contains comments only, so the expected active-row count is zero.

- [ ] **Step 3: Run both tests and observe V1 entrypoint failures**

```bash
bash scripts/tests/v2-verification-entrypoints-test.sh
bash scripts/tests/v2-source-layout-policy-test.sh
```

Expected: at least the entrypoint test FAILS before scripts are rewritten.

- [ ] **Step 4: Delete V1-only static gates/tests**

Remove scripts whose only purpose is a deleted runtime/wave, including the old current-architecture/package-boundary/Room-schema/UI-token/wave-specific entrypoints and their corresponding `scripts/tests/*` files.

Do not delete generic source-layout, structural-suppression, or structural-review machinery; simplify those instead.

- [ ] **Step 5: Rewrite `verification-common.sh`**

Target:

```bash
run_repository_static_gates() {
  for test_script in ./scripts/tests/*.sh; do
    bash "$test_script"
  done

  ./scripts/verify-structural-suppressions.sh
  ./scripts/verify-source-layout.sh
  ./scripts/structural-review-report.sh
}
```

No deleted capability path is named.

- [ ] **Step 6: Rewrite `verify-fast.sh`**

Use:

```bash
"$GRADLEW" \
  --dependency-verification strict \
  verifyArchitecture \
  :build-logic:test \
  :core:common:test \
  :catalog:model:test \
  :catalog:engine:test \
  :reader:engine:test \
  :plugins:api:test \
  :app:testDebugUnitTest \
  detekt \
  --stacktrace
```

- [ ] **Step 7: Rewrite `verify.sh`**

Full verification adds:

```text
:app:lintDebug
:app:assembleDebug
```

and keeps all focused retained-module tests above.

- [ ] **Step 8: Simplify structural report/layout scripts**

Requirements:

- no deleted module-specific grep rules;
- source-layout checks still fail for missing allowlist targets;
- app production files above 300 lines are already hard-failed by `verifyAppStructure`;
- generic report may still report retained/quarantined engine files above 300 lines without labeling them new V2 app debt;
- no active source-layout allowance exists at Step 1 close.

- [ ] **Step 9: Run the static gates and fast verification**

```bash
bash scripts/tests/v2-cutover-context-test.sh
bash scripts/tests/v2-salvage-ledger-test.sh
bash scripts/tests/v2-capability-admission-contract-test.sh
bash scripts/tests/v2-verification-entrypoints-test.sh
bash scripts/tests/v2-source-layout-policy-test.sh
bash scripts/verify-fast.sh
```

Expected: PASS.

- [ ] **Step 10: Commit**

```bash
git add scripts config/source-layout-allowlist.txt config/quality/structural-suppressions.txt
git commit -m "build(v2): reset verification to zero-debt foundation"
```

---

## Task 10: Add the tiny launch-state model and DataStore persistence

**Files:**
- Create: `app/src/main/kotlin/app/openstory/startup/AppLaunchState.kt`
- Create: `app/src/main/kotlin/app/openstory/startup/AppLaunchStateStore.kt`
- Create: `app/src/main/kotlin/app/openstory/startup/AppLaunchStateDataStore.kt`
- Create: `app/src/test/kotlin/app/openstory/startup/AppLaunchStateStoreTest.kt`
- Modify: `app/build.gradle.kts`

**Interfaces:**
- Produces:

```kotlin
internal sealed interface AppLaunchState {
    data object Unknown : AppLaunchState
    data object FirstRun : AppLaunchState
    data object Ready : AppLaunchState
}

internal class AppLaunchStateStore(
    private val dataStore: DataStore<Preferences>,
    private val reportFailure: (code: String, failure: Throwable) -> Unit = { _, _ -> },
) {
    suspend fun resolve(): AppLaunchState
    suspend fun markInitialSetupCompleted(): Boolean
}

internal fun createAppLaunchStateStore(context: Context): AppLaunchStateStore
```

- `resolve()`:
  - absent/false -> `FirstRun`
  - true -> `Ready`
  - `IOException`/DataStore corruption -> log non-sensitive diagnostic and return `FirstRun`
  - cancellation -> rethrow
  - unexpected programmer/runtime exceptions -> do not silently convert
- `markInitialSetupCompleted()`:
  - successful durable edit -> `true`
  - I/O failure -> log and return `false`
  - cancellation -> rethrow

- [ ] **Step 1: Add Preferences DataStore + coroutine test dependencies**

Add only:

```kotlin
implementation(libs.androidx.datastore.preferences)
implementation(libs.kotlinx.coroutines.core)
testImplementation(libs.kotlinx.coroutines.test)
```

Run the V2 source/build gate afterward; these are explicitly allowed Step 1 dependencies.

- [ ] **Step 2: Write store tests first**

Use a test-local `DataStore<Preferences>` fake; do not add a `forTest` production factory.

Required tests:

```kotlin
@Test
fun absentCompletionFactResolvesFirstRun() = runTest { ... }

@Test
fun trueCompletionFactResolvesReady() = runTest { ... }

@Test
fun completionWritePersistsTrue() = runTest { ... }

@Test
fun readIoFailureFallsBackToFirstRun() = runTest { ... }

@Test
fun writeIoFailureReturnsFalse() = runTest { ... }

@Test
fun readCancellationPropagates() = runTest { ... }

@Test
fun writeCancellationPropagates() = runTest { ... }
```

The fake implements the public `DataStore<Preferences>` contract inside test source only.

Use this test-local shape so no Android logging/runtime is needed by local JVM tests:

```kotlin
private class FakePreferencesDataStore(
    initial: Preferences = emptyPreferences(),
    private val readFailure: Throwable? = null,
    private val writeFailure: Throwable? = null,
) : DataStore<Preferences> {
    private val state = MutableStateFlow(initial)

    override val data: Flow<Preferences> = flow {
        readFailure?.let { throw it }
        emitAll(state)
    }

    override suspend fun updateData(
        transform: suspend (t: Preferences) -> Preferences,
    ): Preferences {
        writeFailure?.let { throw it }
        return transform(state.value).also { state.value = it }
    }
}
```

- [ ] **Step 3: Run tests and confirm compile failure**

```bash
./gradlew :app:testDebugUnitTest --tests '*AppLaunchStateStoreTest*' --no-daemon
```

Expected: FAIL because the startup model/store does not exist.

- [ ] **Step 4: Implement the state model**

```kotlin
package app.openstory.startup

internal sealed interface AppLaunchState {
    data object Unknown : AppLaunchState
    data object FirstRun : AppLaunchState
    data object Ready : AppLaunchState
}
```

- [ ] **Step 5: Implement DataStore ownership**

```kotlin
package app.openstory.startup

import android.content.Context
import androidx.datastore.preferences.preferencesDataStore

private val Context.launchStateDataStore by preferencesDataStore(
    name = "hikari_launch_state",
)

internal fun createAppLaunchStateStore(
    context: Context,
): AppLaunchStateStore = AppLaunchStateStore(
    dataStore = context.applicationContext.launchStateDataStore,
    reportFailure = { code, failure ->
        Log.w("HikariLaunchState", code, failure)
    },
)
```

No generic settings repository is introduced.

- [ ] **Step 6: Implement read/write semantics**

Core shape:

```kotlin
private val initialSetupCompletedKey =
    booleanPreferencesKey("initial_setup_completed")

internal class AppLaunchStateStore(
    private val dataStore: DataStore<Preferences>,
    private val reportFailure: (code: String, failure: Throwable) -> Unit = { _, _ -> },
) {
    suspend fun resolve(): AppLaunchState = try {
        if (dataStore.data.first()[initialSetupCompletedKey] == true) {
            AppLaunchState.Ready
        } else {
            AppLaunchState.FirstRun
        }
    } catch (cancelled: CancellationException) {
        throw cancelled
    } catch (failure: IOException) {
        reportFailure("launch_state_read_failed", failure)
        AppLaunchState.FirstRun
    }

    suspend fun markInitialSetupCompleted(): Boolean = try {
        dataStore.edit { preferences ->
            preferences[initialSetupCompletedKey] = true
        }
        true
    } catch (cancelled: CancellationException) {
        throw cancelled
    } catch (failure: IOException) {
        reportFailure("launch_state_write_failed", failure)
        false
    }
}
```

Use `kotlin.coroutines.cancellation.CancellationException`.

- [ ] **Step 7: Run store + foundation gates**

```bash
./gradlew \
  :app:testDebugUnitTest --tests '*AppLaunchStateStoreTest*' \
  :app:verifyFoundation \
  --no-daemon
```

Expected: PASS.

- [ ] **Step 8: Commit**

```bash
git add app/build.gradle.kts app/src/main/kotlin/app/openstory/startup \
  app/src/test/kotlin/app/openstory/startup
git commit -m "feat(v2): add isolated launch state persistence"
```

---

## Task 11: Add StartupGate, FirstRun completion, and returning Home shell

**Files:**
- Create: `app/src/main/kotlin/app/openstory/startup/ui/StartupGate.kt`
- Create: `app/src/main/kotlin/app/openstory/startup/ui/UnknownScreen.kt`
- Create: `app/src/main/kotlin/app/openstory/startup/ui/FirstRunScreen.kt`
- Create: `app/src/main/kotlin/app/openstory/startup/ui/HomeShell.kt`
- Modify: `app/src/main/kotlin/app/openstory/MainActivity.kt`
- Delete/replace: Task 6's inline `startup-shell` content
- Modify: `app/src/androidTest/kotlin/app/openstory/AppLaunchSmokeTest.kt`

**Interfaces:**
- `MainActivity` still does not create the store; it only calls `HikariStartupApp()` from `setContent`.
- `HikariStartupApp()` obtains the application-context store inside composition and calls `StartupGate`.
- `StartupGate` owns only:
  - current `AppLaunchState`;
  - completion write in-flight/error UI state;
  - launch-state resolution.
- Test tags:
  - `startup-unknown`
  - `startup-first-run`
  - `startup-complete`
  - `startup-completion-error`
  - `startup-home`

- [ ] **Step 1: Write a source contract test before UI implementation**

Extend `AppShellContractTest`:

```kotlin
@Test
fun startupGateDoesNotUseNavigationOrAnimationFramework() {
    val source = rootFile(
        "app/src/main/kotlin/app/openstory/startup/ui/StartupGate.kt",
    ).readText()

    listOf(
        "NavDisplay",
        "NavController",
        "AnimatedContent",
        "Crossfade",
        "rememberNavBackStack",
    ).forEach { forbidden ->
        assertFalse(forbidden in source)
    }
}
```

Initially point the assertion at the absent file and make the helper fail clearly if it is missing.

- [ ] **Step 2: Run the focused test and observe failure**

```bash
./gradlew :app:testDebugUnitTest --tests '*AppShellContractTest*' --no-daemon
```

Expected: FAIL because `StartupGate.kt` is absent.

- [ ] **Step 3: Implement `UnknownScreen`**

Requirements:

```text
static Hikari identity only
no progress indicator
no empty-state copy
no Home data placeholder
testTag("startup-unknown")
```

- [ ] **Step 4: Implement `FirstRunScreen`**

Use a single deliberate completion button. It receives:

```kotlin
@Composable
internal fun FirstRunScreen(
    isSaving: Boolean,
    saveFailed: Boolean,
    onComplete: () -> Unit,
)
```

When `saveFailed` is true, show a small retryable persistence error. The button stays on FirstRun until a durable write succeeds.

- [ ] **Step 5: Implement static `HomeShell`**

```kotlin
@Composable
internal fun HomeShell() {
    Text(
        text = "Hikari",
        modifier = Modifier.testTag("startup-home"),
    )
}
```

No repository, ViewModel, plugin, engine, Room, network, or fake product data.

- [ ] **Step 6: Implement `StartupGate`**

Required control flow:

```kotlin
@Composable
internal fun HikariStartupApp() {
    val context = LocalContext.current.applicationContext
    val store = remember(context) {
        createAppLaunchStateStore(context)
    }

    HikariBootTheme {
        HikariBootSurface {
            StartupGate(store)
        }
    }
}
```

`StartupGate(store)` begins with:

```kotlin
var launchState by remember { mutableStateOf<AppLaunchState>(AppLaunchState.Unknown) }
var saveInFlight by remember { mutableStateOf(false) }
var saveFailed by remember { mutableStateOf(false) }

LaunchedEffect(store) {
    launchState = store.resolve()
}
```

This `LaunchedEffect` may start before the first frame, but composition never awaits it before drawing `Unknown`.

Completion:

```kotlin
val scope = rememberCoroutineScope()

fun completeInitialSetup() {
    if (saveInFlight) return
    saveInFlight = true
    saveFailed = false
    scope.launch {
        val persisted = store.markInitialSetupCompleted()
        saveInFlight = false
        if (persisted) {
            launchState = AppLaunchState.Ready
        } else {
            saveFailed = true
        }
    }
}
```

Implement the callback as local state/coroutine logic; do not create a manager/coordinator.

- [ ] **Step 7: Update `MainActivity`**

`setContent` becomes only:

```kotlin
setContent {
    HikariStartupApp()
}
```

`MainActivity` must not import `AppLaunchStateStore` or DataStore.

- [ ] **Step 8: Update the Task 6 smoke test to the real first-run tag**

Because `startup-shell` is replaced by the state gate, change `AppLaunchSmokeTest` to:

```kotlin
@Test
fun freshInstallReachesFirstRunShell() {
    composeRule.onNodeWithTag("startup-first-run")
        .assertIsDisplayed()
}
```

Android Test Orchestrator clears package data for this test, so a fresh instrumentation test process must resolve FirstRun.

- [ ] **Step 9: Run app unit/foundation gates**

```bash
./gradlew \
  :app:testDebugUnitTest \
  :app:verifyFoundation \
  :app:assembleDebug \
  --no-daemon
```

Expected: PASS.

- [ ] **Step 10: Commit**

```bash
git add app/src/main/kotlin/app/openstory app/src/test/kotlin/app/openstory app/src/androidTest/kotlin/app/openstory/AppLaunchSmokeTest.kt
git commit -m "feat(v2): add deterministic startup gate"
```

---

## Task 12: Prove first-run, completion, returning, and recreation behavior on Android

**Files:**
- Create: `app/src/androidTest/kotlin/app/openstory/startup/StartupSurfaceTest.kt`
- Create: `app/src/androidTest/kotlin/app/openstory/startup/StartupFlowTest.kt`
- Modify: `gradle/libs.versions.toml`
- Modify: `app/build.gradle.kts`

**Interfaces:**
- Component tests render the three shell surfaces directly.
- Flow tests create launch state before `ActivityScenario.launch` when testing returning launch.
- Android Test Orchestrator/`clearPackageData=true` provides fresh app data between instrumentation tests.
- Real force-stop/process-death returning launch is additionally covered by Task 14 macrobenchmark setup.

- [ ] **Step 1: Add AndroidX test-core as an explicit test dependency**

Add alias:

```toml
androidx-test-core = { module = "androidx.test:core", version.ref = "androidxTest" }
```

Add:

```kotlin
androidTestImplementation(libs.androidx.test.core)
```

- [ ] **Step 2: Write surface tests**

Using `createComposeRule()`:

```kotlin
@Test
fun unknownSurfaceDoesNotPretendProductContentIsLoading() {
    composeRule.setContent {
        HikariBootTheme {
            HikariBootSurface {
                UnknownScreen()
            }
        }
    }

    composeRule.onNodeWithTag("startup-unknown").assertExists()
    composeRule.onNodeWithTag("startup-first-run").assertDoesNotExist()
    composeRule.onNodeWithTag("startup-home").assertDoesNotExist()
}

@Test
fun firstRunFailureRemainsRetryable() {
    composeRule.setContent {
        HikariBootTheme {
            HikariBootSurface {
                FirstRunScreen(
                    isSaving = false,
                    saveFailed = true,
                    onComplete = {},
                )
            }
        }
    }

    composeRule.onNodeWithTag("startup-completion-error").assertExists()
    composeRule.onNodeWithTag("startup-complete").assertIsEnabled()
}
```

Also test the static Home tag.

- [ ] **Step 3: Write real activity flow tests**

Use `ActivityScenario.launch(MainActivity::class.java)` only after any setup state has been written.

Required tests:

```text
fresh package -> startup-first-run
tap startup-complete -> startup-home
pre-write initial_setup_completed=true -> startup-home
complete setup -> ActivityScenario.recreate() -> startup-home
```

For returning setup:

```kotlin
val context = ApplicationProvider.getApplicationContext<Context>()
runBlocking {
    check(createAppLaunchStateStore(context).markInitialSetupCompleted())
}
```

Then launch the scenario.

- [ ] **Step 4: Run compile/unit gates first**

```bash
./gradlew :app:testDebugUnitTest :app:assembleDebug --no-daemon
```

Expected: PASS.

- [ ] **Step 5: Run instrumentation tests on the connected device/emulator**

```bash
./gradlew :app:connectedDebugAndroidTest --no-daemon
```

Expected: all startup surface/flow tests PASS.

- [ ] **Step 6: Re-run V2 foundation verification**

```bash
./gradlew :app:verifyFoundation verifyArchitecture --no-daemon
```

Expected: PASS; test-only dependencies do not leak into production, and the only classified AndroidX Startup initializer in the merged app manifest is `androidx.profileinstaller.ProfileInstallerInitializer`.

- [ ] **Step 7: Commit**

```bash
git add gradle/libs.versions.toml app/build.gradle.kts app/src/androidTest
git commit -m "test(v2): prove startup state transitions"
```

---

## Task 13: Add startup trace milestones without introducing a startup manager

**Files:**
- Create: `app/src/main/kotlin/app/openstory/startup/StartupTrace.kt`
- Modify: `app/src/main/kotlin/app/openstory/HikariApplication.kt`
- Modify: `app/src/main/kotlin/app/openstory/MainActivity.kt`
- Modify: `app/src/main/kotlin/app/openstory/startup/ui/StartupGate.kt`
- Create: `app/src/test/kotlin/app/openstory/startup/StartupTraceContractTest.kt`
- Modify: `app/src/test/kotlin/app/openstory/AppShellContractTest.kt`

**Interfaces:**
- Perfetto/StartupTimingMetric provides process-start evidence; application code cannot truthfully emit a marker before the process exists.
- Custom trace labels are exactly:
  - `HikariV2:application-created`
  - `HikariV2:activity-created`
  - `HikariV2:content-requested`
  - `HikariV2:first-frame`
  - `HikariV2:launch-state-resolved`
  - `HikariV2:destination-ready`
- Use top-level helper functions/constants, not `StartupTraceManager`.

- [ ] **Step 1: Write trace-contract tests**

```kotlin
@Test
fun startupTraceLabelsAreStableAndUnique() {
    assertEquals(
        setOf(
            "HikariV2:application-created",
            "HikariV2:activity-created",
            "HikariV2:content-requested",
            "HikariV2:first-frame",
            "HikariV2:launch-state-resolved",
            "HikariV2:destination-ready",
        ),
        startupTraceLabels.toSet(),
    )
    assertEquals(startupTraceLabels.size, startupTraceLabels.toSet().size)
}
```

- [ ] **Step 2: Run and observe compile failure**

```bash
./gradlew :app:testDebugUnitTest --tests '*StartupTraceContractTest*' --no-daemon
```

Expected: FAIL because trace labels do not exist.

- [ ] **Step 3: Implement top-level trace helpers**

Use `android.os.Trace`:

```kotlin
internal inline fun <T> startupTraceSection(
    name: String,
    block: () -> T,
): T {
    Trace.beginSection(name)
    return try {
        block()
    } finally {
        Trace.endSection()
    }
}

internal fun startupTraceMark(name: String) {
    Trace.beginSection(name)
    Trace.endSection()
}
```

Keep the six labels in one internal list/constants:

```kotlin
internal const val TRACE_APPLICATION_CREATED = "HikariV2:application-created"
internal const val TRACE_ACTIVITY_CREATED = "HikariV2:activity-created"
internal const val TRACE_CONTENT_REQUESTED = "HikariV2:content-requested"
internal const val TRACE_FIRST_FRAME = "HikariV2:first-frame"
internal const val TRACE_LAUNCH_STATE_RESOLVED = "HikariV2:launch-state-resolved"
internal const val TRACE_DESTINATION_READY = "HikariV2:destination-ready"

internal val startupTraceLabels: List<String> = listOf(
    TRACE_APPLICATION_CREATED,
    TRACE_ACTIVITY_CREATED,
    TRACE_CONTENT_REQUESTED,
    TRACE_FIRST_FRAME,
    TRACE_LAUNCH_STATE_RESOLVED,
    TRACE_DESTINATION_READY,
)
```

- [ ] **Step 4: Mark Application/Activity/content request**

`HikariApplication.onCreate()` may now override only to trace platform startup:

```kotlin
override fun onCreate() {
    startupTraceSection(TRACE_APPLICATION_CREATED) {
        super.onCreate()
    }
}
```

No collaborator invocation is added.

Use this exact Activity shape; `StartupTimingMetric` remains authoritative for TTID while these sections correlate code ownership in Perfetto:

```kotlin
override fun onCreate(savedInstanceState: Bundle?) {
    startupTraceSection(TRACE_ACTIVITY_CREATED) {
        super.onCreate(savedInstanceState)
    }
    enableEdgeToEdge()
    startupTraceMark(TRACE_CONTENT_REQUESTED)
    setContent {
        HikariStartupApp()
    }
}
```

Do not put `store.resolve()`, a scheduler, or any domain call in this method.

- [ ] **Step 5: Mark first frame without blocking it**

Inside the root startup composition:

```kotlin
LaunchedEffect(Unit) {
    withFrameNanos {
        startupTraceMark(TRACE_FIRST_FRAME)
    }
}
```

This is measurement-only shell work. It is a Compose frame-clock correlation marker, **not** the authoritative “frame fully drawn” metric; `StartupTimingMetric` owns TTID. Do not attach maintenance after this callback.

- [ ] **Step 6: Mark state resolution and destination-ready**

After `store.resolve()` returns, mark `launch-state-resolved`.

Use `LaunchedEffect(Unit)` inside `FirstRunScreen` and `HomeShell` to mark `destination-ready` when that destination first enters composition. Do not add a global observer.

- [ ] **Step 7: Update the Application architecture contract for trace-only `onCreate`**

Task 6 intentionally required no `Application.onCreate`. After trace instrumentation, replace that assertion with:

```kotlin
@Test
fun applicationOnCreateIsTraceOnly() {
    val source = rootFile(
        "app/src/main/kotlin/app/openstory/HikariApplication.kt",
    ).readText()

    assertTrue("startupTraceSection" in source)
    assertTrue("super.onCreate()" in source)

    listOf(
        "WorkManager",
        "Reader",
        "Catalog",
        "PluginRuntime",
        "DataStore",
        "CoroutineScope",
        "launch {",
    ).forEach { forbidden ->
        assertFalse(forbidden in source, "Forbidden Application startup work: $forbidden")
    }
}
```

- [ ] **Step 8: Run unit/foundation gates**

```bash
./gradlew \
  :app:testDebugUnitTest --tests '*StartupTraceContractTest*' \
  :app:verifyFoundation \
  --no-daemon
```

Expected: PASS.

- [ ] **Step 9: Commit**

```bash
git add app/src/main/kotlin/app/openstory app/src/test/kotlin/app/openstory
git commit -m "perf(v2): add clean boot trace milestones"
```

---

## Task 14: Replace V1 product benchmarks with fresh/returning V2 startup benchmarks

**Files:**
- Delete: `benchmark/src/main/kotlin/app/openstory/benchmark/BenchmarkFixtureProfileRequest.kt`
- Replace: `benchmark/src/main/kotlin/app/openstory/benchmark/HikariBenchmarkDriver.kt`
- Replace: `benchmark/src/main/kotlin/app/openstory/benchmark/HikariMacrobenchmark.kt`
- Replace: `benchmark/src/main/kotlin/app/openstory/benchmark/BaselineProfileGenerator.kt`
- Create: `app/src/benchmarkRelease/kotlin/app/openstory/benchmark/BenchmarkLaunchStateActivity.kt`
- Create: `app/src/benchmarkRelease/AndroidManifest.xml`
- Modify: `app/build.gradle.kts` to re-attach benchmark-only Kotlin/manifest sources to the generated benchmark target variants after Baseline Profile DSL finalization
- Delete any remaining benchmark code that names Library/Discover/Search/Story/Reader

**Interfaces:**
- Benchmark target package: `app.openstory.v2benchmark`
- Benchmark fixture activity class: `app.openstory.benchmark.BenchmarkLaunchStateActivity`
- Fixture writes only `initial_setup_completed=true`.
- Fresh-install setup uses package clear-data, not a production intent extra.
- Required benchmark methods:
  - `coldFreshInstall`
  - `coldReturningLaunch`

- [ ] **Step 1: Write the new macrobenchmark class first**

Target shape:

```kotlin
@Test
fun coldFreshInstall() {
    benchmarkRule.measureRepeated(
        packageName = HIKARI_PACKAGE,
        metrics = listOf(StartupTimingMetric()),
        compilationMode = benchmarkCompilationMode,
        startupMode = StartupMode.COLD,
        iterations = 5,
        setupBlock = {
            prepareFreshInstall()
            pressHome()
        },
        measureBlock = {
            startHikariAndWait(FIRST_RUN_TAG)
        },
    )
}

@Test
fun coldReturningLaunch() {
    benchmarkRule.measureRepeated(
        packageName = HIKARI_PACKAGE,
        metrics = listOf(StartupTimingMetric()),
        compilationMode = benchmarkCompilationMode,
        startupMode = StartupMode.COLD,
        iterations = 5,
        setupBlock = {
            prepareReturningLaunch()
            pressHome()
        },
        measureBlock = {
            startHikariAndWait(HOME_TAG)
        },
    )
}
```

Use:

```kotlin
CompilationMode.Partial(
    baselineProfileMode = BaselineProfileMode.Require,
)
```

so accepted Step 1 measurements and future capability comparisons use the same shipping-style compilation contract. This contract assumes `androidx.profileinstaller` is present in the target APK; Task 6 keeps it intentionally and Task 5 ensures only its classified startup initializer is admitted.

- [ ] **Step 2: Compile and observe failures from old benchmark driver/contracts**

```bash
./gradlew :benchmark:compileBenchmarkReleaseKotlin --no-daemon
```

Expected: FAIL until the driver and fixture are replaced.

- [ ] **Step 3: Replace benchmark driver constants/helpers**

Replace `HikariBenchmarkDriver.kt` with the following narrow startup-only driver shape:

```kotlin
package app.openstory.benchmark

import androidx.benchmark.macro.BaselineProfileMode
import androidx.benchmark.macro.CompilationMode
import androidx.benchmark.macro.MacrobenchmarkScope
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.By
import androidx.test.uiautomator.UiDevice
import androidx.test.uiautomator.Until

internal const val HIKARI_PACKAGE = "app.openstory.v2benchmark"
internal const val FIRST_RUN_TAG = "startup-first-run"
internal const val HOME_TAG = "startup-home"

internal val benchmarkCompilationMode = CompilationMode.Partial(
    baselineProfileMode = BaselineProfileMode.Require,
)

private const val FIXTURE_COMPONENT =
    "app.openstory.v2benchmark/app.openstory.benchmark.BenchmarkLaunchStateActivity"
private const val FIXTURE_READY_TEXT = "HIKARI_V2_BENCHMARK_READY"
private const val UI_TIMEOUT_MILLIS = 10_000L
private const val FIXTURE_TIMEOUT_MILLIS = 30_000L

internal fun prepareFreshInstall() {
    val device = benchmarkDevice()
    device.executeShellCommand("am force-stop $HIKARI_PACKAGE")
    val clearResult = device.executeShellCommand("pm clear $HIKARI_PACKAGE")
    check("Success" in clearResult) {
        "Unable to clear V2 benchmark package data: $clearResult"
    }
    device.waitForIdle()
}

internal fun prepareReturningLaunch() {
    prepareFreshInstall()
    val device = benchmarkDevice()
    val launchResult = device.executeShellCommand(
        "am start -W -n $FIXTURE_COMPONENT",
    )
    check("Error" !in launchResult && "Exception" !in launchResult) {
        "V2 launch-state fixture failed to launch: $launchResult"
    }
    check(
        device.wait(
            Until.hasObject(By.text(FIXTURE_READY_TEXT)),
            FIXTURE_TIMEOUT_MILLIS,
        ),
    ) {
        "V2 launch-state fixture did not become ready."
    }
    device.pressHome()
    device.waitForIdle()
    device.executeShellCommand("am force-stop $HIKARI_PACKAGE")
}

internal fun MacrobenchmarkScope.startHikariAndWait(tag: String) {
    startActivityAndWait()
    check(
        benchmarkDevice().wait(Until.hasObject(By.res(tag)), UI_TIMEOUT_MILLIS),
    ) {
        "V2 startup destination was not found: $tag"
    }
}

private fun benchmarkDevice(): UiDevice = UiDevice.getInstance(
    InstrumentationRegistry.getInstrumentation(),
)
```

No helper accepts intent extras that change production startup behavior.

- [ ] **Step 4: Add benchmark-only launch-state fixture**

`BenchmarkLaunchStateActivity`:

```kotlin
package app.openstory.benchmark

import android.os.Bundle
import android.widget.TextView
import androidx.activity.ComponentActivity
import app.openstory.startup.createAppLaunchStateStore
import kotlinx.coroutines.runBlocking

class BenchmarkLaunchStateActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val persisted = runBlocking {
            createAppLaunchStateStore(applicationContext)
                .markInitialSetupCompleted()
        }
        check(persisted) {
            "Benchmark launch-state fixture could not persist Ready state."
        }

        setContentView(
            TextView(this).apply {
                text = "HIKARI_V2_BENCHMARK_READY"
            },
        )
    }
}
```

Blocking is acceptable only in this benchmark-only fixture activity because it is never part of production startup or measured `MainActivity`.

- [ ] **Step 5: Add benchmark-only manifest**

```xml
<manifest xmlns:android="http://schemas.android.com/apk/res/android">
    <application>
        <activity
            android:name="app.openstory.benchmark.BenchmarkLaunchStateActivity"
            android:excludeFromRecents="true"
            android:exported="true"
            android:finishOnTaskLaunch="true"
            android:noHistory="true"
            android:theme="@android:style/Theme.Material.Light.NoActionBar" />
    </application>
</manifest>
```

Do not add a production fixture extra/switch.

- [ ] **Step 6: Re-attach deterministic benchmark-only sources after Baseline Profile DSL finalization**

Restore the production-safe variant attachment pattern already required by this repository:

```kotlin
androidComponents {
    beforeVariants(selector().withBuildType("benchmarkRelease")) { variantBuilder ->
        variantBuilder.hostTests["UnitTest"]?.enable = true
    }
    finalizeDsl { extension ->
        listOf("benchmarkRelease", "nonMinifiedRelease").forEach { sourceSetName ->
            extension.sourceSets.getByName(sourceSetName).apply {
                kotlin.directories.add("src/benchmarkRelease/kotlin")
                manifest.srcFile("src/benchmarkRelease/AndroidManifest.xml")
            }
        }
    }
}
```

This is required because the Baseline Profile plugin copies release sources into generated benchmark target variants; do not rely on implicit source-set behavior.

- [ ] **Step 7: Replace Baseline Profile journeys**

Keep only startup:

```kotlin
@Test
fun startupReturning() {
    prepareReturningLaunch()
    baselineProfileRule.collect(
        packageName = HIKARI_PACKAGE,
        includeInStartupProfile = true,
    ) {
        pressHome()
        startHikariAndWait(HOME_TAG)
    }
}
```

Delete `criticalJourneys()` and every V1 feature navigation helper.

- [ ] **Step 8: Verify benchmark source contains no retired capability journey**

Run:

```bash
if grep -RInE \
  'navigation-library|navigation-discover|search-content|story-|reader-|chapter-list|library-collection' \
  benchmark/src/main app/src/benchmarkRelease; then
  echo "V1 product benchmark journey remains in V2 Step 1." >&2
  exit 1
fi
```

Expected: no matches.

- [ ] **Step 9: Compile app + benchmark variants**

```bash
./gradlew \
  :app:assembleBenchmarkRelease \
  :benchmark:compileBenchmarkReleaseKotlin \
  :app:verifyFoundation \
  --no-daemon
```

Expected: PASS. Benchmark-variant manifest may contain the benchmark fixture activity, but still no provider/service or forbidden permission.

- [ ] **Step 10: Commit**

```bash
git add -A app/src/benchmarkRelease benchmark app/build.gradle.kts
git commit -m "perf(v2): replace product journeys with startup baselines"
```

---

## Task 15: Capture the accepted baseline, run every gate, self-review, and freeze Step 1

**Files:**
- Create: `docs/internal/v2/startup-baseline-2026-09-07.md`
- Create: `docs/internal/checkpoints/hikari-v2-step-1-foundation-clean-boot.md`
- Modify only if a discovered defect requires it: files owned by Tasks 1–14

**Interfaces:**
- Produces the immutable Step 1 comparison point for later capability admissions.
- A checkpoint cannot say PASS without the command/output evidence from this task.
- Any issue found in self-review is fixed in its owning task area before the checkpoint is committed.

- [ ] **Step 1: Run the fast V2 gate from a clean Gradle process**

```bash
./gradlew --stop
bash scripts/verify-fast.sh
```

Expected: PASS.

- [ ] **Step 2: Run the full static/build gate**

```bash
bash scripts/verify.sh
```

Expected: PASS.

- [ ] **Step 3: Run Android startup instrumentation**

```bash
./gradlew :app:connectedDebugAndroidTest --no-daemon
```

Expected: PASS for fresh, completion, returning, component, and recreation startup tests.

- [ ] **Step 4: Generate the new startup-only Baseline Profile**

```bash
./gradlew :app:generateBaselineProfile --no-daemon
```

Expected: new V2 startup profile files are generated from `startupReturning`; no V1 feature descriptors are intentionally reintroduced by a stale checked-in profile.

- [ ] **Step 5: Run fresh-install cold startup**

```bash
./gradlew :benchmark:connectedBenchmarkReleaseAndroidTest \
  '-Pandroid.testInstrumentationRunnerArguments.class=app.openstory.benchmark.HikariMacrobenchmark#coldFreshInstall' \
  --no-daemon
```

Expected: PASS, five cold-start iterations.

- [ ] **Step 6: Run returning cold startup**

```bash
./gradlew :benchmark:connectedBenchmarkReleaseAndroidTest \
  '-Pandroid.testInstrumentationRunnerArguments.class=app.openstory.benchmark.HikariMacrobenchmark#coldReturningLaunch' \
  --no-daemon
```

Expected: PASS, five cold-start iterations.

- [ ] **Step 7: Record the baseline from actual benchmark output**

Create `docs/internal/v2/startup-baseline-2026-09-07.md` with:

```text
repository commit SHA
Android device model
Android API level/build fingerprint
benchmark target package app.openstory.v2benchmark
benchmark build type benchmarkRelease
compilation mode Partial + BaselineProfileMode.Require
five raw StartupTimingMetric iteration values for coldFreshInstall
median reported for coldFreshInstall
five raw StartupTimingMetric iteration values for coldReturningLaunch
median reported for coldReturningLaunch
paths/names of generated perfetto trace artifacts
SHA-256 of generated baseline-prof.txt and startup-prof.txt
```

Copy the exact values from the generated benchmark/test artifacts; do not round away the raw values used for future comparison.

- [ ] **Step 8: Verify trace milestones are present in generated traces**

Open at least one fresh-install and one returning trace in Perfetto (or inspect the trace strings with an equivalent local trace tool) and verify all six labels:

```text
HikariV2:application-created
HikariV2:activity-created
HikariV2:content-requested
HikariV2:first-frame
HikariV2:launch-state-resolved
HikariV2:destination-ready
```

If a marker is absent, fix Task 13 before continuing.

- [ ] **Step 9: Run the final architecture-specific evidence commands**

```bash
./gradlew verifyArchitecture :app:verifyFoundation :build-logic:test --no-daemon

grep -RInE \
  'androidx\.room|androidx\.work|okhttp3|coil\.|dagger\.hilt|app\.openstory\.(catalog|library|chapters|reader|downloads|settings|storage|plugins)\.' \
  app/src/main app/build.gradle.kts || true

grep -RInE '\b(forTest|createForTest)\s*\(' app/src/main || true

grep -RInE '\b(class|object|interface)\s+[A-Za-z0-9_]*(Manager|Coordinator|Registry|ServiceLocator)\b' \
  app/src/main || true
```

Expected: the three grep checks print no forbidden production matches.

- [ ] **Step 10: Confirm the active graph is exactly seven modules**

```bash
grep -n '^include(' settings.gradle.kts
./gradlew projects --no-daemon
```

Expected project modules:

```text
:app
:benchmark
:catalog:engine
:catalog:model
:core:common
:plugins:api
:reader:engine
```

No V1 runtime/integration module is listed.

- [ ] **Step 11: Confirm retained/quarantined engine tests are still green**

```bash
./gradlew \
  :core:common:test \
  :catalog:model:test \
  :catalog:engine:test \
  :reader:engine:test \
  :plugins:api:test \
  --no-daemon
```

Expected: PASS.

- [ ] **Step 12: Perform a final spec-to-implementation self-review**

Review these gates explicitly and fix any miss before checkpointing:

```text
Gate 0 — salvage ledger exists; Catalog engine/model remain quarantine
Gate A — app has Step 1 dependencies only; retained modules build independently
Gate B — Application/MainActivity own no domain work/collector
Gate C — merged manifests contain no hidden provider/service or forbidden permission
Gate D — fresh/completion/returning/Unknown/recreation UX is deterministic
Gate E — both cold-start baselines and trace markers are captured
Gate F — retained/quarantine regression tests are green
Gate G — PERF-01..08, admission contract, and zero-debt V2 structural ratchet are active
```

Also verify the implementation still satisfies every Step 1 non-goal. A real product repository/engine/plugin/worker appearing in `:app` is a failed Step 1 even if tests pass.

- [ ] **Step 13: Create the Step 1 checkpoint**

`docs/internal/checkpoints/hikari-v2-step-1-foundation-clean-boot.md` records:

1. accepted design/spec path;
2. implementation plan path;
3. final commit SHA;
4. seven-module active graph;
5. salvage/quarantine decisions;
6. all Gate 0–G results;
7. exact verification commands and PASS status;
8. link/path to startup baseline artifact;
9. generated Baseline Profile hashes;
10. explicit non-goals still deferred to Step 2+.

Do not add future feature plans to this checkpoint.

- [ ] **Step 14: Run one final fast gate after checkpoint documentation**

```bash
bash scripts/verify-fast.sh
```

Expected: PASS.

- [ ] **Step 15: Commit the freeze checkpoint and benchmark baseline**

```bash
git add docs/internal/v2/startup-baseline-2026-09-07.md \
  docs/internal/checkpoints/hikari-v2-step-1-foundation-clean-boot.md \
  app/src/release/generated/baselineProfiles
git commit -m "chore(v2): freeze step 1 foundation baseline"
```

---

# Plan Self-Review

## Spec coverage

| Spec area | Owning task(s) | Review result |
| --- | --- | --- |
| Decision / same-repository branch/worktree cutover / no second project / no `:app-v2` | 0, 1, 7, 8A, 8B | Covered and explicitly gated before source work |
| V1 salvage ledger before deletion | 1, 8A | Covered and ordered correctly |
| Retention tiers / Catalog quarantine | 1, 7, 15 | Covered |
| Clean active graph | 6, 7, 8A, 8B | Covered |
| BOOT-01 process has no domain semantics | 3, 5, 6, 15 | Enforced |
| BOOT-02 first frame not gated by state I/O | 10, 11, 12, 13 | Covered |
| BOOT-03 foreground launch not maintenance trigger | 3, 6, 15 | Enforced by forbidden surface/dependencies |
| BOOT-04 capability follows demand | 2 | Frozen as future admission rule; no real capability in Step 1 |
| BOOT-05 small app-shell dependency boundary | 3, 6, 7 | Machine-enforced |
| BOOT-06 renderable Unknown | 11, 12 | Covered |
| BOOT-07 hidden manifest startup | 5, 6, 14, 15 | Merged-manifest gate allows only the explicitly classified ProfileInstaller initializer and rejects every unclassified initializer/service/provider |
| BOOT-08 performance regression contract | 13, 14, 15 | Baseline and trace evidence captured |
| PERF-01..08 | 2, 15 | Normative operational contract exists |
| Startup state model | 10 | Exact three states |
| One persisted launch fact | 10 | Exact key |
| FirstRun persistence failure semantics | 10, 11, 12 | Covered |
| Cancellation propagation | 10 | Unit-tested |
| Unknown / FirstRun / Home shells | 11, 12 | Covered |
| No initial navigation animation | 11, 12 | No Navigation/animation framework in state resolution |
| Clean-install identity | 6, 14 | Debug/benchmark suffixes selected |
| Backup/restore isolation | 6 | `allowBackup=false` selected for Step 1 |
| Application/MainActivity minimal entrypoints | 6, 13 | Trace-only Application override after Task 13 |
| App dependency policy | 3, 6, 7, 8A, 8B | Source/build/module guards |
| Structural ratchet / SCC / test-only API / broad manager | 4, 9, 15 | Enforced |
| Fresh/returning startup benchmark | 14, 15 | Exactly two Step 1 macrobenchmarks |
| Benchmark-only fixture | 14 | Production startup has no benchmark behavior switch |
| Trace milestones | 13, 15 | Six fixed labels |
| Retained module regression tests | 7, 8A, 8B, 15 | Covered repeatedly |
| Step 1 failure semantics | 10, 11 | Covered |
| Capability ownership model | 2 | Documented without runtime framework |
| Acceptance Gates 0–G | 15 | Explicit close gate |
| Step 2 not predetermined | Global constraints, 2, 15 | No feature admission in this plan |

## Ordering self-review

1. **Wrong-repository / accidental-new-project risk:** Task 0 proves V2 shares the original Git common directory/history, records the cutover base, forbids `git init`/New Project/`:app-v2`, and establishes a clean retained-core baseline before source work. Correct.
2. **Knowledge loss risk:** Task 1 follows Task 0 and precedes every source deletion. Correct.
3. **Verifier bootstrap risk:** Tasks 3–5 build generic guards before Task 6 cuts the app. Correct.
4. **Broken-graph risk:** Task 6 makes `:app` independent before Task 7 removes module includes. Correct.
5. **Dormant-architecture risk:** Task 8A deletes V1 runtime source only after the retained graph is explicit and green; Task 8B prunes build tooling separately so deletion cannot hide build-surface mistakes. Correct.
6. **Verification-target risk:** Task 9 rewrites V1-specific verification only after the deleted paths are no longer architectural inputs. Correct.
7. **Startup feature creep risk:** persistent state and UI arrive only after graph/guard cleanup; they cannot accidentally pull V1 modules because the guards are already active. Correct.
8. **Benchmark contamination risk:** old product journeys/profiles are removed before accepted V2 baselines are captured. Correct.
9. **Catalog transplant risk:** no task adds `:catalog:engine` or `:catalog:model` to `:app`; they remain testable quarantine/reference modules. Correct.
10. **Reader over-port risk:** no task adds `:reader:engine` to `:app`; accepted transplant means retained candidate, not startup dependency. Correct.
11. **Background regression risk:** WorkManager/network/plugin/background dependencies are forbidden before any later capability exists. Correct.

## Type/signature consistency

The plan uses one launch-state API everywhere:

```kotlin
createAppLaunchStateStore(context: Context): AppLaunchStateStore
AppLaunchStateStore.resolve(): AppLaunchState
AppLaunchStateStore.markInitialSetupCompleted(): Boolean
```

Task 11 and the benchmark fixture in Task 14 consume those exact signatures. No later task renames or broadens them.

The state type remains exactly:

```kotlin
AppLaunchState.Unknown
AppLaunchState.FirstRun
AppLaunchState.Ready
```

No `Loading`, `Error`, migration, plugin, or domain state is added to the launch state machine.

## Placeholder / ambiguity review

- No implementation step delegates an undefined “manager”, “service”, or generalized bootstrap queue.
- Dynamic benchmark numbers are intentionally recorded only after Task 15 executes real measurements; the plan specifies exactly which raw values and environment facts must be copied.
- No task says to “port the old implementation”; retained V1 code is either unchanged candidate source or deleted.
- No task mutates Catalog algorithms under the guise of foundation cleanup.
- The only benchmark-only behavior lives in the benchmark source set and fixture activity.
- The only post-render shell coroutine work is launch-state resolution and trace instrumentation; maintenance work remains forbidden.
- Same-application-ID V1→V2 upgrade remains explicitly outside this plan.

## Additional plan red-team corrections

The implementation plan was re-checked against the actual V1 snapshot, the Big Update audit bundle, and current AndroidX behavior before handoff. The following plan defects were corrected inline:

1. **PR-01 — ProfileInstaller was incorrectly removed.** The first draft removed `androidx.profileinstaller` to get a provider-free manifest while still selecting `BaselineProfileMode.Require`. That is an invalid measurement contract: the Macrobenchmark Baseline Profile path requires the target app to contain ProfileInstaller. The final plan keeps it and classifies only `ProfileInstallerInitializer`; every other initializer remains forbidden.
2. **PR-02 — Manifest policy was stricter than the approved spec.** The spec requires initializers to be explicitly classified, not necessarily zero. The verifier now implements classification rather than a blanket provider ban.
3. **PR-03 — Task 7 commit omitted a modified production build-logic file.** `ArchitectureConventionPlugin.kt` is now included in the cutover commit command.
4. **PR-04 — Catalog quarantine remains non-negotiable.** No task makes `:catalog:engine` or `:catalog:model` reachable from `:app`; retained tests are evidence only, not admission.
5. **PR-05 — First-frame tracing is not used as the authoritative TTID source.** `StartupTimingMetric` remains authoritative for startup timing; the custom `HikariV2:first-frame` section is only a trace correlation marker and must not be interpreted as a more precise draw-completion metric.
6. **PR-06 — Benchmark fixture cannot alter production startup.** Ready-state setup remains in the benchmark-only source set and occurs outside the measured app launch; there is no production intent extra, BuildConfig switch, or benchmark branch in `MainActivity`/`StartupGate`.
7. **PR-07 — Same-repository cutover was implicit rather than executable.** The previous plan assumed an executor was already on a V2 branch and could therefore be misread as permission to create a new Android project/repository. Task 0 now proves the shared Git history/common directory, records the V1 base commit, requires the worktree workflow, forbids `git init`/New Project/`:app-v2`, and captures a clean retained-core baseline before Task 1.


# Execution Handoff

Plan implementation begins at **Task 0 inside the existing Hikari repository**. Do not pre-create another Android project or repository. Task 0 invokes `superpowers:using-git-worktrees`, establishes/reuses the isolated V2 workspace, proves shared Git history, records the cutover base, and captures the baseline before Task 1.

**Recommended:** after Task 0 is green, use `superpowers:subagent-driven-development` so each implementation task, including the independent 8A/8B cutover gates, gets a fresh implementation context and a review gate before the next task.

**Alternative:** use `superpowers:executing-plans` to execute this plan inline in ordered batches with review checkpoints.

Do not skip Task 0. Do not start Task 1+ when the preceding gate is red; the sequencing is part of the architecture contract.
