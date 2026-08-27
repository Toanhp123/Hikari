# Adaptive Reader Continuity / HES-v1 M7.4 AccessReason API Hygiene Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Retire the unused `AccessReason` exported symbol without changing Reader routing semantics, then reconcile canonical HES documentation and close M7.4 from fresh final-tree evidence.

**Architecture:** Keep the existing structural route facts (`AccessMode`, `AttemptRole`, `RouteAttempt`, `HedgeDirective`, ranking and recovery order) as the sole access/recovery explanation model. Remove only the redundant enum and its existence-only assertion; no replacement type or trace field is added. Governance remains evidence-driven: M7.4 reopens the HES-v1 freeze only for API hygiene and re-freezes only after blocking final-tree gates are green.

**Tech Stack:** Kotlin/JVM `:reader:engine`, Android/Kotlin `:reader`, Gradle 9.5.x, existing HES architecture/package/schema verification scripts.

**Spec:** `docs/superpowers/specs/2026-08-27-adaptive-reader-continuity-hes-v1-m7-4-access-reason-api-hygiene.md`

**Execution status (updated 2026-08-27):** **VERIFIED/CLOSED VIA THE ACCEPTED M7.5 FINAL-TREE MATRIX.** The API removal, canonical docs reconciliation, stale-roadmap repair, TDD RED/GREEN source probe, engine/Reader/downstream tests, architecture/package/current-architecture gates, retained host verification, Room schema stability, and instrumentation compilation are covered by the fresh M7.5 closure evidence. M7.5 is the single accepted final-tree closure boundary for the retained M7.4 implementation and final hardening.

## Global Constraints

- Keep `HES_V1`, `READER_ROUTING_V1`, `READER_POLICY_V1`, and `HEALTH_POLICY_V1` unchanged.
- Keep `ReaderDecisionTrace`, `RouteAttempt`, `AccessMode`, `AttemptRole`, `HedgeDirective`, ranking, fallback, and replay semantics unchanged.
- Do not add a replacement access-reason enum/property/trace field.
- Do not add a deprecation shim or compatibility alias unless external publication evidence is discovered.
- Keep `:reader:engine` JVM-only behind `:reader`; no dependency/module graph changes.
- Keep Room at schema 11; no `MIGRATION_11_12`.
- Preserve M7.3 checkpoint/implementation-plan text as historical evidence of the previously deferred debt.
- Do not fold unrelated structural-review/Detekt debt into this phase.

---

## File Structure / Expected Touch Set

### Engine API and tests

- Modify: `reader/engine/src/main/kotlin/app/openstory/reader/engine/ReaderRouteDecision.kt`
  - remove only `AccessReason`.
- Modify: `reader/engine/src/test/kotlin/app/openstory/reader/engine/ReaderDecisionTraceTest.kt`
  - remove existence-only `AccessReason` assertion and retain the real durable reason/rejection/diagnostic type contract.

### Canonical HES docs

- Modify: `docs/superpowers/specs/2026-08-25-adaptive-reader-continuity-hes-v1-design.md`
  - update post-freeze governance note, §63, §65 and SR-27.
- Modify: `docs/superpowers/plans/2026-08-25-adaptive-reader-continuity-hes-v1.md`
  - update implementation status and Task 5/Task 16 contract wording.
- Create: `docs/superpowers/specs/2026-08-27-adaptive-reader-continuity-hes-v1-m7-4-access-reason-api-hygiene.md`
- Create: `docs/superpowers/plans/2026-08-27-adaptive-reader-continuity-hes-v1-m7-4-access-reason-api-hygiene.md`

### Governance

- Modify: `docs/project/current-state.md`
- Modify: `docs/implementation/current-roadmap.md`
- Create: `docs/internal/checkpoints/adaptive-reader-continuity-hes-v1-m7-4.md`

Historical M7.3 plan/checkpoint files are intentionally not modified.

---

# Task 1 — Retire the Redundant `AccessReason` API Symbol

**Files:**

- Modify: `reader/engine/src/main/kotlin/app/openstory/reader/engine/ReaderRouteDecision.kt`
- Modify: `reader/engine/src/test/kotlin/app/openstory/reader/engine/ReaderDecisionTraceTest.kt`

**Interfaces:**

- Removes: public `AccessReason` enum.
- Preserves: `DecisionReason`, `RejectionCode`, `DiagnosticNote`, `ReaderDecisionTrace`, `RouteAttempt`, `AccessMode`, `AttemptRole`, `HedgeDirective`.
- Produces no replacement interface.

