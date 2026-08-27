# Reader Integration Architecture Cleanup Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Complete R1–R5 so `:reader` consumes the frozen HES-v1 engine with minimal, explicit, effect-layer-only responsibilities.

**Architecture:** Keep the engine frozen. Move failure isolation and lazy remote source materialization into `:reader`, keep trace observational, trim duplicate Reader→Feature DTO fields, narrow routing preferences to language order, and remove runtime/generated hygiene debt.

**Tech Stack:** Kotlin/JVM + Android/Kotlin, kotlinx.coroutines, Gradle 9.5.x, existing Reader/architecture verifiers.

**Spec:** `docs/superpowers/specs/2026-08-27-reader-integration-architecture-cleanup-design.md`

## Global Constraints

- No modification under `reader/engine/`.
- No HES/version/policy/ranking/health change.
- No Room schema or module graph change.
- Cancellation propagates through every new failure boundary.
- LOCAL path must not require remote registry/availability success.
- Trace must not influence runtime control flow.

---

### Task 1: R1 local independence and lazy remote resolution

**Files:**
- Modify `reader/src/main/kotlin/app/openstory/reader/routing/RouteSnapshotAssembler.kt`
- Modify `reader/src/main/kotlin/app/openstory/reader/routing/ReaderRouteExecutor.kt`
- Modify `reader/src/main/kotlin/app/openstory/reader/routing/ReaderRouteCoordinator.kt`
- Modify focused Reader routing tests.

- [x] Add a failing assembler regression where `enabledPluginIds()` throws while exact local cache exists; assembled local access remains valid and REMOTE is unavailable.
- [x] Add a failing foreground regression proving a LOCAL primary succeeds without invoking `sources.enabled()` even when the route contains REMOTE recovery.
- [x] Add a failing REMOTE regression proving registry enumeration failure becomes `reader.source_unavailable`.
- [x] Add `enabledSourceIds()` failure isolation in the assembler.
- [x] Add one per-execution lazy remote source resolver, remove foreground eager `enabledSources()` load, and share the resolver across attempts.
- [ ] Run focused R1 tests.

### Task 2: R2 executable decision is the only runtime route fact

**Files:**
- Modify `reader/src/main/kotlin/app/openstory/reader/routing/ReaderRouteCoordinator.kt`

- [x] Replace `decision.trace.finalWinnerReleaseId` with `checkNotNull(decision.competitiveSet.primary).releaseId` for route recording.
- [x] Audit production `reader` sources for `decision.trace` control-flow reads; expected none.

### Task 3: R3 trim duplicated navigation DTO fields

**Files:**
- Modify `reader/src/main/kotlin/app/openstory/reader/routing/ReaderRouteSessionContracts.kt`
- Modify `reader/src/main/kotlin/app/openstory/reader/routing/ReaderRouteCoordinator.kt`
- Modify Reader tests constructing/asserting committed results.

- [x] Remove `previousChapterId` / `nextChapterId` from `ReaderForegroundResult.Committed`.
- [x] Remove coordinator adjacency calculation.
- [x] Update test fixtures; retain Feature Reader navigation tests unchanged because Feature owns projection.

### Task 4: R4 narrow routing preferences

**Files:**
- Modify `reader/src/main/kotlin/app/openstory/reader/routing/ReaderRoutePlanningContext.kt`
- Modify `reader/src/main/kotlin/app/openstory/reader/routing/ReaderRouteSessionContracts.kt`
- Modify `reader/src/main/kotlin/app/openstory/reader/routing/ReaderRouteSession.kt`
- Modify routing tests/helpers.

- [x] Add `ReaderRoutingPreferences(languageOrder)` with an owned immutable list.
- [x] Map public `ReaderPreferences` to routing preferences at `updateRoutingPreferences` boundary.
- [x] Store and propagate only routing preferences in session/execution/planning contexts.
- [x] Keep language-order invalidation; make font-only updates no-ops at routing state boundary.
- [ ] Update context fixtures and run routing preference tests.

### Task 5: R5 runtime/generated hygiene

**Files:**
- Modify `reader/src/main/kotlin/app/openstory/reader/routing/CompetitiveCompletionRegistry.kt`
- Modify `app/src/release/generated/baselineProfiles/baseline-prof.txt`

- [x] Replace `Object.wait/notifyAll` in `ReaderAttemptOwnership` with `ReentrantLock` + `Condition` while preserving OPEN/PUBLISHING/PUBLISHED/SEALED/CLOSED semantics.
- [x] Remove checked-in baseline-profile entries that reference removed legacy Reader repository/selection/request/ViewModel signatures.
- [x] Verify no legacy descriptor remains.

### Task 6: Self-review and closure evidence

- [x] Audit diff for any change under `reader/engine/`; expected none.
- [x] Audit source for eager source enumeration, trace control-flow reads, removed DTO fields, `fontScale` in routing contexts, raw monitor usage, and legacy baseline descriptors.
- [ ] Run focused Reader tests, full Reader/Feature tests, architecture/package/current-architecture gates, and `git diff --check` where environment permits.
- [ ] Record any environment-blocked Gradle gates honestly; do not claim closure without host evidence.
