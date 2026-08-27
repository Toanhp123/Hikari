# Adaptive Reader Continuity / HES-v1 M7.5 Final Freeze Hardening Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Repair the last competitive-publication race, harden the final session commit boundary, finish minimal Reader Engine API hygiene, record HES-v2-only naming debt, and perform the final HES-v1 re-freeze verification.

**Architecture:** Keep `:reader:engine` pure and unchanged in routing semantics. Linearize foreground valid-completion publication in `:reader`, serialize publication against ownership sealing/cancellation, and make `ReaderRouteSession` independently validate final result identity/payload. Remove only API artifacts with no production consumer.

**Tech Stack:** Kotlin/JVM engine, Kotlin/coroutines Reader runtime, Gradle 9.5.x, existing HES architecture/schema verification scripts.

**Spec:** `docs/superpowers/specs/2026-08-27-adaptive-reader-continuity-hes-v1-m7-5-final-freeze-hardening.md`

**Execution status:** **VERIFIED/CLOSED — fresh 2026-08-27 final-tree host matrix GREEN; HES-v1 FINAL RE-FROZEN / REFERENCE-GRADE.** Final evidence is recorded in the M7.5 checkpoint.

## Global Constraints

- No routing/ranking/eligibility/continuity/hedge policy formula change.
- Preserve equal completion timestamps and the `completedAtNanos -> role -> attemptId` comparator.
- No callback/channel delivery-order winner key.
- No HES/policy/health version bump.
- No Room/schema/module/dependency change.
- No HES-v1 `DiagnosticCode` redesign; record that as HES-v2 debt.
- After final closure, treat HES-v1 as frozen reference architecture; future work is explicit logic evolution.

---

## Task 1 — Linearize valid completion publication and winner settlement

**Files:**
- Modify `reader/src/main/kotlin/app/openstory/reader/routing/CompetitiveCompletionRegistry.kt`
- Modify `reader/src/main/kotlin/app/openstory/reader/routing/ReaderRouteExecutor.kt`
- Modify `reader/src/main/kotlin/app/openstory/reader/routing/ReaderRouteCoordinator.kt`
- Modify `reader/src/test/kotlin/app/openstory/reader/routing/ReaderCompetitiveExecutionTest.kt`
- Modify `reader/src/test/kotlin/app/openstory/reader/routing/ReaderRouteExecutorAdaptiveTest.kt`

- [x] Add regression proving registry publication owns timestamp capture + record.
- [x] Add ownership regressions for in-flight publication vs close/seal.
- [x] Move foreground completion timestamp creation out of the executor effect payload and into the competition registry publication boundary.
- [x] Replace boolean ownership with `OPEN/PUBLISHING/PUBLISHED/SEALED/CLOSED` state semantics.
- [x] On first success, seal future publications, wait for in-flight publication to settle, recompute winner, then close losers.
- [x] Preserve remote fetch latency as the first two executor clock reads; valid completion remains the post-validation publication read.

Focused host command:

```bash
./gradlew :reader:testDebugUnitTest \
  --tests '*ReaderCompetitiveExecutionTest*' \
  --tests '*ReaderRouteExecutorAdaptiveTest*' \
  --no-daemon
```

## Task 2 — Harden runtime envelope and final session commit coherence

**Files:**
- Modify `reader/src/main/kotlin/app/openstory/reader/routing/CompetitiveCompletionRegistry.kt`
- Modify `reader/src/main/kotlin/app/openstory/reader/routing/ReaderRouteSession.kt`
- Modify `reader/src/test/kotlin/app/openstory/reader/routing/ReaderCompetitiveExecutionTest.kt`
- Modify `reader/src/test/kotlin/app/openstory/reader/routing/ReaderRouteSessionStateTest.kt`

- [x] Require valid completion release/source/locality to match its route attempt.
- [x] Require failure release/source/access mode to match its launched attempt.
- [x] Require final `ReaderForegroundResult.identity` to match the active execution context.
- [x] Require committed chapter group/release to belong to the target execution chapter.

Focused host command:

```bash
./gradlew :reader:testDebugUnitTest \
  --tests '*ReaderRouteSessionStateTest*' \
  --tests '*ReaderCompetitiveExecutionTest*' \
  --tests '*ReaderCoordinatorModelTest*' \
  --no-daemon
```

## Task 3 — Finish minimal public API hygiene

**Files:**
- Modify `reader/engine/src/main/kotlin/app/openstory/reader/engine/ReaderRouteDecision.kt`
- Modify `reader/engine/src/main/kotlin/app/openstory/reader/engine/SourceHealth.kt`
- Modify `reader/engine/src/test/kotlin/app/openstory/reader/engine/ReaderRoutingFactsTest.kt`

- [x] Remove public `ReaderDecisionTrace.empty(...)` test helper.
- [x] Move equivalent empty-trace construction into test source only.
- [x] Remove `HedgeOmissionReason.NOT_EVALUATED`; production retains `NOT_ELIGIBLE`.
- [x] Make the HES-v1 maximum health failure threshold implementation-private.
- [x] Keep `ReaderDecisionTrace` fields and route semantics unchanged.

Focused host command:

```bash
./gradlew :reader:engine:test \
  --tests '*ReaderRoutingFactsTest*' \
  --tests '*ReaderDecisionTraceTest*' \
  --tests '*HedgePolicyTest*' \
  --tests '*ReaderGoldenScenariosTest*' \
  --no-daemon
```

## Task 4 — Documentation debt decision and final audit

- [x] Amend canonical completion semantics to define atomic publication/ownership settlement.
- [x] Document final session commit identity/payload checks.
- [x] Document removed implementation/test-only exports.
- [x] Record `DiagnosticNote.code : RejectionCode` namespace cleanup as HES-v2-only debt.
- [x] Run source/static contradiction and architecture audits.

## Task 5 — Final-tree closure gates

Run on the real repository tree:

```bash
./gradlew :reader:engine:test --no-daemon
./gradlew :reader:testDebugUnitTest --no-daemon
./gradlew :feature:reader:testDebugUnitTest :app:compileDebugKotlin --no-daemon

./gradlew \
  :downloads:testDebugUnitTest \
  :app:testDebugUnitTest \
  --no-daemon

./gradlew :build-logic:test verifyArchitecture --no-daemon

bash scripts/tests/verify-package-boundaries-test.sh
bash scripts/verify-package-boundaries.sh
bash scripts/tests/verify-current-architecture-test.sh
bash scripts/verify-current-architecture.sh
bash scripts/verify-fast.sh
bash scripts/verify.sh
bash scripts/verify-room-schema-stability.sh

./gradlew :storage:room:compileDebugAndroidTestKotlin :app:compileDebugAndroidTestKotlin --no-daemon

git diff --check
```

- [x] All fresh Gradle/host gates green.
- [x] Update M7.4 and M7.5 checkpoints to `VERIFIED/CLOSED`.
- [x] Update current-state/current-roadmap to `M0–M7.5 VERIFIED/CLOSED; HES-v1 FINAL RE-FROZEN`.

Final closure commit subject:

```text
docs(reader): finalize HES-v1 Reader Engine freeze
```