- [x] **Step 1: Capture the pre-change consumer/API audit**

Run:

```bash
rg -n "AccessReason" reader
```

Expected baseline:

```text
one production declaration in ReaderRouteDecision.kt
one existence-only test method/reference set in ReaderDecisionTraceTest.kt
no runtime producer/consumer/trace field
```

- [x] **Step 2: Create and run a temporary RED source-surface probe outside the repository**

Create `/tmp/m7-4-access-reason-absent.sh` (or an equivalent host temp path):

```bash
#!/usr/bin/env bash
set -euo pipefail
FILE="$1/reader/engine/src/main/kotlin/app/openstory/reader/engine/ReaderRouteDecision.kt"
if rg -q '^enum class AccessReason[[:space:]]*\{' "$FILE"; then
  echo 'RED: AccessReason is still exported.' >&2
  exit 1
fi
echo 'GREEN: AccessReason is not exported.'
```

Run:

```bash
bash /tmp/m7-4-access-reason-absent.sh "$PWD"
```

Expected before implementation: exit 1 with `RED: AccessReason is still exported.`

This probe is TDD evidence only and is not committed to the repository.

- [x] **Step 3: Narrow the long-lived type contract test**

Replace:

```kotlin
fun decisionReasonAccessReasonRejectionCodeAndDiagnosticNoteStayDistinctTypes()
```

with:

```kotlin
@Test
fun decisionReasonRejectionCodeAndDiagnosticNoteStayDistinctTypes() {
    val decisionReason: DecisionReason = DecisionReason.TOP_RANKED_NO_INCUMBENT
    val rejectionCode: RejectionCode = RejectionCode.NO_USABLE_ACCESS_PATH
    val diagnostic = DiagnosticNote(RejectionCode.EXPLICIT_RELEASE_NOT_PRESENT)

    assertEquals(DecisionReason.TOP_RANKED_NO_INCUMBENT, decisionReason)
    assertEquals(RejectionCode.NO_USABLE_ACCESS_PATH, rejectionCode)
    assertEquals(RejectionCode.EXPLICIT_RELEASE_NOT_PRESENT, diagnostic.code)
}
```

Do not add a repository test asserting that `AccessReason` does not exist.

- [x] **Step 4: Remove the production enum and nothing else**

Delete only:

```kotlin
enum class AccessReason {
    LOCAL_PREFERRED,
    REMOTE_PREFERRED,
    SAME_RELEASE_REMOTE_RECOVERY,
    RANKED_FALLBACK,
}
```

Do not add any replacement symbol.

- [x] **Step 5: Re-run the temporary probe and verify GREEN**

```bash
bash /tmp/m7-4-access-reason-absent.sh "$PWD"
```

Expected: exit 0 with `GREEN: AccessReason is not exported.`

- [ ] **Step 6: Run focused semantic engine tests**

```bash
./gradlew :reader:engine:test \
  --tests '*ReaderDecisionTraceTest*' \
  --tests '*RoutePlannerTest*' \
  --tests '*ReaderRouteEngineContractTest*' \
  --tests '*HedgePolicyTest*' \
  --tests '*ReaderGoldenScenariosTest*' \
  --no-daemon
```

Acceptance:

```text
trace/reason tests green
local -> same-release remote -> ranked fallback ordering unchanged
hedge semantics remain represented by AttemptRole.HEDGE/HedgeDirective
no AccessReason production/test reference remains
```

- [ ] **Step 7: Run full pure-engine suite**

```bash
./gradlew :reader:engine:test --no-daemon
```

- [ ] **Step 8: Commit the API cleanup**

```bash
git add reader/engine/src/main/kotlin/app/openstory/reader/engine/ReaderRouteDecision.kt \
        reader/engine/src/test/kotlin/app/openstory/reader/engine/ReaderDecisionTraceTest.kt
git commit -m "refactor(reader-engine): retire redundant access reason API"
```

---

# Task 2 — Reconcile Canonical HES Contracts and Current Governance

**Files:**

- Modify: `docs/superpowers/specs/2026-08-25-adaptive-reader-continuity-hes-v1-design.md`
- Modify: `docs/superpowers/plans/2026-08-25-adaptive-reader-continuity-hes-v1.md`
- Modify: `docs/project/current-state.md`
- Modify: `docs/implementation/current-roadmap.md`
- Keep historical M7.3 plan/checkpoint unchanged.

**Interfaces:**

Canonical trace explanation becomes:

```text
semantic winner reason -> DecisionReason
candidate/access rejection -> RejectionCode / CandidateRejection
observational note -> DiagnosticNote
access/recovery topology -> AccessMode + AttemptRole + routeConstruction + stableRanking + HedgeDirective
```

- [x] **Step 1: Update canonical design**

Apply all of the following together:

1. Post-freeze note: state that M7.4 reopened the freeze only for API hygiene while M7.3 remains historical closure evidence.
2. §63: describe only routing value/reason/rejection contracts actually consumed or returned.
3. §65: remove `AccessReason` from the reason-class list and explicitly state that access/recovery explanation is structural, not a duplicate reason enum.
4. SR-27: replace the old resolution with:

```text
DecisionReason, RejectionCode, and DiagnosticNote remain separate semantic categories.
Access/recovery topology is represented directly by AccessMode, AttemptRole, routeConstruction,
stableRanking, and HedgeDirective; a duplicate AccessReason taxonomy is not retained.
```

Do not alter decision/replay behavior.

- [x] **Step 2: Update canonical implementation plan**

- Task 5 produced contracts: remove `AccessReason`.
- Task 16 trace contract: replace
  `DecisionReason/AccessReason/RejectionCode/DiagnosticNote remain distinct types`
  with
  `DecisionReason/RejectionCode/DiagnosticNote remain distinct types; access/recovery topology is represented by immutable route facts`.
- Status note: M7.4 is the current API-hygiene boundary until final gates pass.

- [x] **Step 3: Repair the two current-status surfaces**

Before M7.4 closure, both files must agree on:

```text
M0–M7.3 VERIFIED/CLOSED historical milestones
M7.4 IN PROGRESS
HES-v1 freeze reopened only for AccessReason API hygiene
Wave 10 remains VERIFIED/CLOSED
Room schema 11 / 17 production modules unchanged
```

Also remove the stale roadmap prose that still calls M7.3 `IN PROGRESS` despite its verified/closed table row.

- [x] **Step 4: Audit documentation before closure**

```bash
rg -n "AccessReason" \
  reader \
  docs/superpowers/specs/2026-08-25-adaptive-reader-continuity-hes-v1-design.md \
  docs/superpowers/plans/2026-08-25-adaptive-reader-continuity-hes-v1.md \
  docs/project/current-state.md \
  docs/implementation/current-roadmap.md

rg -n "M7\.3 is IN PROGRESS|M7\.3 must close before|M7\.4|RE-FROZEN|freeze is REOPENED" \
  docs/project/current-state.md docs/implementation/current-roadmap.md
```

Expected before final closure:

```text
no AccessReason hit in reader
canonical design/plan may mention only the M7.4 retirement decision/status, not list it as a live contract
no current-surface statement claims M7.3 is still in progress
M7.4 is consistently the active boundary
```

- [ ] **Step 5: Commit normative/governance reconciliation**

```bash
git add docs/superpowers/specs/2026-08-25-adaptive-reader-continuity-hes-v1-design.md \
        docs/superpowers/plans/2026-08-25-adaptive-reader-continuity-hes-v1.md \
        docs/project/current-state.md \
        docs/implementation/current-roadmap.md \
        docs/superpowers/specs/2026-08-27-adaptive-reader-continuity-hes-v1-m7-4-access-reason-api-hygiene.md \
        docs/superpowers/plans/2026-08-27-adaptive-reader-continuity-hes-v1-m7-4-access-reason-api-hygiene.md
git commit -m "docs(reader): define M7.4 access reason API hygiene"
```

---

# Task 3 — Verify, Checkpoint, and Re-freeze M7.4

**Files:**

- Create: `docs/internal/checkpoints/adaptive-reader-continuity-hes-v1-m7-4.md`
- Modify after fresh evidence is green:
  - `docs/project/current-state.md`
  - `docs/implementation/current-roadmap.md`
  - canonical design/plan status notes
  - this implementation plan checkbox state as execution evidence

- [x] **Step 1: Run engine + Reader/downstream compile regressions**

```bash
./gradlew :reader:engine:test --no-daemon
./gradlew :reader:testDebugUnitTest --no-daemon
./gradlew :feature:reader:testDebugUnitTest :app:compileDebugKotlin --no-daemon
```

- [x] **Step 2: Run architecture and package-boundary gates**

```bash
./gradlew :build-logic:test verifyArchitecture --no-daemon
bash scripts/tests/verify-package-boundaries-test.sh
bash scripts/verify-package-boundaries.sh
bash scripts/tests/verify-current-architecture-test.sh
bash scripts/verify-current-architecture.sh
```

Required boundary remains:

```text
17 production modules
1 android-test module
:reader:engine JVM-only
:reader consumes :reader:engine behind the existing boundary
Room schema 1..11
```

- [x] **Step 3: Run retained host/schema closure gates**

```bash
bash scripts/verify-fast.sh
bash scripts/verify.sh
bash scripts/verify-room-schema-stability.sh
```

Expected: no schema export change and the established Room schema-11 digest remains stable.

- [x] **Step 4: Run final API/version/diff audit**

```bash
rg -n "AccessReason" reader
rg -n "HES_V1|READER_ROUTING_V1|READER_POLICY_V1|HEALTH_POLICY_V1" reader/engine/src/main/kotlin
rg -n "M7\.3 is IN PROGRESS|M7\.3 must close before" docs/project/current-state.md docs/implementation/current-roadmap.md

git diff --check
git status --short
git diff --stat
```

Acceptance:

```text
no AccessReason in reader source/tests
all V1 constants still present/unchanged
no stale current M7.3-in-progress wording
diff contains no Room/module/dependency change
```

- [x] **Step 5: Create the M7.4 checkpoint from actual evidence**

`docs/internal/checkpoints/adaptive-reader-continuity-hes-v1-m7-4.md` must record:

```text
retain-vs-retire decision and evidence
files/contracts removed
explicit no-replacement statement
exact fresh commands/results
module/schema/version invariants
historical M7.3 evidence preserved
current-roadmap stale-status correction
contradiction audit
closure decision
```

Do not fabricate commit SHAs or device evidence not present in the execution environment.

- [x] **Step 6: Re-freeze only after blocking evidence is green**

Update canonical/current status to:

```text
Adaptive Reader Continuity / HES-v1: M0–M7.4 VERIFIED/CLOSED; HES-v1 RE-FROZEN.
```

State explicitly:

```text
AccessReason API hygiene resolved by retirement; no replacement taxonomy; versions/module/schema unchanged.
```

If any blocking host gate is unavailable or failing, leave M7.4 `IN PROGRESS / NOT CLOSED` and record the exact blocker instead.

- [ ] **Step 7: Commit closure**

```bash
git add docs/internal/checkpoints/adaptive-reader-continuity-hes-v1-m7-4.md \
        docs/project/current-state.md \
        docs/implementation/current-roadmap.md \
        docs/superpowers/specs/2026-08-25-adaptive-reader-continuity-hes-v1-design.md \
        docs/superpowers/plans/2026-08-25-adaptive-reader-continuity-hes-v1.md \
        docs/superpowers/plans/2026-08-27-adaptive-reader-continuity-hes-v1-m7-4-access-reason-api-hygiene.md
git commit -m "docs(reader): close HES-v1 M7.4 API hygiene"
```

---

# Final Acceptance Matrix

| ID | Contract | Evidence |
|---|---|---|
| H1 | `AccessReason` removed from exported source | temporary RED/GREEN probe + `rg` |
| H2 | no replacement enum/property/trace field | source/diff audit |
| H3 | `ReaderDecisionTrace` shape unchanged | engine compile/tests + diff review |
| H4 | route construction/recovery ordering unchanged | `RoutePlannerTest`, contract/golden tests |
| H5 | hedge remains explicit via `AttemptRole.HEDGE`/`HedgeDirective` | `HedgePolicyTest` |
| H6 | durable reason/rejection/diagnostic types remain distinct | `ReaderDecisionTraceTest` |
| H7 | canonical design no longer treats `AccessReason` as live normative contract | docs audit |
| H8 | canonical plan no longer requires `AccessReason` | docs audit |
| H9 | M7.3 remains historical accepted evidence | checkpoint untouched + governance review |
| H10 | current-state/current-roadmap agree on M7.4 | current docs audit |
| H11 | all HES/policy V1 constants unchanged | source audit |
| H12 | module boundary unchanged | architecture/package verifiers |
| H13 | Room schema remains 11 | current architecture + schema stability verifier |
| H14 | full retained HES host gates green | Gradle/script matrix |

## Preferred Commit Shape

```text
1. refactor(reader-engine): retire redundant access reason API
2. docs(reader): define M7.4 access reason API hygiene
3. docs(reader): close HES-v1 M7.4 API hygiene
```
